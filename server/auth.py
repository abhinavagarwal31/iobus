"""
PIN authentication module (v1.6.0)

Handles PIN generation, validation, and rate limiting for secure device pairing.
"""

from __future__ import annotations

import hashlib
import logging
import os
import secrets
import time
from dataclasses import dataclass

from protocol.constants import (
    AUTH_LOCKOUT_DURATION_SECONDS,
    MAX_AUTH_ATTEMPTS_PER_IP,
    PIN_CHALLENGE_SIZE,
    PIN_LENGTH,
    PIN_SALT_SIZE,
)

logger = logging.getLogger(__name__)


@dataclass
class AuthAttempt:
    """Tracks authentication attempts for rate limiting"""
    attempts: int
    first_attempt: float
    lockout_until: float | None


class PinAuthenticator:
    """
    Manages PIN-based authentication with rate limiting.

    Features:
    - 6-digit PIN generation
    - Salt + challenge for secure hashing
    - Rate limiting (5 attempts per IP, then 5-minute lockout)
    - SHA-256 hashing with salt and challenge
    """

    def __init__(self, pin: str | None = None, salt: bytes | None = None):
        """
        Initialize authenticator.

        Args:
            pin: 6-digit PIN string. If None, generates random PIN.
            salt: 16-byte salt. If None, generates random salt.
        """
        if pin is None:
            self.pin = self._generate_pin()
            logger.info(f"Generated new PIN: {self.pin}")
        else:
            if len(pin) != PIN_LENGTH or not pin.isdigit():
                raise ValueError(f"PIN must be {PIN_LENGTH} digits")
            self.pin = pin

        if salt is None:
            self.salt = secrets.token_bytes(PIN_SALT_SIZE)
            logger.debug(f"Generated new salt: {self.salt.hex()}")
        else:
            if len(salt) != PIN_SALT_SIZE:
                raise ValueError(f"Salt must be {PIN_SALT_SIZE} bytes")
            self.salt = salt

        # Rate limiting: client_ip -> AuthAttempt
        self._attempts: dict[str, AuthAttempt] = {}

    def _generate_pin(self) -> str:
        """Generate random 6-digit PIN"""
        return f"{secrets.randbelow(1_000_000):06d}"

    def generate_challenge(self) -> bytes:
        """Generate random challenge for this authentication attempt"""
        return secrets.token_bytes(PIN_CHALLENGE_SIZE)

    def compute_hash(self, pin: str, challenge: bytes) -> bytes:
        """
        Compute PIN hash: SHA256(pin + salt + challenge)

        Args:
            pin: 6-digit PIN string
            challenge: 4-byte challenge

        Returns:
            32-byte SHA-256 hash
        """
        if len(pin) != PIN_LENGTH:
            raise ValueError(f"PIN must be {PIN_LENGTH} digits")
        if len(challenge) != PIN_CHALLENGE_SIZE:
            raise ValueError(f"Challenge must be {PIN_CHALLENGE_SIZE} bytes")

        material = pin.encode('utf-8') + self.salt + challenge
        return hashlib.sha256(material).digest()

    def verify(self, client_hash: bytes, challenge: bytes, client_ip: str) -> tuple[bool, int]:
        """
        Verify PIN hash with rate limiting.

        Args:
            client_hash: 32-byte hash sent by client
            challenge: 4-byte challenge sent to client
            client_ip: Client IP address for rate limiting

        Returns:
            (success: bool, retry_after: int seconds)
            - (True, 0): Authentication successful
            - (False, 0): Invalid PIN, can retry immediately
            - (False, N): Rate limited, retry after N seconds
        """
        # Check rate limiting
        now = time.time()
        if client_ip in self._attempts:
            attempt = self._attempts[client_ip]

            # Check if locked out
            if attempt.lockout_until and now < attempt.lockout_until:
                retry_after = int(attempt.lockout_until - now)
                logger.warning(f"Client {client_ip} is locked out for {retry_after}s")
                return False, retry_after

            # Reset if lockout expired
            if attempt.lockout_until and now >= attempt.lockout_until:
                logger.info(f"Lockout expired for {client_ip}, resetting attempts")
                del self._attempts[client_ip]

        # Compute expected hash
        expected_hash = self.compute_hash(self.pin, challenge)

        # Verify
        if secrets.compare_digest(client_hash, expected_hash):
            # Success - clear any failed attempts
            if client_ip in self._attempts:
                del self._attempts[client_ip]
            logger.info(f"Client {client_ip} authenticated successfully")
            return True, 0

        # Failed attempt - update counter
        if client_ip not in self._attempts:
            self._attempts[client_ip] = AuthAttempt(
                attempts=1,
                first_attempt=now,
                lockout_until=None
            )
        else:
            self._attempts[client_ip].attempts += 1

        attempt = self._attempts[client_ip]

        # Check if should lock out
        if attempt.attempts >= MAX_AUTH_ATTEMPTS_PER_IP:
            attempt.lockout_until = now + AUTH_LOCKOUT_DURATION_SECONDS
            logger.warning(
                f"Client {client_ip} exceeded {MAX_AUTH_ATTEMPTS_PER_IP} attempts, "
                f"locking out for {AUTH_LOCKOUT_DURATION_SECONDS}s"
            )
            return False, AUTH_LOCKOUT_DURATION_SECONDS

        logger.warning(
            f"Invalid PIN from {client_ip} "
            f"(attempt {attempt.attempts}/{MAX_AUTH_ATTEMPTS_PER_IP})"
        )
        return False, 0

    def reset_attempts(self, client_ip: str) -> None:
        """Clear failed attempts for a client (admin/debug use)"""
        if client_ip in self._attempts:
            del self._attempts[client_ip]
            logger.info(f"Reset auth attempts for {client_ip}")

    def get_pin_display(self) -> str:
        """Format PIN for display (e.g. '482 915')"""
        return f"{self.pin[:3]} {self.pin[3:]}"

    def regenerate_pin(self) -> str:
        """Generate new PIN and clear all attempts"""
        self.pin = self._generate_pin()
        self.salt = secrets.token_bytes(PIN_SALT_SIZE)
        self._attempts.clear()
        logger.info(f"Regenerated PIN: {self.pin}")
        return self.pin
