#!/usr/bin/env bash
set -euo pipefail
transfer=/home/stage15-operator/stage16-lowmem-33ceb6e9
trial=/home/stage15/stage16-lowmem-33ceb6e9
package="$transfer/copperbench_0.1.0_amd64.deb"
expected=6ae06ca2033d3f40bb4c1401901de36fcaf3cad218ff9243d064968ea48668c0
test ! -e "$trial"
printf '%s  %s\n' "$expected" "$package" | sha256sum -c -
sudo -n install -d -m 755 -o stage15 -g stage15 "$trial"
sudo -n install -m 644 -o stage15 -g stage15 "$transfer/resonance-token-source.zip" "$trial/source.zip"
sudo -n install -m 644 -o stage15 -g stage15 "$transfer/verify-installed-repair.py" "$trial/verify-installed-repair.py"
sudo -n -u stage15 python3 - "$trial" <<'PY'
import datetime,hashlib,json,pathlib,platform,shutil,subprocess,sys,zipfile
root=pathlib.Path(sys.argv[1])
system_tools={name:shutil.which(name) for name in ('java','javac','gradle','git')}
assert all(path is None for path in system_tools.values()),'Clean tooling baseline changed'
assert hashlib.sha256((root/'source.zip').read_bytes()).hexdigest()=='8ddeb1cdf34bcba6a738ab3b8cbac7750160199356710049e9af700185523051'
with zipfile.ZipFile(root/'source.zip') as archive:
    assert all((root/'source'/name).resolve().is_relative_to((root/'source').resolve()) for name in archive.namelist())
    archive.extractall(root/'source')
baseline={'schemaVersion':'1.0','recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'osRelease':pathlib.Path('/etc/os-release').read_text(),'architecture':platform.machine(),
          'systemTools':system_tools,'memory':subprocess.check_output(['free','-b'],text=True),
          'sessions':subprocess.check_output(['loginctl','list-sessions','--no-legend'],text=True),
          'existingApplicationJarSha256':hashlib.sha256(pathlib.Path('/opt/copperbench/lib/copperbench.jar').read_bytes()).hexdigest(),
          'cacheState':'Existing certification guest caches; no caches cleared',
          'memoryProfile':'Hyper-V dynamic startup 3 GiB, minimum 2 GiB, maximum 4 GiB'}
(root/'baseline.json').write_text(json.dumps(baseline,indent=2)+'\n')
PY
sudo -n apt-get install -y --reinstall "$package" > "$transfer/install.log" 2>&1
sudo -n -u stage15 python3 - "$trial" "$expected" <<'PY'
import datetime,hashlib,json,pathlib,subprocess,sys
root=pathlib.Path(sys.argv[1])
verification=subprocess.run(['dpkg','-V','copperbench'],text=True,capture_output=True,check=True)
assert not verification.stdout.strip(),'Installed package file verification failed'
result={'schemaVersion':'1.0','recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'sourceCommit':'33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5','candidateSha256':sys.argv[2],
        'installedVersion':subprocess.check_output(['dpkg-query','-W','-f=${Version}','copperbench'],text=True),
        'installedApplicationJarSha256':hashlib.sha256(pathlib.Path('/opt/copperbench/lib/copperbench.jar').read_bytes()).hexdigest(),
        'dpkgVerifyExitCode':verification.returncode,'dpkgVerifyOutput':verification.stdout,'status':'passed'}
(root/'installation.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result),flush=True)
PY
sudo -n -u stage15 python3 "$trial/verify-installed-repair.py" \
  --product /usr/bin/copperbench \
  --application-jar /opt/copperbench/lib/copperbench.jar \
  --source-workspace "$trial/source/resonance_token.mcreator" \
  --trial-root "$trial/repair-workspace" \
  --evidence "$trial/repair-evidence" \
  --candidate-sha256 "$expected" \
  --source-commit 33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5
