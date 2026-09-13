from datetime import datetime, timezone
import hashlib, json, os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parent
home=ROOT/'home'
assert not home.exists()
env=dict(os.environ)
for key in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','JAVA_OPTS','JAVA_HOME','GRADLE_HOME','GRADLE_OPTS','MCREATOR_HOME'):
    env.pop(key,None)
env.update(COPPERBENCH_HOME=str(home/'.copperbench'),JAVA_TOOL_OPTIONS='-Duser.home='+str(home))
classes=Path(r'D:\AICoding\Minecraft_ModCreator\.tmp\stage15-linux\build\classes\java\main')
installed=Path(r'C:\Program Files\Copperbench')
command=[str(installed/'jdk/bin/javaw.exe'),'-Xmx768m','--add-opens=java.base/java.lang=ALL-UNNAMED','--enable-native-access=ALL-UNNAMED,jcef','-Dcopperbench.productShell=true','-cp',str(classes)+';'+str(installed/'lib/*'),'net.mcreator.Launcher']
started=datetime.now(timezone.utc)
startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=1
with (ROOT/'app.stdout').open('xb') as stdout,(ROOT/'app.stderr').open('xb') as stderr:
    process=subprocess.Popen(command,env=env,cwd=installed,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,startupinfo=startup)
    (ROOT/'launch.json').write_text(json.dumps({'at':started.isoformat(),'pid':process.pid,'command':command,'validationKind':'development-classpath-first-use-dialog-smoke','installedProductValidation':False,'classSha256':hashlib.file_digest((classes/'net/mcreator/ui/MCreatorApplication.class').open('rb'),'sha256').hexdigest()})+'\n')
    status=process.wait()
(ROOT/'process.json').write_text(json.dumps({'startedAt':started.isoformat(),'completedAt':datetime.now(timezone.utc).isoformat(),'exitCode':status})+'\n')
