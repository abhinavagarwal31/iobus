"""
TCP control plane server.

Responsibilities:
- Listen for incoming TCP connections
- Handle HANDSHAKE_REQ → HANDSHAKE_ACK / HANDSHAKE_REJECT
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

from protocol.constants import PROTOCOL_VERSION
from protocol.messages import (
    HEADER_SIZE,
    Ack,
    CommandError,
    HandshakeAck,
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
from server.config import ServerConfig
from server.input.actions import SystemActions

logger = logging.getLogger(__name__)


@dataclass
class ClientSession:
    """Tracks a connected client's state."""
    name: str
    address: tuple[str, int]
    protocol_version: int
    session_id: int = 0
    connected_at: float = field(default_factory=time.monotonic)
    last_pong: float = field(default_factory=time.monotonic)


class TCPControlProtocol(asyncio.Protocol):
    """Handle a single TCP client connection (asyncio Protocol)."""

    def __init__(
        self,
        server: TCPControlServer,
        config: ServerConfig,
        system_actions: SystemActions,
    ) -> None:
        self._server = server
        self._config = config
        self._system = system_actions
        self._transport: asyncio.Transport | None = None
        self._buffer = bytearray()
        self._session: ClientSession | None = None
        self._peer: tuple[str, int] | None = None

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
                server_version=PROTOCOL_VERSION, reason_code=1,
            )
            self._send(reject.encode())
            logger.warning(
                "Rejected '%s': version mismatch (client=%d, server=%d)",
                req.client_name, req.client_version, PROTOCOL_VERSION,
            )
            return

        # Single-client check
        if self._server.has_client():
            reject = HandshakeReject(
                server_version=PROTOCOL_VERSION, reason_code=2,
            )
            self._send(reject.encode())
            logger.warning("Rejected '%s': server busy (another client connected)", req.client_name)
            return

        # Accept
        self._session = ClientSession(
            name=req.client_name,
            address=self._peer or ("?", 0),
            protocol_version=req.client_version,
            session_id=int.from_bytes(os.urandom(4), "big"),
        )
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
            req.client_name, self._config.udp_port, self._session.session_id,
        )

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
        """Respond with current brightness and volume."""
        brightness = SystemActions.get_brightness()
        volume = SystemActions.get_volume()
        logger.info("System state request → brightness=%.2f, volume=%.2f", brightness, volume)
        resp = SystemStateResponse(brightness=brightness, volume=volume)
        self._send(resp.encode())

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

    def __init__(self, config: ServerConfig, system_actions: SystemActions) -> None:
        self._config = config
        self._system = system_actions
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
        proto = TCPControlProtocol(self, self._config, self._system)
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
