"""Real GTK input probe for the task adapter; this is never Minecraft acceptance."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time

import gi
gi.require_version('Gtk','3.0')
from gi.repository import Gtk,GLib,Gdk


def save(path,value):
    temporary=path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    temporary.replace(path)


if len(sys.argv)>1 and sys.argv[1]=='--child':
    state=Path(sys.argv[2]);record={'clicks':0,'text':'','events':[],'scope':'GTK input qualification, not Minecraft'}
    window=Gtk.Window(title='Minecraft control input qualification')
    window.set_decorated(False);window.set_default_size(480,240)
    box=Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
    button=Gtk.Button(label='Input qualification button');entry=Gtk.Entry()
    box.pack_start(button,True,True,0);box.pack_end(entry,False,True,0);window.add(box)
    def update(*args):
        record['text']=entry.get_text()
        area=button.get_allocation()
        record['button']={'x':area.x,'y':area.y,'width':area.width,'height':area.height}
        save(state,record)
    def click(*args):record['clicks']+=1;update()
    def event(widget,event):
        details={'widget':widget.get_name(),'type':str(event.type),'monotonic':time.monotonic()}
        for field in ('keyval','hardware_keycode','button','x','y','state','time'):
            try:details[field]=float(getattr(event,field))
            except (AttributeError,TypeError,ValueError):pass
        record['events'].append(details);update()
        return False
    for widget in (window,button,entry):
        widget.add_events(Gdk.EventMask.BUTTON_PRESS_MASK|Gdk.EventMask.BUTTON_RELEASE_MASK|Gdk.EventMask.KEY_PRESS_MASK|Gdk.EventMask.KEY_RELEASE_MASK|Gdk.EventMask.FOCUS_CHANGE_MASK|Gdk.EventMask.ENTER_NOTIFY_MASK|Gdk.EventMask.LEAVE_NOTIFY_MASK)
        for signal in ('key-press-event','key-release-event','button-press-event','button-release-event','focus-in-event','focus-out-event','enter-notify-event','leave-notify-event'):
            widget.connect(signal,event)
    button.connect('clicked',click);entry.connect('changed',update)
    window.connect('destroy',Gtk.main_quit)
    window.show_all();entry.grab_focus()
    GLib.timeout_add(100,lambda: (update(),False)[1])
    GLib.timeout_add_seconds(90,lambda: (Gtk.main_quit(),False)[1])
    Gtk.main();sys.exit(0)

root=Path(os.environ['P263_DESKTOP_EVIDENCE'])/'input-probe';root.mkdir()
state=root/'widget-state.json'
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'minecraft-control-bridge/src'))
from minecraft_control_bridge.engine import Engine
if os.environ['P263_DESKTOP']=='wayland':
    from gnome_backend import GnomeWaylandBackend
    backend=GnomeWaylandBackend()
else:
    from gnome_backend import GnomeX11Backend
    backend=GnomeX11Backend()
engine=Engine(backend);child=subprocess.Popen([sys.executable,__file__,'--child',str(state)])
record={'scope':'Actual GTK callbacks and screenshots; no Minecraft player acceptance','status':'running'}
try:
    deadline=time.monotonic()+20;window=None
    while time.monotonic()<deadline:
        rows=[w for w in backend.list_windows() if w['pid']==child.pid]
        if len(rows)==1 and state.exists():window=rows[0];break
        time.sleep(.1)
    assert window is not None,'Probe window was not found'
    attached=engine.attach(window['window_id'],root/'bridge',focus=True)
    record['inputInitialization']=getattr(backend,'input_initialization',None)
    sid=attached['session_id'];time.sleep(.5)
    before=engine.observe(sid);save(root/'before.json',before)
    result=engine.step(sid,'probe-text',[{'type':'text','text':'abc123'},{'type':'wait','duration_ms':500}])
    save(root/'keyboard.json',result);assert result['status']=='completed',result
    after_text=json.loads(state.read_text(encoding='utf-8'));record['afterKeyboard']=after_text
    area=after_text['button'];frame=result['observation']['frame_id']
    result=engine.step(sid,'probe-click',[{'type':'click','x':area['x']+area['width']//2,
                       'y':area['y']+area['height']//2,'frame_id':frame},{'type':'wait','duration_ms':500}])
    save(root/'pointer.json',result);assert result['status']=='completed',result
    final=json.loads(state.read_text(encoding='utf-8'));record['afterPointer']=final
    if hasattr(backend,'_state'):record['compositorState']=backend._state()
    assert after_text['text']=='abc123' and final['clicks']==1,'Compositor input did not reach actual widget callbacks'
    from gnome_backend import check_focus_loss
    record['focusLoss']=check_focus_loss(engine,sid,'probe-focus-loss')
    save(root/'focus-loss.json',record['focusLoss'])
    assert not record['focusLoss']['heldKeys'] and not record['focusLoss']['heldButtons']
    captured=subprocess.run(['gnome-screenshot','--file='+str(root/'focus-overview.png')],capture_output=True,timeout=15)
    assert captured.returncode==0 and (root/'focus-overview.png').is_file()
    # The next action must require explicit detach/attach; do not steal focus back.
    record['status']='passed'
except BaseException as error:
    record.update(status='failed',error=str(error));raise
finally:
    engine.close();child.terminate()
    try:child.wait(timeout=5)
    except subprocess.TimeoutExpired:child.kill();child.wait(timeout=5)
    if state.exists():record['finalWidgetState']=json.loads(state.read_text(encoding='utf-8'))
    save(root/'result.json',record)
