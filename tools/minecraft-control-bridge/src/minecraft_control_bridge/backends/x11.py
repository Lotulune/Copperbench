import os
import time
from pathlib import Path

from Xlib import X, XK, display, error
from Xlib.ext import xtest

from .base import DesktopBackend
from ..errors import BridgeError

KEYSYM = {"space": "space", "shift": "Shift_L", "ctrl": "Control_L", "alt": "Alt_L",
          "enter": "Return", "escape": "Escape", "tab": "Tab", "backspace": "BackSpace",
          "up": "Up", "down": "Down", "left": "Left", "right": "Right", "home": "Home",
          "end": "End", "delete": "Delete", "pageup": "Prior", "pagedown": "Next"}
KEYSYM.update({f"f{i}": f"F{i}" for i in range(1, 13)})


class X11Backend(DesktopBackend):
    platform = "linux-x11"

    def __init__(self):
        try:
            self.d = display.Display()
        except Exception as exc:
            raise BridgeError("DISPLAY_UNAVAILABLE", "Cannot connect to DISPLAY with current Xauthority") from exc
        if not self.d.has_extension("XTEST"):
            self.d.close()
            raise BridgeError("INPUT_UNAVAILABLE", "The X server has no XTEST extension")
        self.root = self.d.screen().root
        self.keys, self.buttons = set(), set()
        self.armed = False

    def _property(self, window, name):
        prop = window.get_full_property(self.d.intern_atom(name), X.AnyPropertyType)
        return None if prop is None else prop.value

    def window(self, window_id):
        try:
            window = self.d.create_resource_object("window", int(window_id))
            pid_property = self._property(window, "_NET_WM_PID")
            if pid_property is None:
                raise BridgeError("PROCESS_UNAVAILABLE", "Window has no _NET_WM_PID")
            pid = int(pid_property[0])
            # /proc comm may itself contain spaces and parentheses.
            stat = Path(f"/proc/{pid}/stat").read_text()
            birth = stat[stat.rfind(")") + 2:].split()[19]
            executable = Path(os.readlink(f"/proc/{pid}/exe")).name
            geom = window.get_geometry()
            position = self.root.translate_coords(window, 0, 0)
            active = self._property(self.root, "_NET_ACTIVE_WINDOW")
            focused = int(active[0]) == window.id if active is not None and len(active) else self.d.get_input_focus().focus == window
            title = self._property(window, "_NET_WM_NAME")
            if isinstance(title, bytes):
                title = title.decode("utf-8", "replace")
            else:
                title = window.get_wm_name() or ""
            return {"window_id": str(window.id), "pid": pid, "process_started": birth,
                    "title": title, "executable": executable, "focused": focused,
                    "visible": window.get_attributes().map_state == X.IsViewable,
                    "rect": {"left": position.x, "top": position.y, "width": geom.width, "height": geom.height}}
        except BridgeError:
            raise
        except (error.XError, OSError, ValueError, IndexError) as exc:
            raise BridgeError("WINDOW_UNAVAILABLE", "Cannot inspect the X11 window and its local process") from exc

    def list_windows(self):
        ids = self._property(self.root, "_NET_CLIENT_LIST")
        if ids is None:
            ids = [w.id for w in self.root.query_tree().children]
        result = []
        for wid in ids:
            try:
                row = self.window(wid)
                if "minecraft" in row["title"].lower() or row["executable"] in {"java", "javaw"}:
                    result.append(row)
            except BridgeError:
                pass
        return result

    def focus(self, window_id):
        from Xlib.protocol import event
        window = self.d.create_resource_object("window", int(window_id))
        if self._property(self.root, "_NET_SUPPORTING_WM_CHECK") is None:
            # Dedicated/nested X11 test displays can intentionally have no window manager.
            window.configure(stack_mode=X.Above)
            window.set_input_focus(X.RevertToParent, X.CurrentTime)
            self.d.sync()
            return self.window(window_id)["focused"]
        self.root.send_event(event.ClientMessage(window=window, client_type=self.d.intern_atom("_NET_ACTIVE_WINDOW"),
                                                 data=(32, [2, X.CurrentTime, 0, 0, 0])),
                             event_mask=X.SubstructureRedirectMask | X.SubstructureNotifyMask)
        self.d.sync()
        time.sleep(0.05)
        return self.window(window_id)["focused"]

    def arm(self):
        self.f8 = self.d.keysym_to_keycode(XK.string_to_keysym("F8"))
        catcher = error.CatchError(error.BadAccess)
        self.root.grab_key(self.f8, X.AnyModifier, False, X.GrabModeAsync, X.GrabModeAsync, onerror=catcher)
        self.d.sync()
        if catcher.get_error():
            raise BridgeError("ESTOP_UNAVAILABLE", "Cannot reserve F8 on this X11 desktop")
        self.armed = True

    def emergency_pressed(self):
        while self.d.pending_events():
            e = self.d.next_event()
            if e.type == X.KeyPress and e.detail == self.f8:
                return True
        # Active pointer/keyboard grabs in games may prevent passive hotkey delivery.
        bitmap = self.d.query_keymap()
        return bool(bitmap[self.f8 // 8] & (1 << (self.f8 % 8))) if self.armed else False

    def disarm(self):
        if self.armed:
            self.root.ungrab_key(self.f8, X.AnyModifier)
            self.d.sync()
            self.armed = False

    def _keycode(self, name):
        code = self.d.keysym_to_keycode(XK.string_to_keysym(KEYSYM.get(name, name)))
        if not code:
            raise BridgeError("KEY_UNAVAILABLE", f"No X11 keycode for {name}")
        return code

    def key(self, name, down):
        code = self._keycode(name)
        if down:
            self.keys.add(name)
        xtest.fake_input(self.d, X.KeyPress if down else X.KeyRelease, code)
        self.d.sync()
        if not down:
            self.keys.discard(name)

    def button(self, name, down):
        if down:
            self.buttons.add(name)
        xtest.fake_input(self.d, X.ButtonPress if down else X.ButtonRelease, {"left": 1, "middle": 2, "right": 3}[name])
        self.d.sync()
        if not down:
            self.buttons.discard(name)

    def move_relative(self, dx, dy):
        xtest.fake_input(self.d, X.MotionNotify, detail=1, x=dx, y=dy)
        self.d.sync()

    def move_absolute(self, x, y):
        xtest.fake_input(self.d, X.MotionNotify, x=x, y=y)
        self.d.sync()

    def scroll(self, direction):
        code = 4 if direction > 0 else 5
        xtest.fake_input(self.d, X.ButtonPress, code)
        xtest.fake_input(self.d, X.ButtonRelease, code)
        self.d.sync()

    def type_character(self, character):
        symbol = ord(character)
        matches = [(code, level) for code, level in self.d.keysym_to_keycodes(symbol) if level in (0, 1)]
        if not matches:
            raise BridgeError("TEXT_UNAVAILABLE", "ASCII character is unavailable in the active keyboard layout")
        code, level = matches[0]
        try:
            if level:
                self.key("shift", True)
            xtest.fake_input(self.d, X.KeyPress, code)
            xtest.fake_input(self.d, X.KeyRelease, code)
            self.d.sync()
        finally:
            if level:
                self.key("shift", False)

    def release_all(self):
        for key in list(self.keys):
            self.key(key, False)
        for button in list(self.buttons):
            self.button(button, False)

    def close(self):
        try:
            self.release_all()
        finally:
            self.disarm()
            self.d.close()
