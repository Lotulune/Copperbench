"""Application scripting objects. The same module is usable from external Python."""
from __future__ import annotations

import functools
import inspect
import math
import re
import sys
import time
import traceback
from types import SimpleNamespace
from typing import Any

from copperbench_native import Workspace, ModElement

_workspace: Workspace | None = None
_registered: dict[str, type] = {}


def use_workspace(workspace: Workspace) -> Workspace:
    """Bind context/data/ops to an existing session. Does not transfer ownership."""
    global _workspace
    _workspace = workspace
    return workspace


def _current() -> Workspace:
    if _workspace is None:
        raise RuntimeError('No active workspace; use cb.use_workspace(Workspace.connect(path))')
    return _workspace


class Context:
    @property
    def workspace(self) -> Workspace:
        return _current()

    @property
    def active_element(self) -> ModElement | None:
        workspace = _current()
        identifier = workspace.get_context().get('activeElementId')
        if identifier is None:
            return None
        try:
            return workspace.elements[identifier]
        except KeyError:
            return None

    @active_element.setter
    def active_element(self, element: ModElement | None) -> None:
        if element is not None and element._workspace.workspace_id != _current().workspace_id:
            raise ValueError('The element belongs to another workspace')
        _current().select_element(None if element is None else element.id)

    @property
    def selected_elements(self) -> tuple[ModElement, ...]:
        element = self.active_element
        return () if element is None else (element,)

    @property
    def view(self) -> str:
        return _current().get_context()['view']


class Text:
    def __init__(self, name: str, body: str = ''):
        self.name, self.body = name, body

    def write(self, text: str) -> None:
        self.body += text

    def clear(self) -> None:
        self.body = ''

    def as_string(self) -> str:
        return self.body


class Texts(dict[str, Text]):
    def new(self, name: str, body: str = '') -> Text:
        if name in self:
            raise ValueError(f'Text already exists: {name}')
        text = Text(name, body)
        self[name] = text
        return text


class Data:
    def __init__(self):
        self.texts = Texts()

    @property
    def elements(self):
        return _current().elements

    @property
    def assets(self) -> dict[str, Any]:
        """Fresh Core asset projection; mutations use cb.ops."""
        return _current().query('list_assets')['data']

    @property
    def registries(self) -> dict[str, Any]:
        return _current().query('list_workspace_registries')['data']


class Operator:
    """Subclass, set idname='category.action', implement execute(context, **kwargs)."""
    idname = ''
    label = ''

    @classmethod
    def poll(cls, context: Context) -> bool:
        return True

    def execute(self, context: Context, **kwargs):
        raise NotImplementedError


_aliases = {
    'workspace.build': 'build_workspace', 'workspace.validate': 'validate_workspace',
    'workspace.generate': 'generate_workspace', 'workspace.run_client': 'run_client',
    'workspace.run_server': 'run_server', 'workspace.run_game_tests': 'run_gametest',
    'elements.create': 'create_mod_element', 'elements.update': 'update_mod_element',
    'elements.delete': 'delete_mod_element', 'procedures.update': 'update_procedure',
    'history.list': 'get_history', 'history.create': 'create_recovery_point',
    'tasks.get': 'get_task', 'tasks.cancel': 'cancel_task',
}


class Operations:
    def __init__(self, prefix: str = ''):
        self._prefix = prefix

    def __dir__(self):
        names = set(_registered) | set(_aliases) | set(_current().operations)
        return sorted({name[len(self._prefix):].split('.')[0] for name in names if name.startswith(self._prefix)})

    def __getattr__(self, name):
        qualified = self._prefix + name
        if qualified in _registered:
            @functools.wraps(_registered[qualified].execute)
            def run(**kwargs):
                operator = _registered[qualified]
                if not operator.poll(context):
                    raise RuntimeError(f'Operator is unavailable in the current context: {qualified}')
                return operator().execute(context, **kwargs)
            return run
        target = _aliases.get(qualified, qualified)
        if target in _current().operations:
            def invoke(**payload):
                if target.startswith(('get_', 'list_', 'preview_', 'plan_')):
                    return _current().query(target, **payload)
                if target in {'build_workspace', 'validate_workspace', 'generate_workspace', 'run_client', 'run_server'}:
                    payload.setdefault('scope', 'workspace')
                return _current().command(target, **payload)
            invoke.__name__ = qualified
            invoke.__doc__ = f'Core operation {target}; keyword arguments follow the UI-Core contract.'
            return invoke
        if any(key.startswith(qualified + '.') for key in (*_aliases, *_registered)):
            return Operations(qualified + '.')
        raise AttributeError(qualified)


class Utils:
    @staticmethod
    def register_class(operator: type[Operator]) -> None:
        if not isinstance(operator, type) or not issubclass(operator, Operator):
            raise TypeError('Expected an Operator subclass')
        if not isinstance(operator.idname, str) or not isinstance(operator.label, str):
            raise TypeError('Operator idname and label must be strings')
        if not re.fullmatch(r'[a-z_][a-z0-9_]*\.[a-z_][a-z0-9_]*', operator.idname):
            raise ValueError('Operator idname must be category.action')
        if operator.idname in _registered or operator.idname in _aliases:
            raise ValueError(f'Operator already exists: {operator.idname}')
        _registered[operator.idname] = operator

    @staticmethod
    def unregister_class(operator: type[Operator]) -> None:
        if _registered.get(operator.idname) is not operator:
            raise ValueError('Operator is not registered')
        del _registered[operator.idname]


class Timers:
    def __init__(self):
        self._pending = {}

    def register(self, callback, *, first_interval: float = 0) -> None:
        if not callable(callback) or not math.isfinite(first_interval) or first_interval < 0:
            raise ValueError('Expected a callable and a finite nonnegative interval')
        if callback in self._pending:
            raise ValueError('Timer already registered')
        self._pending[callback] = time.monotonic() + first_interval

    def unregister(self, callback) -> None:
        del self._pending[callback]

    def is_registered(self, callback) -> bool:
        return callback in self._pending

    def _tick(self) -> None:
        for callback, deadline in list(self._pending.items()):
            if deadline > time.monotonic() or callback not in self._pending:
                continue
            del self._pending[callback]
            try:
                interval = callback()
                if interval is not None:
                    self.register(callback, first_interval=max(0.01, interval))
            except BaseException:
                traceback.print_exc()


class App:
    python_version = sys.version
    native_api_version = '1'

    def __init__(self):
        self.timers = Timers()
        self.handlers = SimpleNamespace(workspace_update=[], selection_update=[])
        self._observed = None

    def _tick(self):
        self.timers._tick()
        if not self.handlers.workspace_update and not self.handlers.selection_update:
            return
        workspace = _current()
        selection = workspace.get_context()
        observed = (workspace.revision, selection['selectionVersion'])
        previous, self._observed = self._observed, observed
        if previous is None:
            return
        for index, callbacks in enumerate((self.handlers.workspace_update, self.handlers.selection_update)):
            if previous[index] != observed[index]:
                for callback in list(callbacks):
                    try:
                        callback(context)
                    except BaseException:
                        traceback.print_exc()

    def process_events(self):
        """Pump timers/handlers from an external script; the desktop worker does this while idle."""
        self._tick()


context = Context()
data = Data()
ops = Operations()
utils = Utils()
types = SimpleNamespace(Operator=Operator, ModElement=ModElement, Workspace=Workspace, Text=Text)
app = App()


def api_help(obj=None) -> str:
    """Return concise help without launching an interactive pager."""
    if obj is None:
        return ('cb.context: workspace, active_element, selected_elements, view\n'
                'cb.data: elements, assets, registries, texts\n'
                'cb.ops: Core operations and registered operators (use dir(cb.ops))\n'
                'cb.utils: register_class, unregister_class\n'
                'cb.app: timers, handlers\n'
                'Field writes commit immediately; exceptions do not roll back prior calls.')
    return inspect.getdoc(obj) or repr(obj)
