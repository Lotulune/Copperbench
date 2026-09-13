"""Process-local cache isolation; no persistent environment changes."""
import os
from pathlib import Path
ROOT = Path(__file__).resolve().parent
def configure():
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS',
                'JAVA_HOME', 'JRE_HOME', 'JDK_HOME', 'CLASSPATH', 'GRADLE_HOME',
                'GRADLE_OPTS', 'COPPERBENCH_STAGE5_GRADLE_EXECUTABLE', 'MCREATOR_HOME'):
        os.environ.pop(key, None)
    home = ROOT / 'home'
    data = home / '.copperbench'
    os.environ.update(COPPERBENCH_HOME=str(data),
                      COPPERBENCH_GRADLE_USER_HOME=str(data / 'gradle'),
                      GRADLE_USER_HOME=str(data / 'gradle'),
                      JAVA_TOOL_OPTIONS='-Duser.home=' + str(home))
