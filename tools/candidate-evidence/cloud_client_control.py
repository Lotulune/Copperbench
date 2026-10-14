"""Submit one reviewed JSON request to the task-specific cloud desktop queue."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',required=True,type=int)
    parser.add_argument('--desktop',required=True,choices=('xorg','wayland'))
    parser.add_argument('--loader',required=True,choices=('fabric','neoforge'))
    parser.add_argument('--sequence',required=True,type=int)
    parser.add_argument('--request',required=True,type=Path)
    args=parser.parse_args()
    if args.run<1 or not 1<=args.sequence<=9999:raise ValueError('Invalid run or sequence')
    value=json.loads(args.request.read_text(encoding='utf-8-sig'))
    allowed={'doctor','list_windows','status','attach','observe','step','record_verification','stop','detach',
             'reopen','archive_world','finish','check_focus_loss'}
    if value.get('operation') not in allowed:raise ValueError('Unknown operation')
    key=f'{args.run}-{args.desktop}-{args.loader}'
    value.update(sessionKey=key,sequence=args.sequence)
    content=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if len(content)>65536:raise ValueError('Request exceeds 64 KiB')
    endpoint=f'repos/Lotulune/Copperbench/contents/sessions/{key}/{args.sequence:04d}.json'
    payload={'branch':'codex/p263-client-control',
             'message':f'test(p263): reviewed {key} action {args.sequence:04d}',
             'content':base64.b64encode(content).decode('ascii')}
    # No sha is sent: GitHub must reject replacement of an existing sequence.
    result=subprocess.run(['gh','api','--method','PUT',endpoint,'--input','-'],
                          input=json.dumps(payload),capture_output=True,text=True,encoding='utf-8')
    if result.returncode:raise RuntimeError(result.stderr)
    answer=json.loads(result.stdout)
    print(json.dumps({'sessionKey':key,'sequence':args.sequence,'requestSha256':hashlib.sha256(content).hexdigest(),
                      'commit':answer['commit']['sha'],'url':answer['content']['html_url']}))


if __name__=='__main__':main()
