"""Keep one installed Native client session and one bounded desktop engine alive.

Requests are planner-reviewed JSON files supplied by the cloud action. This worker
never accepts shell commands, file paths outside its task, or unbounded UI scripts.
"""
import hashlib, json, os, shutil, signal, sys, time, traceback
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/minecraft-control-bridge/src'))
import installed_acceptance as h
from minecraft_control_bridge.engine import Engine
from minecraft_control_bridge.errors import BridgeError

read=lambda path:json.loads(Path(path).read_text(encoding='utf-8-sig'))
out=Path(os.environ['P263_CLIENT_EVIDENCE']).resolve()
config=read(os.environ['P263_CLIENT_CONFIG'])
prior=read(config['installedRun']);cell=read(config['installationCell']);prepared=read(config['prepared'])
out.mkdir(parents=True,exist_ok=False)
requests=out/'requests';responses=out/'responses';requests.mkdir();responses.mkdir()
def save(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

product=Path(prior['productRoot']);workspace_file=Path(prior['workspace']);root=workspace_file.parent
desktop=os.environ['P263_DESKTOP'];started=time.monotonic()
record={'status':'preparing','candidateId':cell['candidateId'],'generatorId':prior['generatorId'],
        'artifactSha256':cell['artifactSha256'],'level':cell['level'],'desktop':desktop,
        'installationReceiptSha256':h.sha(config['installationCell']),'observedArtifacts':cell['observedArtifacts'],
        'workspace':str(workspace_file),'runs':[],'controller':'task-local bounded engine; not a registered MCP connection'}
engine=None;workspace=None;task_id=None;attempt=0;interrupted=False
def interrupt(signum, frame):
    global interrupted
    interrupted=True
    if engine is not None:engine.stop_event.set()
signal.signal(signal.SIGTERM,interrupt)
signal.signal(signal.SIGINT,interrupt)
try:
    assert prior['status']==cell['status']=='passed'
    assert prior['candidateId']==prepared['candidateId']==cell['candidateId']
    jars=[p for p in (root/'build/libs').glob('*.jar') if h.sha(p)==cell['artifactSha256']]
    assert len(jars)==1
    for identifier,path in prepared['assetFiles'].items():assert h.sha(path)==prepared['observedArtifacts'][identifier]
    record['artifactPath']=str(jars[0])
    source_files=[p for directory in ('src','elements','gradle') for p in (root/directory).rglob('*') if p.is_file()]
    record['sourceBefore']={p.relative_to(root).as_posix():h.sha(p) for p in source_files}
    home=out/'product-home';home.mkdir()
    env=h.run_environment(product,home,Path(prior['gradleHome']),out,'ubuntu')
    # The process still needs this exact desktop socket, while product data/cache remain isolated.
    for key in ('XDG_RUNTIME_DIR','DISPLAY','XAUTHORITY','WAYLAND_DISPLAY','DBUS_SESSION_BUS_ADDRESS','XDG_SESSION_TYPE'):
        if key in os.environ:env[key]=os.environ[key]
    env['SDL_VIDEO_DRIVER']='wayland' if desktop=='wayland' else 'x11'
    if desktop=='xorg':env['SDL_VIDEO_FORCE_EGL']='1'
    record['renderingRequest']={'sdlVideoDriver':env['SDL_VIDEO_DRIVER'],
                                'forceEgl':desktop=='xorg','softwareMesa':env.get('LIBGL_ALWAYS_SOFTWARE')=='1'}
    env['GDK_BACKEND']='wayland' if desktop=='wayland' else 'x11'
    for key in list(env):
        if key.startswith(('ACTIONS_','INPUT_')) or key in ('GITHUB_TOKEN','GH_TOKEN'):env.pop(key)
    previous=os.environ.copy();os.environ.clear();os.environ.update(env)
    sys.path.insert(0,str(product/'sdk/python'))
    from copperbench_native import Workspace
    try:
        workspace=Workspace.open(workspace_file,launcher=product/'copperbench.sh',cwd=product,
                                 startup_timeout=180,request_timeout=180)
    finally:
        os.environ.clear();os.environ.update(previous)
    record['nativePid']=workspace._process.pid
    save(out/'environment.json',workspace.query('get_workspace_environment'))
    save(out/'opt-in.json',workspace.preview('opt_in',generator_id=prior['generatorId']))
    # run_client uses the existing workspace permission policy; no grant bypass or EULA is needed.
    if desktop=='wayland':
        from gnome_backend import GnomeWaylandBackend
        backend=GnomeWaylandBackend()
    else:
        from gnome_backend import GnomeX11Backend
        backend=GnomeX11Backend()
    engine=Engine(backend)
    def launch():
        global attempt,task_id
        assert task_id is None and attempt<2
        attempt+=1
        accepted=workspace.preview('command',generator_id=prior['generatorId'],command='run_client',payload={})['core']
        assert accepted['status']=='accepted',accepted
        task_id=accepted['task']['id'];record['runs'].append({'attempt':attempt,'taskId':task_id})
        save(out/f'run-{attempt}-start.json',accepted)
    launch()
    record['status']='running'
    save(out/'session.json',record)
    save(out/'ready.json',{'doctor':engine.doctor(),'windows':engine.list_windows(),'session':record})
    sequence=1;last_poll=0
    while time.monotonic()-started<5400:
        if interrupted or (out/'abort.signal').exists():raise RuntimeError('Cloud controller stopped the session')
        engine.poll_stop()
        if (out/'finish.signal').exists():break
        if task_id and time.monotonic()-last_poll>2:
            last_poll=time.monotonic();task_result=workspace.get_task(task_id)
            save(out/'active-task.json',task_result)
            state=task_result['data']['task']['state']
            if state not in ('queued','running'):
                save(out/f'run-{attempt}-terminal.json',task_result)
                record['runs'][-1]['state']=state
                if (root/'run/logs/latest.log').is_file():shutil.copy2(root/'run/logs/latest.log',out/f'run-{attempt}-client.log')
                assert state=='succeeded',task_result
                assert h.sha(jars[0])==cell['artifactSha256']
                task_id=None
                record['status']='awaiting-reopen' if attempt==1 else 'awaiting-review'
                save(out/'session.json',record)
        request_path=requests/f'{sequence:04d}.json'
        if not request_path.exists():time.sleep(0.1);continue
        destination=responses/f'{sequence:04d}';destination.mkdir()
        request_bytes=request_path.read_bytes();request=json.loads(request_bytes)
        operation=request['operation'];arguments=request.get('arguments',{})
        answer={'requestSha256':hashlib.sha256(request_bytes).hexdigest(),'sequence':sequence,'operation':operation}
        try:
            if operation in ('doctor','list_windows'):result=getattr(engine,operation)()
            elif operation=='status':
                result={'session':record,'activeTask':read(out/'active-task.json') if (out/'active-task.json').exists() else None,
                        'compositorState':backend._state(),
                        'backendDiagnostics':backend.diagnostics() if hasattr(backend,'diagnostics') else None}
            elif operation=='attach':
                selected=backend.window(arguments['window_id']);pid=selected['pid']
                process_cwd=Path(f'/proc/{pid}/cwd').resolve()
                cmdline=Path(f'/proc/{pid}/cmdline').read_bytes().replace(b'\0',b' ').decode('utf-8','replace')
                assert process_cwd.is_relative_to(root) or str(root) in cmdline,'Window does not belong to the installed test workspace'
                save(out/f'run-{attempt}-client-process.json',{'pid':pid,'cwd':str(process_cwd),'commandLine':cmdline,'window':selected})
                result=engine.attach(arguments['window_id'],out/'bridge',root/'run',focus=arguments.get('focus',False))
                save(out/f'run-{attempt}-input-initialization.json',getattr(backend,'input_initialization',None))
            elif operation in ('observe','step','record_verification','stop','detach'):
                result=getattr(engine,operation)(**arguments)
            elif operation=='reopen':
                assert task_id is None and engine.session is None,'Close and detach the first client before reopening'
                launch();result={'status':'launched','attempt':attempt,'taskId':task_id}
            elif operation=='archive_world':
                assert task_id is None,'Archive only after normal client exit'
                name=arguments['world_name']
                assert name==config['worldName'],'Only the exact named test world is eligible'
                source=(root/'run/saves'/name).resolve()
                assert source.is_relative_to(root/'run/saves') and source.is_dir()
                target=out/('world-after-first-save' if attempt==1 else 'world-after-reopen')
                shutil.copytree(source,target)
                result={'status':'archived','world':name,'path':str(target),'attempt':attempt}
            elif operation=='finish':
                assert task_id is None and attempt==2 and engine.session is None
                result={'status':'lifecycle-completed'}
                (out/'finish.signal').write_text('planner reviewed finish\n',encoding='utf-8')
            else:raise ValueError('Unsupported operation')
            answer['result']=result
            observation=result.get('observation') if isinstance(result,dict) else None
            if not observation and isinstance(result,dict) and 'image' in result:observation=result
            if observation and observation.get('image'):
                source=Path(observation['image']['path']).resolve()
                assert source.is_relative_to(out/'bridge')
                shutil.copy2(source,destination/'frame.png')
                answer['frameSha256']=h.sha(source)
        except Exception as error:
            answer['error']={'type':type(error).__name__,'message':str(error),'code':getattr(error,'code',None)}
        save(destination/'response.json',answer)
        sequence+=1
    assert (out/'finish.signal').exists(),'Planner review budget exhausted'
    assert all(h.sha(root/path)==digest for path,digest in record['sourceBefore'].items()),'Client run modified mod inputs'
    record['status']='lifecycle-completed'
except BaseException as error:
    record.update(status='failed',error=str(error),traceback=traceback.format_exc())
finally:
    if engine:
        try:engine.close()
        except BaseException as error:record['controlCleanupError']=str(error)
    if workspace:
        if task_id:
            try:save(out/'cleanup-cancel.json',workspace.cancel_task(task_id))
            except BaseException as error:record['taskCleanupError']=str(error)
        try:
            workspace.close()
            record['nativeExitCode']=workspace._process.poll()
        except BaseException as error:record['nativeCleanupError']=str(error)
    record['elapsedSeconds']=round(time.monotonic()-started,3)
    save(out/'session.json',record)
sys.exit(0 if record['status']=='lifecycle-completed' else 1)
