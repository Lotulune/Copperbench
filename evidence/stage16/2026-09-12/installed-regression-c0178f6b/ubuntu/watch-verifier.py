import os,time
from pathlib import Path
deadline=time.monotonic()+900
while Path('/proc/5672').exists() and time.monotonic()<deadline: time.sleep(1)
