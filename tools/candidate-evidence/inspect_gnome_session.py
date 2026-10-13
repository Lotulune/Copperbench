"""Inspect live GNOME APIs without sending desktop input."""
import json, os
from pathlib import Path
import subprocess
import sys
from gi.repository import Gio, GLib

evidence=Path(os.environ['P263_DESKTOP_EVIDENCE'])
bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)
destination='org.gnome.Mutter.RemoteDesktop'
reply=bus.call_sync(destination,'/org/gnome/Mutter/RemoteDesktop',destination,
                    'CreateSession',None,None,Gio.DBusCallFlags.NONE,10000,None)
session=reply.unpack()[0]
xml=bus.call_sync(destination,session,'org.freedesktop.DBus.Introspectable','Introspect',None,
                  None,Gio.DBusCallFlags.NONE,10000,None).unpack()[0]
(evidence/'remote-desktop-session.xml').write_text(xml,encoding='utf-8')
properties=bus.call_sync(destination,session,'org.freedesktop.DBus.Properties','GetAll',
                         GLib.Variant('(s)',('org.gnome.Mutter.RemoteDesktop.Session',)),None,
                         Gio.DBusCallFlags.NONE,10000,None).unpack()[0]
(evidence/'remote-session-properties.json').write_text(json.dumps(properties,default=str,indent=2)+'\n',encoding='utf-8')
result=subprocess.run(['gnome-screenshot','--file='+str(evidence/'desktop.png')],capture_output=True,timeout=20)
(evidence/'screenshot.stdout').write_bytes(result.stdout)
(evidence/'screenshot.stderr').write_bytes(result.stderr)
(evidence/'screenshot-result.json').write_text(json.dumps({'exitCode':result.returncode,
    'fileCreated':(evidence/'desktop.png').is_file(),'scope':'actual GNOME screenshot, no input sent'})+'\n',encoding='utf-8')
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'minecraft-control-bridge/src'))
if os.environ['P263_DESKTOP']=='wayland':
    from gnome_backend import GnomeWaylandBackend
    backend=GnomeWaylandBackend()
else:
    from gnome_backend import GnomeX11Backend
    backend=GnomeX11Backend()
try:
    backend.arm()
    (evidence/'adapter-probe.json').write_text(json.dumps({'status':'armed_without_pressed_controls',
        'platform':backend.platform,'windows':backend.list_windows(),
        'emergencyPressed':backend.emergency_pressed(),'inputInitialization':getattr(backend,'input_initialization',None),
        'scope':'New virtual devices may receive unpressed releases for initialization; no gameplay input or acceptance'})+'\n',encoding='utf-8')
finally:
    backend.close()
# Closing this connection ends the temporary inspection session; no input lease is retained.
bus.flush_sync(None)
