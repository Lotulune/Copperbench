"""Validate an entire batch before issuing any operating system input."""
import math

from .errors import BridgeError

KEYS = set("abcdefghijklmnopqrstuvwxyz0123456789") | {
    "space", "shift", "ctrl", "alt", "enter", "escape", "tab", "backspace",
    "up", "down", "left", "right", "home", "end", "delete", "pageup", "pagedown",
    *[f"f{i}" for i in range(1, 13) if i != 8],
}
BUTTONS = {"left", "right", "middle"}
MAX_DURATION_MS = 2000


def action_duration_ms(action):
    """Declared cost of an already validated action, shared with receipt budgeting."""
    if action["type"] == "text":
        return len(action["text"]) * 10
    if action["type"] == "scroll":
        return abs(action["steps"]) * 10
    return action["duration_ms"] + (action["settle_ms"] if action["type"] == "click" else 0)


def invalid(message):
    raise BridgeError("INVALID_ACTION", message)


def number(value, name, low, high):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        invalid(f"{name} must be a finite number")
    if not low <= value <= high:
        invalid(f"{name} must be between {low} and {high}")
    return value


def validate_actions(actions: list[dict]) -> list[dict]:
    if not isinstance(actions, list) or not 1 <= len(actions) <= 32:
        invalid("Provide 1..32 actions")
    result, total = [], 0
    for source in actions:
        if not isinstance(source, dict):
            invalid("Each action must be an object")
        action = dict(source)
        kind = action.get("type")
        fields = {
            "hold": {"keys", "buttons", "dx", "dy", "duration_ms"},
            "look": {"dx", "dy", "duration_ms"},
            "click": {"x", "y", "frame_id", "button", "duration_ms", "settle_ms"},
            "scroll": {"steps"}, "text": {"text"}, "wait": {"duration_ms"},
        }
        if not isinstance(kind, str) or kind not in fields:
            invalid("Unknown action type")
        if set(action) - fields[kind] - {"type"}:
            invalid(f"Unknown fields for {kind}")
        if kind in {"hold", "look", "wait", "click"}:
            duration = action.setdefault("duration_ms", 80 if kind == "click" else 100)
            number(duration, "duration_ms", 1, MAX_DURATION_MS)
        elif kind == "text":
            value = action.get("text")
            if not isinstance(value, str) or not 1 <= len(value) <= 128 or any(not 32 <= ord(c) <= 126 for c in value):
                invalid("text must contain 1..128 printable ASCII characters; send Enter separately")
            duration = len(value) * 10
        else:
            steps = action.get("steps")
            number(steps, "steps", -20, 20)
            if not isinstance(steps, int) or not steps:
                invalid("steps must be a nonzero integer")
            duration = abs(steps) * 10
        if kind in {"hold", "look"}:
            for axis in ("dx", "dy"):
                number(action.setdefault(axis, 0), axis, -4000, 4000)
            if kind == "hold":
                for field, allowed in (("keys", KEYS), ("buttons", BUTTONS)):
                    values = action.setdefault(field, [])
                    if not isinstance(values, list) or any(not isinstance(x, str) or x not in allowed for x in values):
                        invalid(f"Unsupported {field}; F8 is reserved for emergency stop")
                    if len(set(values)) != len(values):
                        invalid(f"Duplicate {field}")
        if kind == "click":
            settle = action.setdefault("settle_ms", 100)
            number(settle, "settle_ms", 0, 500)
            duration += settle
            for axis in ("x", "y"):
                number(action.get(axis), axis, 0, 32767)
            if not isinstance(action.get("frame_id"), str):
                invalid("click requires frame_id from the latest observation")
            if not isinstance(action.setdefault("button", "left"), str) or action["button"] not in BUTTONS:
                invalid("Unknown mouse button")
        total += action_duration_ms(action)
        result.append(action)
    if total > MAX_DURATION_MS:
        invalid("Total batch duration exceeds 2000 ms; observe and continue in another step")
    return result
