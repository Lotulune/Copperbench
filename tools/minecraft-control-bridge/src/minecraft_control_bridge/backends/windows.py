"""Win32 input uses scan codes and relative motion (never window-message clicks)."""
import ctypes as C
from ctypes import wintypes as W
import os
import time

from .base import DesktopBackend
from ..errors import BridgeError

ULONG_PTR = C.c_size_t


class MOUSEINPUT(C.Structure):
    _fields_ = [("dx", W.LONG), ("dy", W.LONG), ("mouseData", W.DWORD),
                ("dwFlags", W.DWORD), ("time", W.DWORD), ("dwExtraInfo", ULONG_PTR)]


class KEYBDINPUT(C.Structure):
    _fields_ = [("wVk", W.WORD), ("wScan", W.WORD), ("dwFlags", W.DWORD),
                ("time", W.DWORD), ("dwExtraInfo", ULONG_PTR)]


class HARDWAREINPUT(C.Structure):
    _fields_ = [("uMsg", W.DWORD), ("wParamL", W.WORD), ("wParamH", W.WORD)]


class INPUTUNION(C.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(C.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", W.DWORD), ("u", INPUTUNION)]


VK = {"space": 0x20, "shift": 0x10, "ctrl": 0x11, "alt": 0x12, "enter": 0x0D,
      "escape": 0x1B, "tab": 9, "backspace": 8, "up": 0x26, "down": 0x28,
      "left": 0x25, "right": 0x27, "home": 0x24, "end": 0x23, "delete": 0x2E,
      "pageup": 0x21, "pagedown": 0x22}
VK.update({f"f{i}": 0x6F + i for i in range(1, 13)})
EXTENDED = {"up", "down", "left", "right", "home", "end", "delete", "pageup", "pagedown"}


class WindowsBackend(DesktopBackend):
    platform = "windows"

    def __init__(self):
        self.u = C.WinDLL("user32", use_last_error=True)
        self.k = C.WinDLL("kernel32", use_last_error=True)
        self.keys, self.buttons = set(), set()
        self._hotkey = False
        self._enum_type = C.WINFUNCTYPE(W.BOOL, W.HWND, W.LPARAM)
        signatures = {
            "EnumWindows": ([self._enum_type, W.LPARAM], W.BOOL),
            "IsWindow": ([W.HWND], W.BOOL), "IsWindowVisible": ([W.HWND], W.BOOL),
            "IsIconic": ([W.HWND], W.BOOL), "GetForegroundWindow": ([], W.HWND),
            "GetWindowThreadProcessId": ([W.HWND, C.POINTER(W.DWORD)], W.DWORD),
            "GetWindowTextLengthW": ([W.HWND], C.c_int),
            "GetWindowTextW": ([W.HWND, W.LPWSTR, C.c_int], C.c_int),
            "GetClientRect": ([W.HWND, C.POINTER(W.RECT)], W.BOOL),
            "ClientToScreen": ([W.HWND, C.POINTER(W.POINT)], W.BOOL),
            "SetForegroundWindow": ([W.HWND], W.BOOL),
            "SendInput": ([W.UINT, C.POINTER(INPUT), C.c_int], W.UINT),
            "MapVirtualKeyW": ([W.UINT, W.UINT], W.UINT),
            "GetAsyncKeyState": ([C.c_int], W.SHORT),
            "RegisterHotKey": ([W.HWND, C.c_int, W.UINT, W.UINT], W.BOOL),
            "UnregisterHotKey": ([W.HWND, C.c_int], W.BOOL),
            "PeekMessageW": ([C.POINTER(W.MSG), W.HWND, W.UINT, W.UINT, W.UINT], W.BOOL),
            "SetCursorPos": ([C.c_int, C.c_int], W.BOOL),
            "GetSystemMetrics": ([C.c_int], C.c_int),
        }
        for name, (args, result) in signatures.items():
            fn = getattr(self.u, name)
            fn.argtypes, fn.restype = args, result
        self.k.OpenProcess.argtypes, self.k.OpenProcess.restype = [W.DWORD, W.BOOL, W.DWORD], W.HANDLE
        self.k.CloseHandle.argtypes = [W.HANDLE]
        self.k.GetProcessTimes.argtypes = [W.HANDLE] + [C.POINTER(W.FILETIME)] * 4
        self.k.QueryFullProcessImageNameW.argtypes = [W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)]
        # Must happen before capture libraries create any coordinate-related state.
        dpi = self.u.SetProcessDpiAwarenessContext
        dpi.argtypes, dpi.restype = [C.c_void_p], W.BOOL
        dpi(C.c_void_p(-4))  # per-monitor aware v2; already configured is harmless

    def _process(self, pid):
        handle = self.k.OpenProcess(0x1000, False, pid)
        if not handle:
            raise BridgeError("PROCESS_UNAVAILABLE", "Cannot inspect the target process; run at the same integrity level")
        try:
            created, exited, kernel, user = [W.FILETIME() for _ in range(4)]
            if not self.k.GetProcessTimes(handle, C.byref(created), C.byref(exited), C.byref(kernel), C.byref(user)):
                raise BridgeError("PROCESS_UNAVAILABLE", "Cannot obtain target creation time")
            name, size = C.create_unicode_buffer(32768), W.DWORD(32768)
            self.k.QueryFullProcessImageNameW(handle, 0, name, C.byref(size))
            return str((created.dwHighDateTime << 32) | created.dwLowDateTime), os.path.basename(name.value)
        finally:
            self.k.CloseHandle(handle)

    def window(self, window_id):
        try:
            hwnd = int(window_id)
        except (ValueError, TypeError):
            raise BridgeError("WINDOW_UNAVAILABLE", "Invalid window ID")
        if not self.u.IsWindow(hwnd):
            raise BridgeError("WINDOW_UNAVAILABLE", "Window no longer exists")
        pid = W.DWORD()
        self.u.GetWindowThreadProcessId(hwnd, C.byref(pid))
        birth, executable = self._process(pid.value)
        title = C.create_unicode_buffer(self.u.GetWindowTextLengthW(hwnd) + 1)
        self.u.GetWindowTextW(hwnd, title, len(title))
        rect, origin = W.RECT(), W.POINT()
        if not self.u.GetClientRect(hwnd, C.byref(rect)) or not self.u.ClientToScreen(hwnd, C.byref(origin)):
            raise BridgeError("WINDOW_UNAVAILABLE", "Cannot read the client area")
        return {"window_id": str(hwnd), "pid": pid.value, "process_started": birth,
                "title": title.value, "executable": executable,
                "focused": self.u.GetForegroundWindow() == hwnd,
                "visible": bool(self.u.IsWindowVisible(hwnd)) and not self.u.IsIconic(hwnd),
                "rect": {"left": origin.x, "top": origin.y,
                         "width": rect.right - rect.left, "height": rect.bottom - rect.top}}

    def list_windows(self):
        result = []

        @self._enum_type
        def visit(hwnd, _):
            if self.u.IsWindowVisible(hwnd) and self.u.GetWindowTextLengthW(hwnd):
                try:
                    row = self.window(str(hwnd))
                    if "minecraft" in row["title"].lower() or row["executable"].lower() in {"java.exe", "javaw.exe"}:
                        result.append(row)
                except BridgeError:
                    pass
            return True
        self.u.EnumWindows(visit, 0)
        return result

    def focus(self, window_id):
        self.u.SetForegroundWindow(int(window_id))
        time.sleep(0.05)
        return self.window(window_id)["focused"]

    def arm(self):
        if not self.u.RegisterHotKey(None, 0x4D43, 0x4000, VK["f8"]):
            raise BridgeError("ESTOP_UNAVAILABLE", "Cannot reserve F8; another application may own it")
        self._hotkey = True

    def emergency_pressed(self):
        message = W.MSG()
        while self.u.PeekMessageW(C.byref(message), None, 0x0312, 0x0312, 1):
            if message.wParam == 0x4D43:
                return True
        return bool(self.u.GetAsyncKeyState(VK["f8"]) & 0x8000)

    def disarm(self):
        if self._hotkey:
            self.u.UnregisterHotKey(None, 0x4D43)
            self._hotkey = False

    def _send(self, entry):
        if self.u.SendInput(1, C.byref(entry), C.sizeof(INPUT)) != 1:
            raise BridgeError("INPUT_REJECTED", "SendInput failed; check focus and process integrity", {"winerror": C.get_last_error()})

    def key(self, name, down):
        vk = VK.get(name, ord(name.upper()) if len(name) == 1 else 0)
        scan = self.u.MapVirtualKeyW(vk, 0)
        flags = 0x0008 | (0 if down else 0x0002) | (1 if name in EXTENDED else 0)
        if down:
            self.keys.add(name)  # also release if insertion fails partway through a batch
        self._send(INPUT(type=1, ki=KEYBDINPUT(wScan=scan, dwFlags=flags)))
        if not down:
            self.keys.discard(name)

    def button(self, name, down):
        flags = {"left": (2, 4), "right": (8, 16), "middle": (32, 64)}[name][not down]
        if down:
            self.buttons.add(name)
        self._send(INPUT(type=0, mi=MOUSEINPUT(dwFlags=flags)))
        if not down:
            self.buttons.discard(name)

    def move_relative(self, dx, dy):
        self._send(INPUT(type=0, mi=MOUSEINPUT(dx=dx, dy=dy, dwFlags=0x0001)))

    def move_absolute(self, x, y):
        if not self.u.SetCursorPos(x, y):
            raise BridgeError("INPUT_REJECTED", "SetCursorPos failed")

    def scroll(self, direction):
        self._send(INPUT(type=0, mi=MOUSEINPUT(mouseData=(direction * 120) & 0xFFFFFFFF, dwFlags=0x0800)))

    def type_character(self, character):
        # Unicode packets avoid dependence on the active keyboard layout and clipboard.
        for flags in (0x0004, 0x0006):
            self._send(INPUT(type=1, ki=KEYBDINPUT(wScan=ord(character), dwFlags=flags)))

    def release_all(self):
        errors = []
        for name in list(self.keys):
            try:
                self.key(name, False)
            except BridgeError as error:
                errors.append(error)
        for name in list(self.buttons):
            try:
                self.button(name, False)
            except BridgeError as error:
                errors.append(error)
        if errors:
            raise errors[0]

    def close(self):
        try:
            self.release_all()
        finally:
            self.disarm()
