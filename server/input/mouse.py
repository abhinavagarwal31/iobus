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


def _get_cursor_position() -> Quartz.CGPoint:
    """Return the current cursor position as a CGPoint."""
    event = Quartz.CGEventCreate(None)
    point = Quartz.CGEventGetLocation(event)
    return point


def _post_event(event: Quartz.CGEventRef) -> None:
    """Post a CGEvent to the HID event system."""
    Quartz.CGEventPost(Quartz.kCGHIDEventTap, event)


class MouseController:
    """Manages mouse state and injects mouse events via CGEvent API."""

    def __init__(self) -> None:
        self._held_button: MouseButton | None = None

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
        else:
            event_type = _BUTTON_MAP_UP[msg.button]

        event = Quartz.CGEventCreateMouseEvent(
            None, event_type, pos, _CG_BUTTON_NUMBER[msg.button],
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
