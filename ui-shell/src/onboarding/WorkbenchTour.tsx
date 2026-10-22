import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { ArrowLeft, ArrowRight, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useWorkbenchMode } from '../context/WorkbenchModeContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { uiText, useUiLocale } from '../i18n';
import './workbenchTour.css';

const TourContext = createContext<{ openTour: () => void }>({ openTour: () => {} });
export const useWorkbenchTour = () => useContext(TourContext);
type Bounds = { x: number; y: number; width: number; height: number };
const visibleTarget = (selector: string) => Array.from(document.querySelectorAll<HTMLElement>(selector)).find(element => {
  const bounds = element.getBoundingClientRect();
  return bounds.width > 0 && bounds.height > 0 && getComputedStyle(element).visibility !== 'hidden';
});

export function WorkbenchTourProvider({ children }: { children: ReactNode }) {
  const { setActiveView } = useWorkbench();
  const { mode } = useWorkbenchMode();
  const [open, setOpen] = useState(false);
  const openTour = useCallback(() => { setActiveView('hub'); setOpen(true); }, [setActiveView]);
  return <TourContext.Provider value={{ openTour }}>{children}{open && <WorkbenchTour key={mode} onClose={() => setOpen(false)} />}</TourContext.Provider>;
}

function WorkbenchTour({ onClose }: { onClose: () => void }) {
  useUiLocale();
  const { mode } = useWorkbenchMode();
  const { setActiveView } = useWorkbench();
  const [step, setStep] = useState(0);
  const [closing, setClosing] = useState(false);
  const [target, setTarget] = useState<Bounds | null>(null);
  const [viewport, setViewport] = useState({ width: window.innerWidth, height: window.innerHeight });
  const [cardHeight, setCardHeight] = useState(250);
  const [direction, setDirection] = useState(1);
  const highlight = useRef<SVGRectElement>(null);
  const hole = useRef<SVGRectElement>(null);
  const currentBounds = useRef<Bounds | null>(null);
  const closeTimer = useRef<number | undefined>(undefined);
  const maskId = `workbench-tour-mask-${mode}`;
  const finish = useCallback(() => {
    if (closeTimer.current !== undefined) return;
    setClosing(true);
    try { localStorage.setItem(`copperbench.tour.${mode}`, 'seen'); } catch { /* The tour remains replayable. */ }
    closeTimer.current = window.setTimeout(onClose, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 80 : 180);
  }, [mode, onClose]);
  const card = useDialogA11y(true, finish);
  useEffect(() => () => window.clearTimeout(closeTimer.current), []);

  const steps = mode === 'simple' ? [
    { target: '[data-testid="simple-new-workspace"], [data-testid="simple-navigation-toggle"]', title: uiText('先准备你的作品', 'Start with your creation'),
      body: uiText('新建作品时选择名称、保存目录和 Minecraft 版本。已有作品可直接打开，模式切换会保留当前工作区。', 'Choose a name, location and Minecraft version for a new creation, or open an existing one. Switching modes keeps your workspace.') },
    { target: '[data-testid="simple-agent-setup"]', title: uiText('安装并连接创作助手', 'Install and connect an assistant'),
      body: uiText('这里可以检测五种 CLI、查看官方安装方式并打开登录。程序已安装和模型连接成功会分别显示。', 'Detect all five CLIs, find official installation steps and open sign-in here. Installation and successful model requests are shown separately.') },
    { target: '.creation-composer', title: uiText('描述一件想做的东西', 'Describe one thing to make'),
      body: uiText('先从一个普通方块开始，描述外观和属性。需要你批准或选择时，助手会停下来等待；停止后已有修改仍会保留。', 'Start with a simple block and describe its appearance and properties. The assistant waits for approvals and choices. Stopping retains existing changes.') },
    { target: '.creation-preview-tabs', title: uiText('核对实际内容', 'Inspect the actual content'),
      body: uiText('查看工作区纹理与设置；需要更多选项时切换专业模式。助手的回复不等同于已保存，请核对内容与任务结果。', 'Inspect workspace textures and settings. Switch to Professional for more options. Check saved content and task results as well as the reply.') },
    { target: '[data-testid="simple-playtest"]', title: uiText('去游戏里试一试', 'Try it in the game'),
      body: uiText('构建成功后启动试玩，在独立世界中获得并放置方块。正常退出，再启动新客户端打开同一世界，检查保存结果。', 'After a successful build, obtain and place the block in a separate test world. Exit normally, start a new client and reopen that world to check persistence.') },
    { target: '[data-testid="simple-export"]', title: uiText('导出，然后继续创作', 'Export and keep creating'),
      body: uiText('从这里导出真实构建产物。对话可在侧栏整理，作品修改可从恢复点检查。帮助中有完整的中英文首作路线。', 'Export the actual build artifact here. Manage conversations in the sidebar and inspect changes through recovery points. Help includes the full first-creation guide.') }
  ] : [
    { target: '[data-testid="nav-new-workspace"], [data-testid="nav-hub"]', title: uiText('创建或打开工作区', 'Create or open a workspace'),
      body: uiText('作品的加载器、Minecraft 版本和保存位置由工作区确定。简洁与专业模式共用当前工作区。', 'Your workspace defines the loader, Minecraft version and location. Simple and Professional share this workspace.') },
    { target: '[data-testid="nav-elements"]', title: uiText('编辑元素并保存', 'Edit and save elements'),
      body: uiText('在元素列表新建或选择方块，编辑属性后应用修改。切换界面会保留未完成的输入。', 'Create or select a block in the element list, edit its properties and apply changes. Unfinished input is retained when switching views.') },
    { target: '[data-testid="nav-assets"]', title: uiText('为作品添加素材', 'Add assets to your creation'),
      body: uiText('管理纹理、模型和声音，检查元素实际引用的素材。首作可以使用内置石头纹理。', 'Manage textures, models and sounds, and check the assets your elements reference. Use the built-in stone texture for your first creation.') },
    { target: '[data-testid="titlebar-build-btn"], [data-testid="hub-build-btn"]', title: uiText('构建并读取结果', 'Build and read the result'),
      body: uiText('构建任务受理后，在任务面板等待最终结果。失败时读取具体诊断，修复并查看新任务的结果。', 'After a build is accepted, wait for the final result in the task panel. Read diagnostics on failure, repair the problem and check the new task.') },
    { target: '[data-testid="titlebar-run-btn"]', title: uiText('试玩、检查、导出', 'Playtest, inspect and export'),
      body: uiText('试玩使用已保存内容。检查游戏表现与重开世界后的保存结果，再从更多运行选项导出作品。', 'Playtests use saved content. Check behavior in the game and after reopening the world, then export from More run options.') },
    { target: '[data-testid="workbench-mode-control"]', title: uiText('随时切换创作方式', 'Switch how you create'),
      body: uiText('鼠标移到顶部模式名称，展开后选择简洁或专业模式。键盘聚焦也能展开；切换时草稿、任务和对话都会保留。', 'Hover over the mode name, or focus it with the keyboard, to choose Simple or Professional. Switching retains drafts, tasks and conversations.') }
  ];
  const current = steps[step];

  useLayoutEffect(() => {
    let frame = 0;
    const element = visibleTarget(current.target);
    const read = () => {
      const bounds = element?.getBoundingClientRect();
      setViewport({ width: window.innerWidth, height: window.innerHeight });
      setTarget(bounds ? { x: Math.max(4, bounds.x - 6), y: Math.max(4, bounds.y - 6),
        width: Math.min(bounds.width + 12, window.innerWidth - 8), height: Math.min(bounds.height + 12, window.innerHeight - 8) } : null);
      if (card.current) setCardHeight(card.current.offsetHeight);
    };
    const schedule = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(read); };
    if (element) element.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'instant' });
    read();
    const observer = new ResizeObserver(schedule);
    if (element) observer.observe(element);
    if (card.current) observer.observe(card.current);
    window.addEventListener('resize', schedule); document.addEventListener('scroll', schedule, true);
    return () => { cancelAnimationFrame(frame); observer.disconnect(); window.removeEventListener('resize', schedule); document.removeEventListener('scroll', schedule, true); };
  }, [current.target, card]);

  useLayoutEffect(() => {
    if (!target) return;
    const start = currentBounds.current ?? target;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const started = performance.now(); let frame = 0;
    const draw = (now: number) => {
      const progress = reduced ? 1 : Math.min(1, (now - started) / 380);
      const eased = 1 - Math.pow(1 - progress, 4);
      const bounds = Object.fromEntries(Object.entries(target).map(([key, value]) => [key, start[key as keyof Bounds] + (value - start[key as keyof Bounds]) * eased])) as Bounds;
      currentBounds.current = bounds;
      for (const element of [hole.current, highlight.current]) for (const [key, value] of Object.entries(bounds)) element?.setAttribute(key, String(value));
      if (progress < 1) frame = requestAnimationFrame(draw);
    };
    draw(started); return () => cancelAnimationFrame(frame);
  }, [target]);

  const width = Math.min(370, viewport.width - 24), height = Math.min(cardHeight, viewport.height - 24);
  let x = (viewport.width - width) / 2, y = (viewport.height - height) / 2;
  if (target) {
    const right = target.x + target.width + 16;
    if (right + width <= viewport.width - 12) { x = right; y = target.y; }
    else if (target.x - width - 16 >= 12) { x = target.x - width - 16; y = target.y; }
    else { x = target.x; y = target.y + target.height + 16;
      if (y + height > viewport.height - 12) y = target.y - height - 16; }
  }
  x = Math.max(12, Math.min(x, viewport.width - width - 12)); y = Math.max(12, Math.min(y, viewport.height - height - 12));
  return createPortal(<div className="workbench-tour" data-testid="workbench-tour" data-closing={closing} data-mode={mode}>
    <svg className="workbench-tour-mask" width="100%" height="100%" aria-hidden="true"><defs><mask id={maskId}>
      <rect width="100%" height="100%" fill="white" /><rect ref={hole} rx="9" fill="black" />
    </mask></defs><rect width="100%" height="100%" fill="var(--tour-overlay)" mask={`url(#${maskId})`} />
      {target && <rect ref={highlight} rx="9" className="workbench-tour-highlight" fill="none" />}</svg>
    <section ref={card} role="dialog" aria-modal="true" aria-labelledby="workbench-tour-title" aria-describedby="workbench-tour-description"
      className="workbench-tour-card" data-testid="workbench-tour-card" style={{ width, transform: `translate(${x}px, ${y}px)` }}>
      <header><span>{uiText('快速教程', 'Quick tour')} · {step + 1} / {steps.length}</span><button type="button" onClick={finish} aria-label={uiText('退出教程', 'Exit tour')}><X size={17} aria-hidden="true" /></button></header>
      <div className="workbench-tour-track" aria-hidden="true"><span style={{ transform: `scaleX(${(step + 1) / steps.length})` }} /></div>
      <div className="workbench-tour-copy" key={`${mode}-${step}`} data-direction={direction} aria-live="polite">
        <h2 id="workbench-tour-title">{current.title}</h2><p id="workbench-tour-description">{current.body}</p></div>
      {step === 0 && <button type="button" className="workbench-tour-guide-link" onClick={() => { finish(); setActiveView('new-workspace'); }}>{uiText('新建作品', 'New creation')}</button>}
      <footer><button type="button" className="btn-secondary" disabled={step === 0 || closing} onClick={() => { setDirection(-1); setStep(value => value - 1); }}><ArrowLeft size={14} />{uiText('上一步', 'Back')}</button>
        <button type="button" className="btn-primary" data-testid="tour-next" disabled={closing} onClick={() => { if (step === steps.length - 1) finish(); else { setDirection(1); setStep(value => value + 1); } }}>{step === steps.length - 1 ? uiText('开始创作', 'Start creating') : uiText('下一步', 'Next')}<ArrowRight size={14} /></button></footer>
      <button type="button" className="workbench-tour-guide-link" onClick={() => { finish(); setActiveView('help'); }}>{uiText('阅读完整首作路线', 'Read the full first-creation guide')}</button>
    </section>
  </div>, document.body);
}
