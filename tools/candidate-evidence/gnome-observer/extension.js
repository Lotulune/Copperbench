import Gio from 'gi://Gio';
import Clutter from 'gi://Clutter';
import Meta from 'gi://Meta';
import Shell from 'gi://Shell';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const XML = `<node><interface name="dev.copperbench.ValidationObserver">
<method name="State"><arg type="s" direction="out"/></method>
<method name="Arm"><arg type="b" direction="out"/></method>
<method name="Disarm"/>
<method name="Focus"><arg type="s" direction="in"/><arg type="b" direction="out"/></method>
</interface></node>`;

export default class ValidationObserver extends Extension {
    enable() {
        this._emergency = false;
        this._action = 0;
        // A headless Mutter seat initially has no input devices. Advertise both
        // capabilities before clients bind wl_seat; creating them on the first
        // real key/click races the client's keyboard/pointer subscription.
        // Device creation sends no key, button or motion notifications.
        this._seatDevices = [];
        if (Meta.is_wayland_compositor()) {
            const seat = Clutter.get_default_backend().get_default_seat();
            this._seatDevices = [Clutter.InputDeviceType.KEYBOARD_DEVICE, Clutter.InputDeviceType.POINTER_DEVICE]
                .map(type => seat.create_virtual_device(type));
        }
        this._export = Gio.DBusExportedObject.wrapJSObject(XML, this);
        this._export.export(Gio.DBus.session, '/dev/copperbench/ValidationObserver');
        this._signal = global.display.connect('accelerator-activated', (_display, action) => {
            if (this._action && action === this._action) this._emergency = true;
        });
    }

    State() {
        const shieldActive = Main.screenShield?.active ?? false;
        const blocked = Main.overview.visible || shieldActive;
        const windows = global.get_window_actors().map(actor => {
            const window = actor.meta_window;
            const rect = window.get_frame_rect();
            return {window_id: String(window.get_stable_sequence()), pid: window.get_pid(),
                x11_window_id: typeof window.get_xwindow === 'function' ? String(window.get_xwindow()) : null,
                title: window.get_title(), client_type: window.get_client_type(),
                native_wayland: window.get_client_type() === Meta.WindowClientType.WAYLAND,
                focused: !blocked && window.has_focus(),
                visible: actor.visible && !window.minimized && window.located_on_workspace(global.workspace_manager.get_active_workspace()),
                rect: {left: rect.x, top: rect.y, width: rect.width, height: rect.height}};
        });
        return JSON.stringify({windows, pointer: global.get_pointer().slice(0, 2),
            taskSeatDeviceCount: this._seatDevices.length,
            stageKeyFocus: String(global.stage.get_key_focus()), modalCount: Main.modalCount ?? null,
            emergency: this._emergency, emergencyReserved: this._action !== 0,
            overview: Main.overview.visible, screenShield: shieldActive,
            screenShieldAvailable: Main.screenShield !== null});
    }

    Arm() {
        if (this._action) return false;
        this._action = global.display.grab_accelerator('F8', Meta.KeyBindingFlags.NONE);
        if (!this._action) return false;
        Main.wm.allowKeybinding(Meta.external_binding_name_for_action(this._action), Shell.ActionMode.ALL);
        this._emergency = false;
        return true;
    }

    Disarm() {
        if (this._action) global.display.ungrab_accelerator(this._action);
        this._action = 0;
    }

    Focus(identifier) {
        const matches = global.get_window_actors().map(actor => actor.meta_window)
            .filter(window => String(window.get_stable_sequence()) === identifier);
        if (matches.length !== 1 || Main.screenShield?.active) return false;
        Main.overview.hide();
        matches[0].activate(global.get_current_time());
        return matches[0].has_focus();
    }

    disable() {
        this.Disarm();
        for (const device of this._seatDevices) device.run_dispose();
        this._seatDevices = [];
        if (this._signal) global.display.disconnect(this._signal);
        this._export?.unexport();
        this._export = null;
    }
}
