#!/usr/bin/env bash
set -euo pipefail
trial_root=/home/stage15/stage16-3c0aa090
package_file=/home/stage15-operator/stage16-pr-candidate.deb
expected_package=65dafdee5f8e06b922ae336eb687aa955c3b43029f2f3feaca0deeeb18eca520
test ! -e "$trial_root/pr-rehearsal-baseline.json"
sudo -n -u stage15 python3 - "$trial_root/pr-rehearsal-baseline.json" <<'PY'
import hashlib,json,pathlib,platform,shutil,subprocess,sys
with open('/opt/copperbench/lib/copperbench.jar','rb') as f:
    previous=hashlib.file_digest(f,'sha256').hexdigest()
result={'schemaVersion':'1.0','kind':'stage16-linux-pr-rehearsal-baseline',
        'osRelease':pathlib.Path('/etc/os-release').read_text(),'arch':platform.machine(),
        'systemTools':{name:shutil.which(name) for name in ('java','javac','gradle','git')},
        'session':subprocess.check_output(['loginctl','show-session','1','-p','Type','-p','Active','-p','State'],text=True),
        'previousApplicationJarSha256':previous,'desktopInputExercised':False,
        'cacheState':'Existing Stage15 certification guest caches; not cold-cache certification',
        'purpose':'Development rehearsal of the public repair collector; not the final release candidate'}
assert all(value is None for value in result['systemTools'].values())
pathlib.Path(sys.argv[1]).write_text(json.dumps(result,indent=2)+'\n')
PY
printf '%s  %s\n' "$expected_package" "$package_file" | sha256sum -c -
sudo -n apt-get install -y --reinstall "$package_file" > /home/stage15-operator/stage16-pr-install.log 2>&1
sudo -n -u stage15 python3 "$trial_root/verify-installed-repair.py" \
  --product /usr/bin/copperbench \
  --application-jar /opt/copperbench/lib/copperbench.jar \
  --source-workspace "$trial_root/resonance_token/resonance_token.mcreator" \
  --trial-root "$trial_root/pr-repair-workspace" \
  --evidence "$trial_root/pr-repair-evidence" \
  --candidate-sha256 "$expected_package" \
  --source-commit 94346b61d98ade884113f17dc3c1b6fc6edb79e4
