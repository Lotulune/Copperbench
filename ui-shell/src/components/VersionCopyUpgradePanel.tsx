import React, { useEffect, useRef, useState } from 'react';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiText } from '../i18n';
import type { VersionCopyUpgradeEntry, VersionCopyUpgradePlan, VersionCopyUpgradeResult } from '../types/contract';
import './versionCopyUpgrade.css';

function meaning(entry: VersionCopyUpgradeEntry): string {
  switch (entry.reasonCode) {
    case 'EXACT_TARGET_SELECTED': return uiText('仅在新副本中切换到所选生成器。', 'Switch to the selected generator only in a new copy.');
    case 'IDENTITY_PRESERVED': return uiText('内部名称、注册名、元素 UUID 和源码归属保持不变。', 'Keep internal name, registry name, element UUID and source ownership.');
    case 'METADATA_PRESERVED': return uiText('此属性可按原值保留。', 'Preserve this metadata value unchanged.');
    case 'UNKNOWN_METADATA_PRESERVED': return uiText('原样保留未知属性；需核对插件行为。', 'Preserve unknown metadata; review plugin behavior.');
    case 'FILE_SEMANTICS_UNPROVEN': return uiText('需检查目标 API 和资源兼容性，保留手写归属。', 'Review target API and resource compatibility; preserve manual ownership.');
    case 'TYPE_RULE_UNAVAILABLE': return uiText('此类型尚无转换规则，需保留定义并人工处理。', 'No conversion rule exists for this type. Preserve its definition for manual resolution.');
    case 'DOCUMENT_PRESERVED': return uiText('保留名称、元素 UUID 与未知属性；副本使用独立工作区身份。', 'Preserve names, element UUIDs and unknown fields; give the copy its own workspace identity.');
    case 'FUNCTION_COMMANDS_PROVEN': return uiText('命令属于已验证范围，保留命令原文与未知属性。', 'The commands are within the proven scope. Preserve their text and unknown fields.');
    case 'GENERATED_BASELINE_PROVEN': return uiText('与源生成器模板字节一致，可在副本中按目标模板重建。', 'Matches the source template bytes; regenerate from the target template in the copy.');
    case 'EMPTY_LANGUAGE_PROVEN': return uiText('空语言文件没有需要转换的词条。', 'This empty language file has no entries to convert.');
    case 'RUNTIME_EXCLUDED': return uiText('保留在源目录；运行目录、世界与历史记录不复制。', 'Keep in the source. Do not copy runtime directories, worlds or history.');
    case 'SOURCE_LAUNCH_PROFILE_EXCLUDED': return uiText('源工程的 IDE 启动配置留在源目录；新工程使用独立运行目录。', 'Keep the source IDE launch profile in the source; the new workspace uses its own run directory.');
    case 'FILE_RULE_UNPROVEN': return uiText('文件被外部修改或不属于已验证模板，需人工处理。', 'The file was changed or is outside the proven template scope. Manual resolution is required.');
    case 'FUNCTION_RULE_UNPROVEN': return uiText('函数名称、命名空间或命令超出转换范围。', 'The function name, namespace or commands are outside the conversion scope.');
    default: return uiText('尚无已验证的字段转换规则，需人工核对目标语义。', 'No proven field conversion rule exists. Review its target meaning manually.');
  }
}

export const VersionCopyUpgradePanel: React.FC = () => {
  const { state, previewVersionCopyUpgrade, versionCopyUpgradeV2, previewTargets } = useWorkbench();
  const workspace = state.workbench?.workspace;
  const source = workspace?.generator.id ?? '';
  const target = /^(fabric|neoforge)-26\.2$/.test(source) ? source.replace(/26\.2$/, '26.3') : '';
  const [plan, setPlan] = useState<VersionCopyUpgradePlan | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [outputName, setOutputName] = useState('upgrade-26-3');
  const [confirmed, setConfirmed] = useState(false);
  const [execution, setExecution] = useState<VersionCopyUpgradeResult['execution']>();
  const [filter, setFilter] = useState<'all' | VersionCopyUpgradeEntry['disposition']>('all');
  const sequence = useRef(0);
  useEffect(() => {
    sequence.current++; setPlan(null); setError(''); setBusy(false); setConfirmed(false); setExecution(undefined);
    return () => { sequence.current++; };
  }, [workspace?.id, workspace?.revision, source, outputName]);
  const preview = async (executable = false) => {
    const current = ++sequence.current; setBusy(true); setPlan(null); setError(''); setConfirmed(false); setExecution(undefined);
    try {
      const result = executable ? await versionCopyUpgradeV2({ operation: 'preview', targetGeneratorId: target, outputName })
        : await previewVersionCopyUpgrade(target);
      if (current !== sequence.current) return;
      if (result.status !== 'succeeded' || !result.plan) {
        setError(result.diagnostics.map(d => d.message).join(' '));
      } else setPlan(result.plan);
    } catch (e) {
      if (current === sequence.current) setError(e instanceof Error ? e.message : String(e));
    } finally { if (current === sequence.current) setBusy(false); }
  };
  const execute = async () => {
    if (!plan?.outputName || !plan.targetDirectory || !confirmed) return;
    const current = ++sequence.current; setBusy(true); setError(''); setExecution(undefined);
    try {
      const choice = await previewTargets({ operation: 'opt_in', generatorId: plan.targetGeneratorId, workspaceFolderPath: plan.targetDirectory });
      if (current !== sequence.current) return;
      if (choice.status !== 'succeeded') throw new Error(choice.diagnostics.map(d => d.code).join(' '));
      const result = await versionCopyUpgradeV2({ operation: 'execute', targetGeneratorId: plan.targetGeneratorId,
        outputName: plan.outputName, planId: plan.planId, expectedRevision: plan.sourceRevision, userApproved: true });
      if (current !== sequence.current) return;
      setExecution(result.execution);
      if (result.status !== 'succeeded') setError(result.diagnostics.map(d => d.message).join(' '));
    } catch (e) { if (current === sequence.current) setError(e instanceof Error ? e.message : String(e)); }
    finally { if (current === sequence.current) { setBusy(false); setPlan(null); setConfirmed(false); } }
  };
  const label = (value: VersionCopyUpgradeEntry['disposition']) => value === 'convertible' ? uiText('可转换', 'Convertible')
    : value === 'manual' ? uiText('需人工处理', 'Manual review') : uiText('暂不支持', 'Unsupported');
  const entries = plan?.entries.filter(entry => filter === 'all' || entry.disposition === filter) ?? [];
  return <section className="version-copy-upgrade" data-testid="version-copy-upgrade">
    <header><h2>{uiText('预览 26.3 复制升级', 'Preview a copy upgrade to 26.3')}</h2>
      <p>{uiText('查看字段和文件的转换范围，再处理需要人工核对的内容。', 'Review field and file conversions, then resolve the items that need manual review.')}</p></header>
    <div className="version-upgrade-controls">
      <label>{uiText('源生成器', 'Source generator')}<input value={source} readOnly /></label>
      <label>{uiText('目标生成器', 'Target generator')}<input value={target || uiText('需要 26.2 源工程', 'A 26.2 source workspace is required')} readOnly /></label>
      <button className="btn-primary" type="button" data-testid="preview-version-upgrade" onClick={() => preview()} disabled={!target || busy}>
        {busy ? uiText('正在检查…', 'Checking…') : uiText('查看转换计划', 'Preview conversion plan')}
      </button>
    </div>
    {target && <div className="version-upgrade-copy">
      <h3>{uiText('创建 26.3 副本', 'Create a 26.3 copy')}</h3>
      <p>{uiText('当前可复制未锁定的 function 工程：mod 命名空间、简单记分板命令及绝对坐标 setblock。计划中的人工任务需先解决。', 'Unlocked function workspaces can be copied with the mod namespace, simple scoreboard commands and absolute setblock commands. Resolve all manual tasks in the plan first.')}</p>
      <div className="version-upgrade-controls">
        <label>{uiText('新目录名称（与源工程同级）', 'New directory name (beside the source)')}
          <input data-testid="upgrade-output-name" value={outputName} disabled={busy} onChange={e => setOutputName(e.target.value)} /></label>
        <button type="button" data-testid="preview-executable-upgrade" onClick={() => preview(true)} disabled={busy || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(outputName)}>
          {uiText('检查可执行计划', 'Check executable plan')}</button>
      </div>
    </div>}
    {!target && <p>{uiText('请打开 Fabric 或 NeoForge 26.2 工程，再预览同加载器的 26.3 副本。', 'Open a Fabric or NeoForge 26.2 workspace to preview a 26.3 copy using the same loader.')}</p>}
    <div role="status" aria-live="polite">{busy && uiText('正在读取源工程、运行目录和世界指纹。', 'Reading source, runtime directory and world fingerprints.')}</div>
    {error && <p role="alert" className="version-upgrade-error" data-testid="upgrade-preview-error">{error}</p>}
    {execution && <div role="status" data-testid="upgrade-execution-result">
      <p>{execution.conversionComplete ? uiText('副本已创建。请打开新工程完成构建和验收。', 'Copy created. Open the new workspace to build and validate it.')
        : uiText('副本未完成。可继续使用源工程；目标目录保留失败记录。', 'Copy incomplete. Continue with the source; the target directory retains its failure record.')}</p>
      <code className="version-upgrade-path">{execution.targetWorkspaceFile ?? execution.targetDirectory}</code>
    </div>}
    {plan && <>
      <p data-testid="upgrade-copy-policy">{uiText('预览不创建副本。源工程和世界保持不变；恢复时继续使用源工程。', 'Preview creates no copy. Source files and worlds remain unchanged; recovery means continuing with the source workspace.')}</p>
      {plan.targetDirectory && <div className="version-upgrade-copy" data-testid="upgrade-execute-controls">
        <p>{uiText('目标目录：', 'Target directory: ')}<code className="version-upgrade-path">{plan.targetDirectory}</code></p>
        {plan.executionAvailable ? <>
          <label className="version-upgrade-confirm"><input type="checkbox" data-testid="confirm-version-copy" checked={confirmed} disabled={busy}
            onChange={e => setConfirmed(e.target.checked)} />{uiText('使用 26.3 预览创建此副本，世界保留在源工程。', 'Use the 26.3 preview to create this copy; keep worlds in the source.')}</label>
          <button type="button" className="btn-primary" data-testid="execute-version-copy" onClick={execute}
            disabled={busy || !confirmed || state.workbench?.permission.profile === 'read_only'}>{uiText('创建此副本', 'Create this copy')}</button>
        </> : <p data-testid="upgrade-execution-blocked">{uiText('存在人工任务，暂不能创建副本。', 'Manual tasks must be resolved before creating the copy.')}</p>}
      </div>}
      <div className="version-upgrade-filters" aria-label={uiText('按处理方式筛选', 'Filter by disposition')}>
        <button type="button" aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>{uiText('全部', 'All')} · {plan.entries.length}</button>
        {(['convertible', 'manual', 'unsupported'] as const).map(kind => <button key={kind} type="button" aria-pressed={filter === kind} onClick={() => setFilter(kind)}>
          {label(kind)} · {plan.counts[kind]}
        </button>)}
      </div>
      <details className="version-upgrade-basis"><summary>{uiText('计划依据', 'Plan identity')}</summary>
        <dl><dt>{uiText('规则版本', 'Rule version')}</dt><dd>{plan.ruleVersion}</dd>
          <dt>{uiText('源指纹', 'Source fingerprint')}</dt><dd><code>{plan.sourceFingerprint}</code></dd>
          <dt>{uiText('计划编号', 'Plan ID')}</dt><dd><code>{plan.planId}</code></dd></dl>
      </details>
      <div className="version-upgrade-entries" data-testid="upgrade-plan-entries">
        {entries.map(entry => <article key={entry.path} className="version-upgrade-entry" data-disposition={entry.disposition}>
          <div><code className="version-upgrade-path">{entry.path}</code><span className="badge">{label(entry.disposition)}</span></div>
          <p>{meaning(entry)}</p>
          <details><summary>{uiText('原值与处理依据', 'Original value and rule evidence')}</summary>
            <pre>{JSON.stringify(entry.sourceValue, null, 2)}</pre>
            <p>{uiText('依据：', 'Evidence: ')}{entry.evidence} · {entry.reasonCode}</p>
            <p>{uiText('恢复：从源工程取回原始内容。', 'Recovery: retrieve the original content from the source workspace.')}</p>
          </details>
        </article>)}
      </div>
    </>}
  </section>;
};
