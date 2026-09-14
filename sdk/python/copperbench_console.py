"""Persistent interpreter worker owned by the desktop Python workbench."""
from __future__ import annotations

import codeop
import contextlib
import json
import linecache
import queue
import rlcompleter
import sys
import threading
import traceback

import copperbench as cb
import copperbench_scripting as scripting


def main():
    if sys.version_info < (3, 11):
        raise RuntimeError('Python 3.11 or newer is required')
    protocol = sys.stdout
    lock = threading.Lock()
    execution_id = None

    def emit(event, **data):
        with lock:
            protocol.write(json.dumps({'event': event, 'executionId': execution_id, **data}, ensure_ascii=True) + '\n')
            protocol.flush()

    class Output:
        def __init__(self, channel):
            self.channel = channel

        def write(self, value):
            for offset in range(0, len(value), 4096):
                emit('output', channel=self.channel, text=value[offset:offset + 4096])
            return len(value)

        def flush(self):
            pass

        def isatty(self):
            return False

    incoming = queue.Queue()

    def read_commands():
        try:
            for line in sys.stdin:
                incoming.put(json.loads(line))
        finally:
            incoming.put(None)

    def no_input(*args, **kwargs):
        raise RuntimeError('input() is unavailable in the workbench; pass values through console variables')

    with cb.Workspace.connect(sys.argv[1]) as workspace:
        cb.use_workspace(workspace)
        namespace = {'__name__': '__main__', 'cb': cb, 'workspace': workspace, 'input': no_input}
        compiler = codeop.CommandCompiler()
        pending = ''
        threading.Thread(target=read_commands, daemon=True).start()
        emit('ready', pythonVersion=sys.version)
        with contextlib.redirect_stdout(Output('stdout')), contextlib.redirect_stderr(Output('stderr')):
            while True:
                try:
                    request = incoming.get(timeout=0.2)
                except queue.Empty:
                    try:
                        scripting.app._tick()
                    except BaseException:
                        traceback.print_exc()
                    continue
                if request is None:
                    return
                execution_id = request['id']
                try:
                    operation = request.get('operation', 'execute')
                    if operation == 'complete':
                        completer = rlcompleter.Completer(namespace)
                        matches = []
                        for index in range(100):
                            candidate = completer.complete(request['text'], index)
                            if candidate is None:
                                break
                            matches.append(candidate)
                        emit('completed', completions=matches)
                        continue
                    source = request['source']
                    filename = request.get('filename', '<console>')
                    console = request.get('mode') == 'console'
                    if console:
                        source = pending + source
                    linecache.cache[filename] = (len(source), None, source.splitlines(True), filename)
                    compiled = compiler(source, filename, 'single' if console else 'exec')
                    if compiled is None:
                        pending = source + '\n'
                        emit('incomplete')
                        continue
                    if console:
                        pending = ''
                    else:
                        scripting.data.texts[filename] = scripting.Text(filename, source)
                    namespace['__file__'] = filename
                    exec(compiled, namespace)
                    emit('completed', operators=[{'idname': key, 'label': value.label or key}
                                                  for key, value in scripting._registered.items()])
                except BaseException as error:
                    pending = ''
                    traceback.print_exc()
                    frames = traceback.extract_tb(error.__traceback__)
                    location = next((frame for frame in reversed(frames)
                                     if frame.filename == request.get('filename', '<console>')), None)
                    emit('failed', line=getattr(error, 'lineno', None) or (location.lineno if location else None),
                         message=f'{type(error).__name__}: {error}',
                         operators=[{'idname': key, 'label': value.label or key}
                                    for key, value in scripting._registered.items()])
                finally:
                    execution_id = None
                    try:
                        scripting.app._tick()
                    except BaseException:
                        traceback.print_exc()


if __name__ == '__main__':
    try:
        main()
    except BaseException:
        traceback.print_exc()
        sys.exit(1)
