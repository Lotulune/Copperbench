"""Opt-in real Win32 IME regression in an owned disposable window, not Minecraft."""
import argparse
import ctypes as C
import json
import os
import threading
import time
import traceback
from pathlib import Path
import tkinter as tk

from minecraft_control_bridge import Bridge


def main(output, native_only=False):
    root=tk.Tk();root.title('Minecraft Bridge IME Regression')
    root.geometry('640x480+100+100');root.configure(bg='#164c35')
    root.update_idletasks()
    imm=C.WinDLL('imm32')
    imm.ImmGetContext.argtypes=[C.c_void_p];imm.ImmGetContext.restype=C.c_void_p
    imm.ImmGetOpenStatus.argtypes=[C.c_void_p];imm.ImmGetOpenStatus.restype=C.c_int
    imm.ImmSetOpenStatus.argtypes=[C.c_void_p,C.c_int];imm.ImmSetOpenStatus.restype=C.c_int
    imm.ImmReleaseContext.argtypes=[C.c_void_p,C.c_void_p]
    events=[];done=threading.Event();report={'kind':'owned_windows_ime_fixture','passed':False}
    root.bind('<KeyPress>',lambda e:events.append({'key':e.keysym,'char':e.char}))
    context=imm.ImmGetContext(root.winfo_id())
    original=bool(imm.ImmGetOpenStatus(context))
    def checks():
        try:
            if native_only:
                # IME APIs on this fixture only; no key/mouse input or focus-check bypass.
                from minecraft_control_bridge.backends.windows import WindowsBackend
                with_backend=WindowsBackend()
                try:
                    row=next(w for w in with_backend.list_windows() if w['pid']==os.getpid() and w['title']=='Minecraft Bridge IME Regression')
                    report['before']=with_backend.keyboard_status(row['window_id'])
                    assert report['before']['ime_open'] is True,report
                    with_backend.request_ime_open(row['window_id'],False)
                    time.sleep(.1)
                    report['english']=with_backend.keyboard_status(row['window_id'])
                    assert report['english']['ime_open'] is False,report
                    with_backend.request_ime_open(row['window_id'],True)
                    time.sleep(.1)
                    report['restored']=with_backend.keyboard_status(row['window_id'])
                    assert report['restored']['ime_open'] is True,report
                    report.update(passed=True,scope='native_ime_toggle_and_restore_only',keyboard_input_verified=False)
                finally:with_backend.close()
                return
            with Bridge() as bridge:
                row=next(w for w in bridge.list_windows()['windows'] if w['pid']==os.getpid() and w['title']=='Minecraft Bridge IME Regression')
                report['before']=row.get('keyboard')
                assert report['before']['ime_open'] is True,report['before']
                sid=bridge.attach(row['window_id'],evidence_dir=output,focus=True)['session_id']
                bridge.observe(sid)
                receipt=bridge.step(sid,'english-e',[{'type':'hold','keys':['e'],'duration_ms':120},{'type':'wait','duration_ms':200}])
                report['receipt']=receipt
                assert receipt['status']=='completed',receipt
                assert receipt['keyboard']['changed'],receipt
                assert any(e['char']=='e' for e in events),events
                report['detach']=bridge.detach(sid)
                after=next(w for w in bridge.list_windows()['windows'] if w['window_id']==row['window_id'])
                report['after']=after['keyboard']
                assert report['after']['ime_open'] is True,report
                report['passed']=True
        except BaseException:
            report['error']=traceback.format_exc()
        finally:
            report['events']=events
            output.mkdir(parents=True,exist_ok=True)
            (output/'ime-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print(json.dumps({k:v for k,v in report.items() if k not in {'receipt','detach'}},indent=2),flush=True)
            done.set()
    def start():
        root.lift();root.focus_force()
        imm.ImmSetOpenStatus(context,True)
        root.after(250,lambda:threading.Thread(target=checks,daemon=True).start())
    def poll():
        if done.is_set():
            imm.ImmSetOpenStatus(context,original)
            imm.ImmReleaseContext(root.winfo_id(),context)
            root.destroy()
        else:root.after(30,poll)
    root.after(400,start);root.after(50,poll);root.mainloop()
    return 0 if report['passed'] else 1


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--native-only',action='store_true')
    args=parser.parse_args()
    raise SystemExit(main(args.output.resolve(),args.native_only))
