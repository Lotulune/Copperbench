import json,pathlib,re,zipfile,hashlib
root=pathlib.Path(r'D:\AICoding\Minecraft_ModCreator')
work=root/'.tmp/stage15-linux'; build=work/'build'; run=build/'stage16-agent-reliability/cold-cache-c0178f6b-20260912'
inv=json.loads((run/'storage-before.json').read_text(encoding='utf-8'))
sizes={r['path'].lower():r for r in inv['directories']}; targets=[]
def add(p,reason):
    p=p.resolve(strict=True)
    assert p.is_relative_to(root) and p != root
    assert not p.is_relative_to(run)
    assert not any(x in p.parts for x in ('.git','evidence','src','docs'))
    st=p.stat(); size=sizes[str(p).lower()]['bytes'] if p.is_dir() else st.st_size
    targets.append({'path':str(p),'kind':'directory' if p.is_dir() else 'file','bytes':size,'reason':reason})
for r in inv['directories']:
    p=pathlib.Path(r['path'])
    if p.name=='caches' and 'stage8-workspace-generator-gradle' in p.parts:
        add(p,'Regenerable dependency cache from completed Stage8/14 verification')
add(root/'build/p0-export-replay-isolated/home/.copperbench/gradle/caches','Regenerable previous isolated build dependency cache')
for p in [root/'build/export',build/'export']:
    add(p,'Regenerable packaging output; frozen candidate installers and backup retained separately')
for rel in ['stage15-linux-hyperv/run42/deb-chunks','stage15-linux-hyperv/run42/portable-chunks','stage15-linux-hyperv/run37/deb-chunks','stage15-linux-hyperv/run37/portable-chunks','stage15-linux-hyperv/run33/transfer']:
    add(build/rel,'Completed download transfer fragments; final payload retained')
for rel,pattern in [('stage15-linux-hyperv/run31',r'deb\.part\d+(?:\.rest)?'),('stage15-linux-hyperv/run34',r'chunk-\d+\.bin'),('stage16-agent-reliability/linux-33ceb6e9-explicit',r'part-\d+'),('stage16-agent-reliability/linux-b62d6709-explicit',r'part-\d+')]:
    for p in (build/rel).iterdir():
        if p.is_file() and re.fullmatch(pattern,p.name): add(p,'Completed download fragment; full candidate payload retained')
pairs=[('stage15-linux-hyperv/run31/stage15-linux-candidate.zip','stage15-linux-hyperv/run31/candidate'),('stage16-agent-reliability/linux-33ceb6e9-explicit/artifact.zip','stage16-agent-reliability/linux-33ceb6e9-explicit/payload'),('stage16-agent-reliability/linux-b62d6709-explicit/artifact.zip','stage16-agent-reliability/linux-b62d6709-explicit/payload'),('stage16-agent-reliability/linux-3c0aa090.artifact.zip','stage16-agent-reliability/linux-3c0aa090')]
verified=[]
for archive,folder in pairs:
    with zipfile.ZipFile(build/archive) as z:
        members=[n for n in z.namelist() if n.endswith(('.tar.gz','.deb'))]
        assert len(members)==2,(archive,members)
        for member in members:
            local=build/folder/pathlib.PurePosixPath(member).name
            assert local.is_file(),local
            digest=hashlib.file_digest(z.open(member),'sha256').hexdigest()
            assert hashlib.file_digest(local.open('rb'),'sha256').hexdigest()==digest,local
            verified.append({'archive':archive,'member':member,'retained':str(local),'sha256':digest})
    add(build/archive,'Redundant archive container; both embedded final packages hash-match retained payloads')
assert len({r['path'] for r in targets})==len(targets)
result={'schemaVersion':'1.0','authorizedBy':'User requested local build/cache/VM storage cleanup before full cold-cache validation','workspaceBoundary':str(root),'targets':targets,'duplicatePayloadVerification':verified,'logicalBytes':sum(x['bytes'] for x in targets),'protected':['All virtual machine disks and checkpoints','All source worktrees and Git state','All evidence and reports','All final candidate packages except verified duplicate containers','Host installation rollback backup and user state backup']}
(run/'cleanup-plan.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'targetCount':len(targets),'logicalGiB':round(result['logicalBytes']/1024**3,2),'duplicatePackagesVerified':len(verified),'targetsByReason':{reason:round(sum(t['bytes'] for t in targets if t['reason']==reason)/1024**3,2) for reason in sorted({t['reason'] for t in targets})}},indent=2))
