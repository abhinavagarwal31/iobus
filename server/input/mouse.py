"""
Mouse input injection via CGEvent API.

Responsibilities:
- Translate protocol mouse events into CGEvent calls
- Handle: move, left click, right click, middle click, scroll, drag
- Track mouse state (position, button hold state for drag)
- Use Quartz.CGEventCreateMouseEvent and related APIs
"""

from __future__ import annotations

import logging
import time

import Quartz

from protocol.messages import (
    ClickAction,
    MouseButton,
    MouseClick,
    MouseDrag,
    MouseMove,
    MouseScroll,
)

logger = logging.getLogger(__name__)

# CGEvent mouse-button constants
_BUTTON_MAP_DOWN = {
    MouseButton.LEFT: Quartz.kCGEventLeftMouseDown,
    MouseButton.RIGHT: Quartz.kCGEventRightMouseDown,
    MouseButton.MIDDLE: Quartz.kCGEventOtherMouseDown,
}
_BUTTON_MAP_UP = {
    MouseButton.LEFT: Quartz.kCGEventLeftMouseUp,
    MouseButton.RIGHT: Quartz.kCGEventRightMouseUp,
    MouseButton.MIDDLE: Quartz.kCGEventOtherMouseUp,
}
_BUTTON_MAP_DRAG = {
    MouseButton.LEFT: Quartz.kCGEventLeftMouseDragged,
    MouseButton.RIGHT: Quartz.kCGEventRightMouseDragged,
    MouseButton.MIDDLE: Quartz.kCGEventOtherMouseDragged,
}
_CG_BUTTON_NUMBER = {
    MouseButton.LEFT: 0,
    MouseButton.RIGHT: 1,
    MouseButton.MIDDLE: 2,
}


# macOS double-click thresholds (match system defaults)
_DOUBLE_CLICK_INTERVAL_S = 0.5   # seconds — NSEvent.doubleClickInterval default
_DOUBLE_CLICK_DISTANCE_PX = 5.0  # pixels — max cursor drift between clicks


def _get_cursor_position() -> Quartz.CGPoint:
    """Return the current cursor position as a CGPoint."""
    event = Quartz.CGEventCreate(None)
    point = Quartz.CGEventGetLocation(event)
    return point


def _post_event(event: Quartz.CGEventRef) -> None:
    """Post a CGEvent to the HID event system."""
    Quartz.CGEventPost(Quartz.kCGHIDEventTap, event)


class MouseController:
    """Injects mouse events via CGEvent API."""

    def __init__(self) -> None:
        # Click-state tracking for double-click synthesis.
        # kCGMouseEventClickState must be 1 for single, 2 for double, etc.
        # Without this, synthesized clicks never register as double-clicks.
        self._click_count: int = 0
        self._last_click_time: float = 0.0
        self._last_click_pos: Quartz.CGPoint = Quartz.CGPointMake(0, 0)

    def handle_move(self, msg: MouseMove) -> None:
        """Move the cursor by a relative delta."""
        pos = _get_cursor_position()
        new_x = pos.x + msg.dx
        new_y = pos.y + msg.dy
        new_point = Quartz.CGPointMake(new_x, new_y)

        event = Quartz.CGEventCreateMouseEvent(
            None, Quartz.kCGEventMouseMoved, new_point, Quartz.kCGMouseButtonLeft,
        )
        _post_event(event)

    def handle_click(self, msg: MouseClick) -> None:
        """Press or release a mouse button at the current cursor position."""
        pos = _get_cursor_position()

        if msg.action == ClickAction.PRESS:
            event_type = _BUTTON_MAP_DOWN[msg.button]

            # Compute click state for left-button clicks.
            # macOS requires kCGMouseEventClickState = 2 on the second down event
            # for apps (Finder, etc.) to recognise it as a double-click.
            if msg.button == MouseButton.LEFT:
                now = time.monotonic()
                dx = pos.x - self._last_click_pos.x
                dy = pos.y - self._last_click_pos.y
                dist = (dx * dx + dy * dy) ** 0.5
                elapsed = now - self._last_click_time

                if elapsed < _DOUBLE_CLICK_INTERVAL_S and dist < _DOUBLE_CLICK_DISTANCE_PX:
                    self._click_count += 1
                else:
                    self._click_count = 1

                self._last_click_time = now
                self._last_click_pos = pos
            else:
                # Non-left buttons: always single
                self._click_count = 1

        else:
            event_type = _BUTTON_MAP_UP[msg.button]

        click_state = self._click_count

        event = Quartz.CGEventCreateMouseEvent(
            None, event_type, pos, _CG_BUTTON_NUMBER[msg.button],
        )
        Quartz.CGEventSetIntegerValueField(
            event, Quartz.kCGMouseEventClickState, click_state
        )
        _post_event(event)

    def handle_scroll(self, msg: MouseScroll) -> None:
        """Inject a scroll wheel event.

        CGEvent scroll uses "lines" as units. We pass raw deltas and let
        macOS interpret them. Negative dy = scroll down, positive = scroll up.
        """
        event = Quartz.CGEventCreateScrollWheelEvent(
            None,
            Quartz.kCGScrollEventUnitLine,
            2,       # number of axes (vertical + horizontal)
            msg.dy,  # vertical scroll
            msg.dx,  # horizontal scroll
        )
        _post_event(event)

    def handle_drag(self, msg: MouseDrag) -> None:
        """Move the cursor while a button is held (drag)."""
        pos = _get_cursor_position()
        new_x = pos.x + msg.dx
        new_y = pos.y + msg.dy
        new_point = Quartz.CGPointMake(new_x, new_y)

        drag_type = _BUTTON_MAP_DRAG.get(msg.button, Quartz.kCGEventLeftMouseDragged)

        event = Quartz.CGEventCreateMouseEvent(
            None, drag_type, new_point, _CG_BUTTON_NUMBER[msg.button],
        )
        _post_event(event)
