import React, { useEffect, useState } from 'react';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiText } from '../i18n';
import { useUiLocale } from '../i18n/locale';
import type { AcceptanceCoverageDiagnostics, TaskSummary } from '../types/contract';

export const AcceptanceSummary: React.FC<{ task: TaskSummary; fallback: React.ReactNode }> = ({ task, fallback }) => {
  useUiLocale();
  const { readAcceptanceDiagnostics } = useWorkbench();
  const [coverage, setCoverage] = useState<AcceptanceCoverageDiagnostics | null>(null);
  useEffect(() => {
    let active = true;
    setCoverage(null);
    readAcceptanceDiagnostics(task.id).then(value => { if (active) setCoverage(value); })
      .catch(() => { if (active) setCoverage(null); }); // Older hosts retain the existing v1.0 report presentation.
    return () => { active = false; };
  }, [readAcceptanceDiagnostics, task.id, task.state, task.verification?.status,
    task.verification?.reasonCode, task.verifiedExport?.status]);
  if (!coverage || coverage.taskId !== task.id) return <>{fallback}</>;

  const headings: Record<AcceptanceCoverageDiagnostics['state'], string> = {
    running: uiText('验收正在执行', 'Acceptance is running'),
    report_unavailable: uiText('验收报告不可用', 'Acceptance report is unavailable'),
    business_failed: uiText('业务用例未通过', 'Business tests failed'),
    no_business: uiText('没有业务用例执行', 'No business tests ran'),
    duplicate: uiText('报告包含重复业务身份', 'Report contains duplicate business identities'),
    required_cases_missing: uiText('必要业务用例缺失', 'Required business cases are missing'),
    insufficient: uiText('独立业务用例数量不足', 'Too few distinct business tests'),
    passed_current_input: uiText('本次输入验收通过', 'Acceptance passed for this input'),
    passed_historical_input: uiText('历史输入验收通过', 'Acceptance passed for historical input')
  };
  const counts = [
    [uiText('最低业务数', 'Minimum business tests'), coverage.minimumTests, 'minimum'],
    [uiText('实际执行', 'Distinct tests executed'), coverage.uniqueExecuted, 'executed'],
    [uiText('数量差额', 'Shortfall'), coverage.shortfall, 'shortfall']
  ] as const;
  const identities = (name: string, cases: AcceptanceCoverageDiagnostics['duplicates'], id: string) => cases.length > 0 && <details data-testid={`acceptance-${id}`}>
    <summary>{name} ({cases.length})</summary>
    <ul>{cases.slice(0, 20).map((item, index) => <li key={index}>
      {item.className && <><code>{item.className.slice(0, 512)}{item.className.length > 512 ? '…' : ''}</code><span> · </span></>}
      <code>{item.name.slice(0, 512)}{item.name.length > 512 ? '…' : ''}</code>
    </li>)}</ul>
    {(cases.length > 20 || cases.some(item => item.className.length > 512 || item.name.length > 512))
      && <p>{uiText('此处显示部分身份，完整内容见报告。', 'Some identities are abbreviated here. See the report for complete values.')}</p>}
  </details>;
  return <div className="acceptance-summary" data-testid="acceptance-summary" data-state={coverage.state}>
    <div role="status" aria-live="polite">
      <strong>{headings[coverage.state]}</strong>
      <dl>{counts.map(([label, count, id]) => <div key={id}><dt>{label}</dt>
        <dd data-testid={`acceptance-${id}`}>{count ?? uiText('尚未确定', 'Not yet known')}</dd></div>)}</dl>
    </div>
    {coverage.evidenceScope === 'required_cases' && <p>{uiText(`冻结的必要用例：${coverage.required ?? 0} 项。`, `Frozen required cases: ${coverage.required ?? 0}.`)}</p>}
    {identities(uiText('重复身份', 'Duplicate identities'), coverage.duplicates, 'duplicates')}
    {identities(uiText('缺失用例', 'Missing cases'), coverage.missingCases, 'missing')}
    {coverage.inputScope === 'historical_at_export' && <p>{uiText('此次导出对应较早的受测输入。', 'This export belongs to earlier tested input.')}</p>}
  </div>;
};
