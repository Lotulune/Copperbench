const fs = require('node:fs/promises');
const path = require('node:path');
const {spawn} = require('node:child_process');

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let artifact;
const evidence = process.env.P263_CLIENT_EVIDENCE;
const desktopEvidence = process.env.P263_DESKTOP_EVIDENCE;
const key = `${process.env.GITHUB_RUN_ID}-${process.env.P263_DESKTOP}-${process.env.P263_LOADER}`;
const allowed = new Set(['doctor','list_windows','attach','observe','step','record_verification',
                         'stop','detach','reopen','archive_world','finish']);
let child, stopped = false, exitCode = null;
async function exists(file) {try {await fs.access(file); return true;} catch {return false;}}
async function abort() {
  stopped = true;
  if (await exists(evidence)) await fs.writeFile(path.join(evidence,'abort.signal'),'Controller stopped\n');
}
process.on('SIGINT', abort);
process.on('SIGTERM', abort);
async function filesAt(directory) {
  const rows = [];
  for (const entry of await fs.readdir(directory,{withFileTypes:true})) {
    const file = path.join(directory,entry.name);
    if (entry.isDirectory()) rows.push(...await filesAt(file));
    else if (entry.isFile()) rows.push(file);
  }
  return rows;
}
async function upload(name, directory) {
  const files = await filesAt(directory);
  if (files.length) await artifact.uploadArtifact(name,files,directory,{retentionDays:14});
}
async function main() {
  const {DefaultArtifactClient} = await import('@actions/artifact');
  artifact = new DefaultArtifactClient();
  if (process.env.P263_CONTROL_PREFLIGHT==='true') {
    const directory=path.join(process.env.RUNNER_TEMP,'p263-control-preflight');
    await fs.mkdir(directory);
    await fs.writeFile(path.join(directory,'receipt.json'),JSON.stringify({source:process.env.GITHUB_SHA,
      scope:'Transport upload only; no desktop or game input',status:'preflight'}));
    await upload('p263-controller-preflight',directory);
    return;
  }
  console.log(`Planner control key: ${key}`);
  const env = {...process.env};
  for (const name of Object.keys(env))
    if (name.startsWith('ACTIONS_') || name.startsWith('INPUT_') || ['GH_TOKEN','GITHUB_TOKEN'].includes(name)) delete env[name];
  child = spawn('bash',['tools/candidate-evidence/qualify_cloud_desktop.sh',process.env.P263_DESKTOP,desktopEvidence],
                {env,stdio:'inherit'});
  child.on('exit', code => {exitCode = code ?? 1;});
  child.on('error', error => {console.error(error.message); exitCode = 1;});
  const deadline = Date.now()+90*60*1000;
  let sequence=1, ready=false;
  while (exitCode === null && Date.now()<deadline && !stopped) {
    if (!ready && await exists(path.join(evidence,'ready.json'))) {
      await artifact.uploadArtifact('p263-client-ready',[path.join(evidence,'ready.json')],evidence,{retentionDays:14});
      ready = true;
    }
    if (!ready) {await pause(2000);continue;}
    const number=String(sequence).padStart(4,'0');
    const responseDir=path.join(evidence,'responses',number);
    if (await exists(path.join(responseDir,'response.json'))) {
      await upload(`p263-client-${number}`,responseDir);sequence++;continue;
    }
    const requestFile=path.join(evidence,'requests',`${number}.json`);
    if (!(await exists(requestFile))) {
      const url=`https://api.github.com/repos/${process.env.GITHUB_REPOSITORY}/contents/sessions/${key}/${number}.json?ref=codex/p263-client-control`;
      const response=await fetch(url,{headers:{Authorization:`Bearer ${process.env.INPUT_TOKEN}`,
                     Accept:'application/vnd.github.raw+json','X-GitHub-Api-Version':'2022-11-28'},
                     signal:AbortSignal.timeout(15000)});
      if (response.status !== 404) {
        if (!response.ok) throw new Error(`Request retrieval failed: HTTP ${response.status}`);
        const body=await response.text();
        if (Buffer.byteLength(body)>65536) throw new Error('Control request exceeds 64 KiB');
        const request=JSON.parse(body);
        if (request.sessionKey!==key || request.sequence!==sequence || !allowed.has(request.operation))
          throw new Error('Request is outside this session or operation scope');
        await fs.writeFile(requestFile+'.tmp',body);
        await fs.rename(requestFile+'.tmp',requestFile);
      }
    }
    await pause(4000);
  }
  if (exitCode===null) {
    await abort();
    for (let i=0;i<60 && exitCode===null;i++) await pause(1000);
    if (exitCode===null) throw new Error('Worker did not finish cleanup within 60 seconds');
  }
  // The finish response may be written immediately before normal worker exit.
  const last=path.join(evidence,'responses',String(sequence).padStart(4,'0'));
  if (await exists(path.join(last,'response.json'))) await upload(`p263-client-${String(sequence).padStart(4,'0')}`,last);
  if (exitCode!==0) throw new Error(`Client worker exited with code ${exitCode}`);
}
main().catch(async error => {console.error(error.message);process.exitCode=1;await abort();})
  .finally(async () => {
    if (child && exitCode===null) for (let i=0;i<60 && exitCode===null;i++) await pause(1000);
    for (const [name,dir] of [['p263-client-evidence',evidence],['p263-client-desktop',desktopEvidence]])
      if (await exists(dir)) await upload(name,dir);
  }).catch(error=>{console.error(error.message);process.exitCode=1;});
