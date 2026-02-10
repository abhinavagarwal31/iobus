"""
Server entry point.

Responsibilities:
- Parse CLI arguments
- Check macOS Accessibility permissions
- Initialize configuration
- Start TCP (control) and UDP (data) listeners
- Run the asyncio event loop
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import signal
import sys

from server.config import ServerConfig
from server.discovery import print_connection_info
from server.input.keyboard import KeyboardController
from server.input.mouse import MouseController
from server.permissions import require_accessibility
from server.transport.tcp_server import TCPControlServer
from server.transport.udp_server import UDPDataServer

logger = logging.getLogger("iobus")


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="iobus-server",
        description="IOBus — macOS remote-control server",
    )
    parser.add_argument(
        "--tcp-port", type=int, default=None,
        help="TCP port for control plane (default: 9800)",
    )
    parser.add_argument(
        "--udp-port", type=int, default=None,
        help="UDP port for data plane (default: 9801)",
    )
    parser.add_argument(
        "--bind", type=str, default=None,
        help="Bind address (default: 0.0.0.0)",
    )
    parser.add_argument(
        "--log-level", type=str, default=None,
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Logging verbosity (default: INFO)",
    )
    parser.add_argument(
        "--skip-permission-check", action="store_true",
        help="Skip Accessibility permission check (for debugging only)",
    )
    return parser.parse_args()


def _build_config(args: argparse.Namespace) -> ServerConfig:
    """Build config from CLI args (override env/defaults)."""
    kwargs: dict = {}
    if args.tcp_port is not None:
        kwargs["tcp_port"] = args.tcp_port
    if args.udp_port is not None:
        kwargs["udp_port"] = args.udp_port
    if args.bind is not None:
        kwargs["bind_address"] = args.bind
    if args.log_level is not None:
        kwargs["log_level"] = args.log_level
    return ServerConfig(**kwargs)


def _setup_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level, logging.INFO),
        format="%(asctime)s  %(levelname)-8s  %(name)-20s  %(message)s",
        datefmt="%H:%M:%S",
    )


async def _run(config: ServerConfig) -> None:
    """Async entry — start all servers and run until interrupted."""
    loop = asyncio.get_running_loop()

    # Input controllers
    mouse = MouseController()
    keyboard = KeyboardController()

    # Transport servers
    tcp_server = TCPControlServer(config)
    udp_server = UDPDataServer(config, tcp_server, mouse, keyboard)

    await tcp_server.start(loop)
    await udp_server.start(loop)

    print_connection_info(config.tcp_port, config.udp_port)

    # Graceful shutdown on SIGINT / SIGTERM
    stop_event = asyncio.Event()

    def _signal_handler() -> None:
        logger.info("Shutdown signal received")
        stop_event.set()

    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, _signal_handler)

    await stop_event.wait()

    logger.info("Shutting down…")
    await udp_server.stop()
    await tcp_server.stop()
    logger.info("Server stopped cleanly")


def main() -> None:
    args = _parse_args()
    config = _build_config(args)

    _setup_logging(config.log_level)
    logger.info("IOBus server starting  |  %s", config.summary())

    # Permission gate
    if not args.skip_permission_check:
        require_accessibility()
    else:
        logger.warning("Accessibility permission check SKIPPED (--skip-permission-check)")

    try:
        asyncio.run(_run(config))
    except KeyboardInterrupt:
        pass  # Clean exit on Ctrl+C


if __name__ == "__main__":
    main()
