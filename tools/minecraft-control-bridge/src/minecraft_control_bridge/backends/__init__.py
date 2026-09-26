import os
import sys

from ..errors import BridgeError


def create_backend():
    if sys.platform == "win32":
        from .windows import WindowsBackend
        return WindowsBackend()
    if sys.platform == "linux":
        if os.environ.get("XDG_SESSION_TYPE", "").lower() == "wayland" or os.environ.get("WAYLAND_DISPLAY"):
            raise BridgeError("WAYLAND_UNSUPPORTED", "Use an X11 desktop session; XWayland is not certified")
        if not os.environ.get("DISPLAY"):
            raise BridgeError("DISPLAY_UNAVAILABLE", "An interactive X11 desktop is required")
        from .x11 import X11Backend
        return X11Backend()
    raise BridgeError("PLATFORM_UNSUPPORTED", "MVP supports Windows and Linux X11")
