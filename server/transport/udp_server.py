"""
UDP data plane server.

Responsibilities:
- Receive high-frequency input events (mouse, keyboard) over UDP
- Decode headers and dispatch to the appropriate input handler
- Stateless — each datagram is processed independently
- Validate that the sender matches the TCP-authenticated client
"""

from __future__ import annotations

import asyncio
import logging

from protocol.messages import (
    HEADER_SIZE,
    Header,
    KeyEvent,
    MessageType,
    MouseClick,
    MouseDrag,
    MouseMove,
    MouseScroll,
    SystemAction,
    SystemActionId,
)
from server.config import ServerConfig
from server.input.actions import SystemActions
from server.input.keyboard import KeyboardController
from server.input.mouse import MouseController
from server.transport.tcp_server import TCPControlServer

logger = logging.getLogger(__name__)


class UDPDataProtocol(asyncio.DatagramProtocol):
    """Handle incoming UDP datagrams containing input events."""

    def __init__(
        self,
        tcp_server: TCPControlServer,
        mouse: MouseController,
        keyboard: KeyboardController,
    ) -> None:
        self._tcp = tcp_server
        self._mouse = mouse
        self._keyboard = keyboard
        self._system = SystemActions(keyboard)
        self._transport: asyncio.DatagramTransport | None = None

    def connection_made(self, transport: asyncio.DatagramTransport) -> None:
        self._transport = transport

    def datagram_received(self, data: bytes, addr: tuple[str, int]) -> None:
        """Process a single UDP datagram."""
        if len(data) < HEADER_SIZE:
            return  # Runt packet — ignore silently

        # Validate sender: must match the TCP-authenticated client's IP
        client = self._tcp.client
        if client is None:
            return  # No client connected — drop
        if addr[0] != client.address[0]:
            logger.debug("UDP from unknown sender %s — dropping", addr[0])
            return

        try:
            header = Header.decode(data[:HEADER_SIZE])
        except (ValueError, KeyError):
            return  # Corrupt header — drop silently (UDP, expected)

        payload = data[HEADER_SIZE:HEADER_SIZE + header.payload_length]

        try:
            self._dispatch(header.msg_type, payload)
        except Exception:
            # Never crash the event loop on a bad packet
            logger.exception("Error processing UDP message type 0x%02X", header.msg_type)

    def _dispatch(self, msg_type: MessageType, payload: bytes) -> None:
        """Route a decoded message to the appropriate input handler."""
        match msg_type:
            case MessageType.MOUSE_MOVE:
                self._mouse.handle_move(MouseMove.decode(payload))

            case MessageType.MOUSE_CLICK:
                self._mouse.handle_click(MouseClick.decode(payload))

            case MessageType.MOUSE_SCROLL:
                self._mouse.handle_scroll(MouseScroll.decode(payload))

            case MessageType.MOUSE_DRAG:
                self._mouse.handle_drag(MouseDrag.decode(payload))

            case MessageType.KEY_EVENT:
                self._keyboard.handle_key_event(KeyEvent.decode(payload))

            case MessageType.SYSTEM_ACTION:
                action = SystemAction.decode(payload)
                match action.action_id:
                    case SystemActionId.LOCK_SCREEN:
                        self._system.lock_screen()
                    case SystemActionId.POWER_DIALOG:
                        self._system.show_power_dialog()
                    case SystemActionId.SLEEP:
                        self._system.sleep()
                    case _:
                        logger.debug("Unknown system action: %d", action.action_id)

            case _:
                logger.debug("Ignoring unknown UDP message type: 0x%02X", msg_type)

    def error_received(self, exc: Exception) -> None:
        logger.warning("UDP error: %s", exc)

    def connection_lost(self, exc: Exception | None) -> None:
        logger.info("UDP transport closed")


class UDPDataServer:
    """Manages the UDP listener for input data."""

    def __init__(
        self,
        config: ServerConfig,
        tcp_server: TCPControlServer,
        mouse: MouseController,
        keyboard: KeyboardController,
    ) -> None:
        self._config = config
        self._tcp_server = tcp_server
        self._mouse = mouse
        self._keyboard = keyboard
        self._transport: asyncio.DatagramTransport | None = None

    async def start(self, loop: asyncio.AbstractEventLoop) -> None:
        """Bind the UDP socket and start receiving datagrams."""
        transport, _ = await loop.create_datagram_endpoint(
            lambda: UDPDataProtocol(self._tcp_server, self._mouse, self._keyboard),
            local_addr=(self._config.bind_address, self._config.udp_port),
        )
        self._transport = transport
        logger.info(
            "UDP data server listening on %s:%d",
            self._config.bind_address, self._config.udp_port,
        )

    async def stop(self) -> None:
        """Close the UDP transport."""
        if self._transport:
            self._transport.close()
        logger.info("UDP data server stopped")
