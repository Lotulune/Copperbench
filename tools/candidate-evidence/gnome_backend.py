"""Task-local native GNOME Wayland adapter for the existing bounded input engine.

This does not register or upgrade the project's MCP server. Window/focus/F8 state
comes from the temporary observer extension. Input uses Mutter RemoteDesktop;
screenshots use the installed GNOME Screenshot application.
"""
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

import gi
gi.require_version('Gdk', '3.0')
from gi.repository import Gio, GLib, Gdk
from PIL import Image
from minecraft_control_bridge.errors import BridgeError
from minecraft_control_bridge.backends.x11 import X11Backend

KEYSYM={'enter':'Return','escape':'Escape','space':'space','shift':'Shift_L','ctrl':'Control_L','alt':'Alt_L',
        'tab':'Tab','backspace':'BackSpace','up':'Up','down':'Down','left':'Left','right':'Right',
        'home':'Home','end':'End','delete':'Delete','pageup':'Prior','pagedown':'Next'}
KEYSYM.update({f'f{i}':f'F{i}' for i in range(1,13)})


class GnomeWaylandBackend:
    platform='linux-gnome-wayland-task'

    def __init__(self):
        if os.environ.get('XDG_SESSION_TYPE')!='wayland' or not os.environ.get('WAYLAND_DISPLAY'):
            raise BridgeError('WAYLAND_REQUIRED','A native GNOME Wayland desktop is required')
        self.bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)
        self.remote=None
        self.keys=set();self.buttons=set()
        self.capture_dir=tempfile.TemporaryDirectory(prefix='p263-gnome-capture-')
        self._state()

    def _call(self,destination,path,interface,method,arguments=None):
        return self.bus.call_sync(destination,path,interface,method,arguments,None,
                                  Gio.DBusCallFlags.NONE,3000,None).unpack()

    def _observer(self,method,arguments=None):
        return self._call('org.gnome.Shell','/dev/copperbench/ValidationObserver',
                          'dev.copperbench.ValidationObserver',method,arguments)

    def _state(self): return json.loads(self._observer('State')[0])

    def window(self,window_id):
        rows=[row for row in self._state()['windows'] if row['window_id']==str(window_id)]
        if len(rows)!=1: raise BridgeError('WINDOW_UNAVAILABLE','The selected GNOME window is no longer unique')
        row=rows[0]
        if not row['native_wayland']: raise BridgeError('WAYLAND_REQUIRED','XWayland cannot satisfy this cell')
        try:
            stat=Path(f"/proc/{row['pid']}/stat").read_text()
            row['process_started']=stat[stat.rfind(')')+2:].split()[19]
            row['executable']=Path(os.readlink(f"/proc/{row['pid']}/exe")).name
        except (OSError,IndexError) as error:
            raise BridgeError('PROCESS_UNAVAILABLE','Cannot verify the selected window process') from error
        return row

    def list_windows(self):
        result=[]
        for row in self._state()['windows']:
            if not row['native_wayland']:continue
            try: current=self.window(row['window_id'])
            except BridgeError:continue
            if 'minecraft' in current['title'].lower() or current['executable']=='java':result.append(current)
        return result

    def focus(self,window_id):
        self.window(window_id)
        self._observer('Focus',GLib.Variant('(s)',(str(window_id),)))
        time.sleep(0.15)
        return self.window(window_id)['focused']

    def arm(self):
        if not self._observer('Arm')[0]:raise BridgeError('ESTOP_UNAVAILABLE','Cannot reserve F8 in GNOME')
        try:
            self.remote=self._call('org.gnome.Mutter.RemoteDesktop','/org/gnome/Mutter/RemoteDesktop',
                                   'org.gnome.Mutter.RemoteDesktop','CreateSession')[0]
            self._input('Start')
        except BaseException:
            self.disarm();raise

    def emergency_pressed(self):return self._state()['emergency']

    def _input(self,method,arguments=None):
        if self.remote is None:raise BridgeError('INPUT_UNAVAILABLE','Input session is not armed')
        return self._call('org.gnome.Mutter.RemoteDesktop',self.remote,
                          'org.gnome.Mutter.RemoteDesktop.Session',method,arguments)

    def key(self,name,down):
        keysym=Gdk.unicode_to_keyval(ord(name)) if len(name)==1 else Gdk.keyval_from_name(KEYSYM.get(name,name))
        if not keysym:raise BridgeError('KEY_UNAVAILABLE','Unknown keysym: '+name)
        if down:self.keys.add(name)
        self._input('NotifyKeyboardKeysym',GLib.Variant('(ub)',(keysym,down)))
        if not down:self.keys.discard(name)

    def button(self,name,down):
        if down:self.buttons.add(name)
        self._input('NotifyPointerButton',GLib.Variant('(ib)',({'left':272,'right':273,'middle':274}[name],down)))
        if not down:self.buttons.discard(name)

    def move_relative(self,dx,dy):
        self._input('NotifyPointerMotionRelative',GLib.Variant('(dd)',(float(dx),float(dy))))

    def move_absolute(self,x,y):
        # Read the compositor pointer after each move; never click if it did not reach the requested point.
        for attempt in range(3):
            px,py=self._state()['pointer']
            if abs(px-x)<1 and abs(py-y)<1:return
            self.move_relative(x-px,y-py)
        px,py=self._state()['pointer']
        if abs(px-x)>=1 or abs(py-y)>=1:raise BridgeError('POINTER_POSITION_UNCONFIRMED','Absolute pointer placement was not confirmed')

    def scroll(self,direction):
        self._input('NotifyPointerAxisDiscrete',GLib.Variant('(ui)',(0,-direction)))

    def type_character(self,character):
        self.key(character,True)
        self.key(character,False)

    def release_all(self):
        for key in list(self.keys):self.key(key,False)
        for button in list(self.buttons):self.button(button,False)

    def disarm(self):
        try:
            if self.remote is not None:
                self.release_all()
                self._input('Stop')
        finally:
            self.remote=None
            self._observer('Disarm')

    def capture(self,window,max_width):
        path=Path(self.capture_dir.name)/(str(uuid.uuid4())+'.png')
        process=subprocess.run(['gnome-screenshot','--file='+str(path)],capture_output=True,timeout=15)
        if process.returncode or not path.is_file():raise BridgeError('CAPTURE_UNAVAILABLE','GNOME Screenshot failed')
        current=self.window(window['window_id'])
        if current['rect']!=window['rect'] or not current['focused']:
            raise BridgeError('WINDOW_CHANGED','Window moved or lost focus during capture')
        rect=current['rect']
        with Image.open(path) as original:
            if min(rect['left'],rect['top'])<0 or rect['left']+rect['width']>original.width or rect['top']+rect['height']>original.height:
                raise BridgeError('WINDOW_NOT_VISIBLE','The complete window must fit the monitor')
            picture=original.crop((rect['left'],rect['top'],rect['left']+rect['width'],rect['top']+rect['height']))
            if picture.width>max_width:picture=picture.resize((max_width,max(1,round(picture.height*max_width/picture.width))),Image.Resampling.LANCZOS)
            output=io.BytesIO();picture.save(output,format='PNG')
            return output.getvalue(),picture.width,picture.height

    def close(self):
        try:self.disarm()
        finally:self.capture_dir.cleanup()


class GnomeX11Backend(X11Backend):
    """Use the real GNOME foreground state while retaining X11 capture and input."""
    def __init__(self):
        super().__init__()
        self.bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)

    def _observer(self,method,arguments=None):
        return self.bus.call_sync('org.gnome.Shell','/dev/copperbench/ValidationObserver',
            'dev.copperbench.ValidationObserver',method,arguments,None,Gio.DBusCallFlags.NONE,3000,None).unpack()

    def _state(self):return json.loads(self._observer('State')[0])

    def diagnostics(self):
        ids=self._property(self.root,'_NET_CLIENT_LIST')
        rows=[]
        for identifier in ([] if ids is None else ids):
            row={'window_id':str(identifier)}
            try:row['verifiedWindow']=self.window(identifier)
            except BridgeError as error:row['inspectionError']={'code':error.code,'message':str(error)}
            window=self.d.create_resource_object('window',int(identifier))
            for name in ('_NET_WM_PID','_NET_WM_NAME','WM_CLASS'):
                value=self._property(window,name)
                row[name]=value.decode('utf-8','replace') if isinstance(value,bytes) else list(value) if value is not None else None
            rows.append(row)
        return {'x11Clients':rows}

    def focus(self,window_id):
        selected=super().window(window_id)
        matches=[w for w in self._state()['windows'] if w['pid']==selected['pid'] and w['title']==selected['title']]
        if len(matches)!=1:raise BridgeError('FOCUS_TARGET_AMBIGUOUS','GNOME cannot uniquely identify this exact X11 window')
        self._observer('Focus',GLib.Variant('(s)',(matches[0]['window_id'],)))
        time.sleep(.25)
        return self.window(window_id)['focused']

    def window(self,window_id):
        row=super().window(window_id)
        state=self._state()
        row['focused']=row['focused'] and not state['overview'] and not state['screenShield'] and not state['modalCount']
        return row
