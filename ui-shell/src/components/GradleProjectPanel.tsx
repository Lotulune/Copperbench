import { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { gradleProjectBridge, type GradleProjectStatus } from '../bridge/gradleProjectBridge';
import { uiText } from '../i18n';
import type { WorkspaceSummary } from '../types/contract';

/**
 * What Copperbench identified in an adopted Gradle project (ADR-0020), and the project's two desktop choices:
 * whether `.copperbench/` goes into its `.gitignore` (decision 7) and which Gradle user home builds use (decision 8).
 */
export function GradleProjectPanel({ workspace }: { workspace: WorkspaceSummary }) {
  const [status, setStatus] = useState<GradleProjectStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const available = gradleProjectBridge.available;
  useEffect(() => {
    let current = true;
    setStatus(null); setError(null);
    if (!available) return;
    void gradleProjectBridge.status().then(result => { if (current) setStatus(result); })
      .catch(failure => { if (current) setError(failure instanceof Error ? failure.message : uiText('读取失败', 'Could not load')); });
    return () => { current = false; };
  }, [available, workspace.id]);
  const apply = (change: () => Promise<GradleProjectStatus>) => {
    if (busy) return;
    setBusy(true); setError(null);
    void change().then(setStatus)
      .catch(failure => setError(failure instanceof Error ? failure.message : uiText('设置未保存', 'The setting was not saved')))
      .finally(() => setBusy(false));
  };
  const problems = workspace.gradleProject?.problems ?? [];
  const gitIgnore = status?.gitIgnore;
  const askGitIgnore = gitIgnore?.repository && !gitIgnore.ignored && gitIgnore.decision === null;

  return <section className="overview-section overview-gradle-project" aria-labelledby="overview-project-title" data-testid="gradle-project-panel">
    <div className="overview-section-heading"><h2 id="overview-project-title">{uiText('接管的 Gradle 工程', 'Adopted Gradle project')}</h2></div>
    <dl className="overview-project-facts">
      <div><dt>{uiText('目标', 'Target')}</dt><dd data-testid="gradle-project-target">{workspace.generator.displayName}</dd></div>
      <div><dt>{uiText('模组 ID', 'Mod ID')}</dt><dd><code>{workspace.gradleProject?.modId ?? uiText('未识别', 'Not identified')}</code></dd></div>
      {status && <div className="overview-project-root"><dt>{uiText('文件夹', 'Folder')}</dt><dd><code>{status.projectRoot}</code></dd></div>}
    </dl>
    <p className="overview-project-note">{uiText(
      'Copperbench 不生成也不改写这个工程的文件，构建使用工程自己的 Gradle Wrapper。结构化元素、Procedure 与版本迁移在这里不可用：直接编辑源码，再用构建和 GameTest 验证。',
      'Copperbench neither generates nor rewrites this project’s files, and builds use the project’s own Gradle Wrapper. Structured elements, Procedures and version migration are not available here: edit the source directly, then verify with a build and GameTests.')}</p>
    {problems.length > 0 && <div className="overview-alert-row" role="alert" data-testid="gradle-project-problems"><AlertTriangle size={15} aria-hidden="true" />
      <span>{uiText('构建与验收不可用：', 'Building and acceptance are unavailable: ')}{problems.map(problem => problem.message).join(' ')}</span></div>}

    {askGitIgnore && <div className="overview-project-question" role="group" aria-labelledby="gradle-project-gitignore-question" data-testid="gradle-project-gitignore">
      <p id="gradle-project-gitignore-question">{uiText(
        '这个工程在 Git 仓库中。要把 .copperbench/ 加入工程的 .gitignore 吗？它保存本地历史、任务记录和对话，不应提交。加入前会先建一个恢复点。',
        'This project is in a Git repository. Add .copperbench/ to the project’s .gitignore? It holds local history, task records and chats, which should not be committed. A recovery point is taken before the change.')}</p>
      <div className="overview-inline-actions">
        <button type="button" className="overview-action overview-action-primary" disabled={busy} data-testid="gradle-project-gitignore-add"
          onClick={() => apply(() => gradleProjectBridge.decideGitIgnore(true))}>{uiText('加入 .gitignore', 'Add to .gitignore')}</button>
        <button type="button" className="overview-action" disabled={busy} data-testid="gradle-project-gitignore-decline"
          onClick={() => apply(() => gradleProjectBridge.decideGitIgnore(false))}>{uiText('不改动', 'Leave it')}</button>
      </div>
    </div>}
    {gitIgnore?.repository && !askGitIgnore && <p className="overview-project-line-status" data-testid="gradle-project-gitignore-state">
      {gitIgnore.ignored ? uiText('.copperbench/ 已被工程的 .gitignore 忽略。', '.copperbench/ is ignored by the project’s .gitignore.')
        : uiText('.copperbench/ 没有加入 .gitignore，提交前请留意。', '.copperbench/ is not in .gitignore; watch for it before committing.')}
      {!gitIgnore.ignored && <button type="button" className="overview-link" disabled={busy} onClick={() => apply(() => gradleProjectBridge.decideGitIgnore(true))}>
        {uiText('加入 .gitignore', 'Add to .gitignore')}</button>}
    </p>}

    {status && <fieldset className="overview-project-choice" disabled={busy} data-testid="gradle-project-gradle-home">
      <legend>{uiText('构建使用的 Gradle 用户目录', 'Gradle user home for builds')}</legend>
      <label><input type="radio" name="gradle-user-home" value="product" checked={status.gradleUserHome.mode === 'product'}
        onChange={() => apply(() => gradleProjectBridge.setGradleUserHome('product'))} />
        <span><strong>{uiText('产品目录（默认）', 'Product directory (default)')}</strong>
          <small>{uiText('复用 Copperbench 的缓存与网络设置，包括国内镜像。', 'Reuses Copperbench’s cache and network settings, including mirrors.')}</small>
          <code>{status.gradleUserHome.product}</code></span></label>
      <label><input type="radio" name="gradle-user-home" value="environment" checked={status.gradleUserHome.mode === 'environment'}
        onChange={() => apply(() => gradleProjectBridge.setGradleUserHome('environment'))} />
        <span><strong>{uiText('工程环境默认', 'Project environment default')}</strong>
          <small>{uiText('GRADLE_USER_HOME 或用户目录下的 .gradle；不使用产品的镜像设置。从下一个任务起生效。', 'GRADLE_USER_HOME or .gradle in your home folder, without the product’s mirror settings. Applies from the next task.')}</small>
          <code>{status.gradleUserHome.environment}</code></span></label>
    </fieldset>}
    {!available && <p className="overview-project-line-status" role="status">{uiText('版本控制与 Gradle 目录的设置在桌面应用中修改。', 'Version control and Gradle directory settings are changed in the desktop app.')}</p>}
    {error && <p className="overview-project-line-status is-error" role="alert">{error}</p>}
  </section>;
}
