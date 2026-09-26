"""Opt-in real desktop test. Creates and controls only its own disposable fixture.

Run: python tests/live_desktop.py --output /absolute/evidence/path
This proves the OS backend, not Minecraft gameplay. No tests execute this implicitly.
"""
import argparse
import json
import os
import threading
import time
import traceback
from pathlib import Path

from minecraft_control_bridge import Bridge


def run_checks(output, events, done):
    result = {"kind": "desktop_fixture_not_minecraft", "passed": False}
    try:
        with Bridge() as bridge:
            candidates = [w for w in bridge.list_windows()["windows"] if w["pid"] == os.getpid()
                          and w["title"] == "Minecraft Control Bridge Fixture"]
            assert len(candidates) == 1, candidates
            attached = bridge.attach(candidates[0]["window_id"], evidence_dir=output)
            sid = attached["session_id"]
            observed = bridge.observe(sid)
            from PIL import Image
            picture = Image.open(observed["image"]["path"])
            color = picture.getpixel((picture.width // 2, picture.height // 2))
            assert all(abs(a - b) < 5 for a, b in zip(color, (22, 76, 53))), color
            anchor = bridge.step(sid, "anchor", [{"type": "click", "x": 320, "y": 240,
                                                   "frame_id": observed["frame_id"]}])
            assert anchor["status"] == "completed", anchor
            actions = [{"type": "hold", "keys": ["w", "space"], "dx": 25, "dy": 12, "duration_ms": 150}]
            first = bridge.step(sid, "move", actions)
            assert first["status"] == "completed", first
            count = len(events)
            assert bridge.step(sid, "move", actions)["replayed"]
            time.sleep(0.05)
            assert len(events) == count, "replayed request produced native events"
            observed = bridge.observe(sid)
            clicked = bridge.step(sid, "click", [{"type": "click", "x": 160, "y": 180,
                                                  "frame_id": observed["frame_id"]}])
            assert clicked["status"] == "completed", clicked
            start = len(events)
            typed = bridge.step(sid, "text", [{"type": "text", "text": "bridge123"}])
            assert typed["status"] == "completed", typed
            time.sleep(0.1)
            text = "".join(e.get("char", "") for e in events[start:] if e["type"] == "key_down")
            assert text == "bridge123", (text, events[start:])
            assert any(e["type"] == "key_down" and e.get("key") == "w" for e in events), events
            assert any(e["type"] == "key_up" and e.get("key") == "w" for e in events), events
            assert any(e["type"] == "button_down" for e in events), events
            assert any(e["type"] == "button_up" for e in events), events
            assert any(e["type"] == "motion" for e in events), events
            bridge.observe(sid)
            receipt = []
            thread = threading.Thread(target=lambda: receipt.append(bridge.step(sid, "interrupt", [
                {"type": "hold", "keys": ["w"], "duration_ms": 1900}])))
            thread.start()
            time.sleep(0.15)
            stop = bridge.stop(sid)
            thread.join(timeout=5)
            assert receipt and receipt[0]["status"] == "interrupted", receipt
            assert receipt[0]["error"]["code"] == "STOPPED", receipt
            assert receipt[0]["input_elapsed_ms"] < 1000, receipt
            detached = bridge.detach(sid)
            result.update(passed=True, target=attached["target"], evidence=detached,
                          screenshot_color=color, text_received=text, stop=stop,
                          interrupt_ms=receipt[0]["input_elapsed_ms"], events=events)
    except BaseException:
        result["error"] = traceback.format_exc()
    finally:
        output.mkdir(parents=True, exist_ok=True)
        (output / "desktop-fixture-result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps({k: v for k, v in result.items() if k != "events"}, indent=2), flush=True)
        done.set()


def windows_fixture(output, arm_file=None):
    import tkinter as tk
    root = tk.Tk()
    root.title("Minecraft Control Bridge Fixture")
    root.geometry("640x480+100+100")
    root.configure(bg="#164c35")
    root.update_idletasks()
    # Disable composition only in this disposable fixture, not the user's input language.
    # Minecraft uses GLFW key events; Tk otherwise receives VK_PROCESSKEY under an active IME.
    import ctypes
    imm = ctypes.WinDLL("imm32")
    imm.ImmAssociateContext.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    imm.ImmAssociateContext.restype = ctypes.c_void_p
    imm.ImmAssociateContext(root.winfo_id(), None)
    events, done = [], threading.Event()
    root.bind("<KeyPress>", lambda e: events.append({"type": "key_down", "key": e.keysym.lower(), "char": e.char}))
    root.bind("<KeyRelease>", lambda e: events.append({"type": "key_up", "key": e.keysym.lower()}))
    root.bind("<ButtonPress>", lambda e: events.append({"type": "button_down", "button": e.num}))
    root.bind("<ButtonRelease>", lambda e: events.append({"type": "button_up", "button": e.num}))
    root.bind("<Motion>", lambda e: events.append({"type": "motion", "x": e.x, "y": e.y}))
    def start():
        if arm_file and not arm_file.exists():
            root.after(100, start)
            return
        root.lift()
        root.focus_force()
        threading.Thread(target=run_checks, args=(output, events, done), daemon=True).start()
    def poll():
        if done.is_set():
            root.destroy()
        else:
            root.after(50, poll)
    root.after(500, start)
    root.after(100, poll)
    root.mainloop()


def x11_fixture(output):
    from Xlib import X, XK, Xatom, display
    d = display.Display()
    root = d.screen().root
    window = root.create_window(100, 100, 640, 480, 0, d.screen().root_depth,
                                X.InputOutput, X.CopyFromParent, background_pixel=0x164C35,
                                event_mask=X.KeyPressMask | X.KeyReleaseMask | X.ButtonPressMask |
                                X.ButtonReleaseMask | X.PointerMotionMask | X.StructureNotifyMask)
    window.set_wm_name("Minecraft Control Bridge Fixture")
    window.change_property(d.intern_atom("_NET_WM_PID"), Xatom.CARDINAL, 32, [os.getpid()])
    window.map()
    d.sync()
    window.set_input_focus(X.RevertToParent, X.CurrentTime)
    d.sync()
    events, done = [], threading.Event()
    threading.Thread(target=run_checks, args=(output, events, done), daemon=True).start()
    while not done.is_set():
        if not d.pending_events():
            time.sleep(0.005)
            continue
        e = d.next_event()
        if e.type in (X.KeyPress, X.KeyRelease):
            symbol = d.keycode_to_keysym(e.detail, 1 if e.state & X.ShiftMask else 0)
            events.append({"type": "key_down" if e.type == X.KeyPress else "key_up",
                           "key": (XK.keysym_to_string(symbol) or "").lower(),
                           "char": chr(symbol) if 32 <= symbol <= 126 else ""})
        elif e.type in (X.ButtonPress, X.ButtonRelease):
            events.append({"type": "button_down" if e.type == X.ButtonPress else "button_up", "button": e.detail})
        elif e.type == X.MotionNotify:
            events.append({"type": "motion", "x": e.event_x, "y": e.event_y})
    window.destroy()
    d.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--arm-file", type=Path, help="Wait for this file so an external controller can focus the fixture")
    args = parser.parse_args()
    if os.name == "nt":
        windows_fixture(args.output.resolve(), args.arm_file)
    else:
        x11_fixture(args.output.resolve())
    report = json.loads((args.output / "desktop-fixture-result.json").read_text())
    raise SystemExit(0 if report["passed"] else 1)
