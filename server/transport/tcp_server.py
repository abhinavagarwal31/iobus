"""
TCP control plane server.

Responsibilities:
- Listen for incoming TCP connections
- Handle HANDSHAKE_REQ → HANDSHAKE_AUTH_REQUIRED → HANDSHAKE_AUTH_SUCCESS/FAILED (v1.6.0)
- Manage PING/PONG keepalive cycle
- Handle graceful DISCONNECT
- Track connected client state
- Enforce single-client policy (v1)
"""

from __future__ import annotations

import asyncio
import logging
import os
import time
from dataclasses import dataclass, field

from protocol.constants import (
    PROTOCOL_VERSION,
    REJECT_AUTH_REQUIRED,
    REJECT_BUSY,
    REJECT_VERSION_MISMATCH,
)
from protocol.messages import (
    HEADER_SIZE,
    Ack,
    CommandError,
    HandshakeAck,
    HandshakeAuthFailed,
    HandshakeAuthRequired,
    HandshakeAuthResponse,
    HandshakeAuthSuccess,
    HandshakeReject,
    HandshakeReq,
    Header,
    LaunchApp,
    MessageType,
    SystemStateResponse,
    encode_error,
    encode_ping,
    encode_pong,
)
from server.auth import PinAuthenticator
from server.config import ServerConfig
from server.input.actions import SystemActions

logger = logging.getLogger(__name__)

# How often (seconds) to poll macOS for state changes.
# Lower value = more responsive but higher CPU usage.
STATE_POLL_INTERVAL: float = 0.25  # 250ms for snappy activity updates


@dataclass
class ClientSession:
    """Tracks a connected client's state."""
    name: str
    address: tuple[str, int]
    protocol_version: int
    session_id: int = 0
    connected_at: float = field(default_factory=time.monotonic)
    last_pong: float = field(default_factory=time.monotonic)
    pending_auth_challenge: bytes | None = None  # v1.6.0: 4-byte challenge for PIN verification


class TCPControlProtocol(asyncio.Protocol):
    """Handle a single TCP client connection (asyncio Protocol)."""

    def __init__(
        self,
        server: TCPControlServer,
        config: ServerConfig,
        system_actions: SystemActions,
        authenticator: PinAuthenticator | None,
    ) -> None:
        self._server = server
        self._config = config
        self._system = system_actions
        self._authenticator = authenticator
        self._transport: asyncio.Transport | None = None
        self._buffer = bytearray()
        self._session: ClientSession | None = None
        self._peer: tuple[str, int] | None = None
        self._state_watcher_task: asyncio.Task | None = None

    # ---- asyncio.Protocol callbacks ----

    def connection_made(self, transport: asyncio.Transport) -> None:
        self._transport = transport
        peer = transport.get_extra_info("peername")
        self._peer = (peer[0], peer[1]) if peer else ("?", 0)
        logger.info("TCP connection from %s:%d", *self._peer)

    def data_received(self, data: bytes) -> None:
        self._buffer.extend(data)
        self._process_buffer()

    def connection_lost(self, exc: Exception | None) -> None:
        if self._state_watcher_task:
            self._state_watcher_task.cancel()
            self._state_watcher_task = None
        if self._session:
            logger.info("Client disconnected: %s", self._session.name)
            self._server.remove_client(self._session)
            self._session = None
        elif self._peer:
            logger.info("TCP connection closed: %s:%d", *self._peer)

    # ---- Message processing ----

    def _process_buffer(self) -> None:
        """Consume complete messages from the receive buffer."""
        while len(self._buffer) >= HEADER_SIZE:
            try:
                header = Header.decode(bytes(self._buffer[:HEADER_SIZE]))
            except (ValueError, KeyError):
                logger.warning("Invalid header from %s — dropping connection", self._peer)
                if self._transport:
                    self._transport.close()
                return

            total = HEADER_SIZE + header.payload_length
            if len(self._buffer) < total:
                break  # Wait for more data

            payload = bytes(self._buffer[HEADER_SIZE:total])
            del self._buffer[:total]

            self._handle_message(header, payload)

    def _handle_message(self, header: Header, payload: bytes) -> None:
        """Dispatch a decoded message."""
        match header.msg_type:
            case MessageType.HANDSHAKE_REQ:
                self._on_handshake(payload)
            case MessageType.HANDSHAKE_AUTH_RESPONSE:
                self._on_handshake_auth_response(payload)
            case MessageType.PING:
                self._on_ping()
            case MessageType.PONG:
                self._on_pong()
            case MessageType.DISCONNECT:
                self._on_disconnect()
            case MessageType.GET_SYSTEM_STATE:
                self._on_get_system_state()
            case MessageType.LAUNCH_APP:
                self._on_launch_app(payload)
            case _:
                logger.warning("Unexpected TCP message type: 0x%02X", header.msg_type)
                self._send(encode_error(f"Unexpected message type: 0x{header.msg_type:02X}"))

    def _on_handshake(self, payload: bytes) -> None:
        req = HandshakeReq.decode(payload)
        logger.info(
            "Handshake from '%s' (protocol v%d)", req.client_name, req.client_version,
        )

        # Version check
        if req.client_version != PROTOCOL_VERSION:
            reject = HandshakeReject(
                server_version=PROTOCOL_VERSION, reason_code=REJECT_VERSION_MISMATCH,
            )
            self._send(reject.encode())
            logger.warning(
                "Rejected '%s': version mismatch (client=%d, server=%d)",
                req.client_name, req.client_version, PROTOCOL_VERSION,
            )
            return

        # Auth check (v1.6.0) - require PIN if authenticator present
        if self._authenticator:
            # Generate auth challenge
            challenge = self._authenticator.generate_challenge()

            # Store challenge in temporary session (will be completed after auth)
            self._session = ClientSession(
                name=req.client_name,
                address=self._peer or ("?", 0),
                protocol_version=req.client_version,
                session_id=0,  # Will be assigned after auth
                pending_auth_challenge=challenge,
            )

            # Send auth required
            auth_req = HandshakeAuthRequired(
                pin_salt=self._authenticator.get_salt(),
                challenge=challenge,
            )
            self._send(auth_req.encode())
            logger.info("Sent auth challenge to '%s'", req.client_name)
            return  # Wait for HANDSHAKE_AUTH_RESPONSE

        # Single-client check (allow same-IP reconnection)
        if not self._check_and_allow_reconnect(req.client_name):
            return

        # Accept (no auth required)
        self._accept_client(req.client_name)

    def _on_handshake_auth_response(self, payload: bytes) -> None:
        """Verify PIN hash and accept/reject client (v1.6.0)."""
        if not self._session or not self._session.pending_auth_challenge:
            logger.warning("Unexpected HANDSHAKE_AUTH_RESPONSE from %s", self._peer)
            if self._transport:
                self._transport.close()
            return

        resp = HandshakeAuthResponse.decode(payload)
        challenge = self._session.pending_auth_challenge
        client_ip = self._peer[0] if self._peer else "unknown"

        # Verify PIN hash
        assert self._authenticator is not None
        success, retry_after = self._authenticator.verify(
            resp.pin_hash, challenge, client_ip
        )

        if not success:
            # Auth failed
            fail_msg = HandshakeAuthFailed(retry_after=retry_after)
            self._send(fail_msg.encode())
            logger.warning(
                "Auth failed for '%s' from %s (retry after %ds)",
                self._session.name, client_ip, retry_after,
            )
            # Clear session and close connection
            self._session = None
            if self._transport:
                self._transport.close()
            return

        # Auth succeeded - check single-client policy (allow same-IP reconnection)
        if not self._check_and_allow_reconnect(self._session.name):
            self._session = None
            return

        # Accept client with session token
        session_token = os.urandom(16)
        success_msg = HandshakeAuthSuccess(
            server_version=PROTOCOL_VERSION,
            flags=0,
            session_token=session_token,
        )
        self._send(success_msg.encode())
        logger.info("Auth succeeded for '%s' from %s", self._session.name, client_ip)

        # Complete client acceptance
        self._accept_client(self._session.name)

    def _accept_client(self, client_name: str) -> None:
        """Complete client acceptance and send HandshakeAck."""
        # Assign session ID if not already set
        if not self._session:
            self._session = ClientSession(
                name=client_name,
                address=self._peer or ("?", 0),
                protocol_version=PROTOCOL_VERSION,
                session_id=int.from_bytes(os.urandom(4), "big"),
            )
        else:
            # Update temp session from auth flow
            self._session.session_id = int.from_bytes(os.urandom(4), "big")
            self._session.pending_auth_challenge = None

        self._server.set_client(self._session)

        ack = HandshakeAck(
            server_version=PROTOCOL_VERSION,
            flags=0,
            udp_port=self._config.udp_port,
            keepalive_interval=self._config.keepalive_interval,
        )
        self._send(ack.encode())
        logger.info(
            "Accepted client '%s' — UDP port %d, session 0x%08X",
            client_name, self._config.udp_port, self._session.session_id,
        )

        # Start state watcher — pushes initial brightness+volume then tracks changes
        self._state_watcher_task = asyncio.create_task(
            self._state_watcher(), name=f"state-watcher-{self._session.session_id:08X}"
        )

    def _check_and_allow_reconnect(self, client_name: str) -> bool:
        """
        Check single-client policy with reconnection support.
        Returns True if connection should proceed, False if rejected.

        If an existing client is connected from a different address, reject as BUSY.
        If from the same address, kick out the old session and allow reconnection.
        """
        existing = self._server.client
        if not existing:
            return True  # No existing client, proceed

        current_ip = self._peer[0] if self._peer else None
        existing_ip = existing.address[0]

        if current_ip == existing_ip:
            # Same IP reconnecting - allow it by removing old session
            logger.info(
                "Client '%s' reconnecting from %s (removing stale session)",
                client_name, current_ip
            )
            self._server.remove_client(existing)
            return True
        else:
            # Different client trying to connect - reject as busy
            reject = HandshakeReject(
                server_version=PROTOCOL_VERSION, reason_code=REJECT_BUSY,
            )
            self._send(reject.encode())
            logger.warning(
                "Rejected '%s' from %s: server busy (client '%s' at %s connected)",
                client_name, current_ip, existing.name, existing_ip
            )
            if self._transport:
                self._transport.close()
            return False

    def _on_ping(self) -> None:
        self._send(encode_pong())

    def _on_pong(self) -> None:
        if self._session:
            self._session.last_pong = time.monotonic()

    def _on_disconnect(self) -> None:
        logger.info("Client sent DISCONNECT")
        if self._session:
            self._server.remove_client(self._session)
            self._session = None
        if self._transport:
            self._transport.close()

    def _on_get_system_state(self) -> None:
        """Respond with current brightness, volume, mute, lock, activity, and battery state."""
        brightness = SystemActions.get_brightness()
        volume = SystemActions.get_volume()
        muted = SystemActions.get_mute()
        locked = SystemActions.get_screen_lock_status()
        activity = SystemActions.get_activity_status()
        idle_time = SystemActions.get_idle_time()
        battery_pct, charging = SystemActions.get_battery_status()
        battery = int(battery_pct * 100)
        logger.info(
            "System state request → brightness=%.2f, volume=%.2f, muted=%s, locked=%s, activity=%s, battery=%d%%, charging=%s",
            brightness, volume, muted, locked, activity, battery, charging
        )
        resp = SystemStateResponse(
            brightness=brightness, volume=volume, is_muted=muted,
            is_locked=locked, activity_status=activity, idle_time=idle_time,
            battery_percent=battery, is_charging=charging,
        )
        self._send(resp.encode())

    # ---- State watcher ----

    async def _state_watcher(self) -> None:
        """Push SYSTEM_STATE_RESPONSE on connect and whenever state changes.

        Tracks brightness, volume, mute, lock status, and activity.
        Runs only while a client is connected.
        Subprocess calls are offloaded to a thread executor so the event loop
        is never blocked.
        """
        loop = asyncio.get_running_loop()

        try:
            # Read initial state (in executor — subprocess calls can take ~100 ms)
            last_b = await loop.run_in_executor(None, SystemActions.get_brightness)
            last_v = await loop.run_in_executor(None, SystemActions.get_volume)
            last_m = await loop.run_in_executor(None, SystemActions.get_mute)
            last_l = await loop.run_in_executor(None, SystemActions.get_screen_lock_status)
            last_a = await loop.run_in_executor(None, SystemActions.get_activity_status)
            last_i = await loop.run_in_executor(None, SystemActions.get_idle_time)
            last_batt_pct, last_c = await loop.run_in_executor(None, SystemActions.get_battery_status)
            last_batt = int(last_batt_pct * 100)
        except Exception:
            last_b, last_v, last_m, last_l, last_a, last_i = 0.5, 0.5, False, False, "active", 0.0
            last_batt, last_c = 100, False

        # Push initial state so the app syncs all values immediately
        self._send(SystemStateResponse(
            brightness=last_b, volume=last_v, is_muted=last_m,
            is_locked=last_l, activity_status=last_a, idle_time=last_i,
            battery_percent=last_batt, is_charging=last_c,
        ).encode())
        logger.debug(
            "State watcher: initial push brightness=%.2f volume=%.2f muted=%s locked=%s activity=%s battery=%d%% charging=%s",
            last_b, last_v, last_m, last_l, last_a, last_batt, last_c
        )

        try:
            while self._session is not None:
                await asyncio.sleep(STATE_POLL_INTERVAL)
                if self._session is None:
                    break
                try:
                    # Get idle time first (fastest check for activity changes)
                    i = await loop.run_in_executor(None, SystemActions.get_idle_time)

                    # Derive activity status locally (no extra syscall)
                    if i < 2:
                        a = "active"
                    elif i < 300:
                        a = "idle"
                    else:
                        a = "away"

                    b = await loop.run_in_executor(None, SystemActions.get_brightness)
                    v = await loop.run_in_executor(None, SystemActions.get_volume)
                    m = await loop.run_in_executor(None, SystemActions.get_mute)
                    l = await loop.run_in_executor(None, SystemActions.get_screen_lock_status)
                    batt_pct, c = await loop.run_in_executor(None, SystemActions.get_battery_status)
                    batt = int(batt_pct * 100)
                except Exception:
                    continue

                # Push update if any value changed
                if (abs(b - last_b) > 0.005 or abs(v - last_v) > 0.005 or
                    m != last_m or l != last_l or a != last_a or
                    batt != last_batt or c != last_c):
                    last_b, last_v, last_m, last_l, last_a, last_i = b, v, m, l, a, i
                    last_batt, last_c = batt, c
                    self._send(SystemStateResponse(
                        brightness=b, volume=v, is_muted=m,
                        is_locked=l, activity_status=a, idle_time=i,
                        battery_percent=batt, is_charging=c,
                    ).encode())
                    logger.debug(
                        "State watcher: push change brightness=%.2f volume=%.2f muted=%s locked=%s activity=%s battery=%d%% charging=%s",
                        b, v, m, l, a, batt, c
                    )
        except asyncio.CancelledError:
            pass  # Normal on disconnect

    def _on_launch_app(self, payload: bytes) -> None:
        """Launch app by name, respond with ACK or ERROR."""
        launch = LaunchApp.decode(payload)
        if not launch.app_name:
            logger.warning("Empty app name in LAUNCH_APP")
            self._send(CommandError(app_id=0).encode())
            return
        try:
            self._system.launch_app(launch.app_name)
            self._send(Ack(app_id=0).encode())
        except Exception:
            logger.exception("Failed to launch app '%s'", launch.app_name)
            self._send(CommandError(app_id=0).encode())

    def _send(self, data: bytes) -> None:
        if self._transport and not self._transport.is_closing():
            self._transport.write(data)

    # ---- Keepalive ----

    def send_ping(self) -> None:
        """Called externally by the keepalive timer."""
        self._send(encode_ping())

    def is_alive(self, timeout: float) -> bool:
        """Check if the client has responded within the timeout window."""
        if self._session is None:
            return False
        return (time.monotonic() - self._session.last_pong) < timeout


class TCPControlServer:
    """Manages the TCP listener and connected client state."""

    def __init__(
        self,
        config: ServerConfig,
        system_actions: SystemActions,
        authenticator: PinAuthenticator | None = None,
    ) -> None:
        self._config = config
        self._system = system_actions
        self._authenticator = authenticator
        self._client: ClientSession | None = None
        self._protocols: list[TCPControlProtocol] = []
        self._server: asyncio.AbstractServer | None = None
        self._keepalive_task: asyncio.Task | None = None

    def has_client(self) -> bool:
        return self._client is not None

    def set_client(self, session: ClientSession) -> None:
        self._client = session

    def remove_client(self, session: ClientSession) -> None:
        if self._client is session:
            self._client = None

    @property
    def client(self) -> ClientSession | None:
        return self._client

    async def start(self, loop: asyncio.AbstractEventLoop) -> None:
        """Start listening for TCP connections."""
        self._server = await loop.create_server(
            lambda: self._make_protocol(),
            host=self._config.bind_address,
            port=self._config.tcp_port,
        )
        self._keepalive_task = asyncio.create_task(self._keepalive_loop())
        logger.info("TCP control server listening on %s:%d", self._config.bind_address, self._config.tcp_port)

    def _make_protocol(self) -> TCPControlProtocol:
        proto = TCPControlProtocol(self, self._config, self._system, self._authenticator)
        self._protocols.append(proto)
        return proto

    async def _keepalive_loop(self) -> None:
        """Periodically send PING to connected clients and check for timeouts."""
        interval = self._config.keepalive_interval
        timeout = self._config.keepalive_timeout

        while True:
            await asyncio.sleep(interval)
            # Clean up dead protocols
            active: list[TCPControlProtocol] = []
            for proto in self._protocols:
                if proto._session is not None:
                    if proto.is_alive(timeout):
                        proto.send_ping()
                        active.append(proto)
                    else:
                        logger.warning(
                            "Client '%s' timed out — no pong in %ds",
                            proto._session.name, timeout,
                        )
                        self.remove_client(proto._session)
                        if proto._transport:
                            proto._transport.close()
            self._protocols = active

    async def stop(self) -> None:
        """Shut down the TCP server."""
        if self._keepalive_task:
            self._keepalive_task.cancel()
        if self._server:
            self._server.close()
            await self._server.wait_closed()
        logger.info("TCP control server stopped")
