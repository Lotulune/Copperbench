import { useEffect, useRef, useState } from 'react';
import { FolderOpen, Plus, X } from 'lucide-react';
import { uiText } from '../i18n';
import { useDialogA11y } from '../hooks/useDialogA11y';
import copperIcon from '../assets/workspace-icons/mod-copper.svg';
import grassIcon from '../assets/workspace-icons/mod-grass.svg';
import crystalIcon from '../assets/workspace-icons/mod-crystal.svg';
import lanternIcon from '../assets/workspace-icons/mod-lantern.svg';

type IconChoice = { id: string; src?: string };
const presetSources: Record<string, string> = { copper: copperIcon, grass: grassIcon, crystal: crystalIcon, lantern: lanternIcon };
const validCustom = (value: unknown): value is IconChoice & { src: string } => {
  const icon = value as IconChoice | null;
  return icon?.id === 'custom' && typeof icon.src === 'string' && icon.src.length < 1500000 && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(icon.src);
};

export function WorkspaceIconChooser({ workspaceId, name }: { workspaceId: string; name: string }) {
  const storageKey = `copperbench.workspace-icon.${workspaceId}`;
  const [selected, setSelected] = useState<IconChoice>(() => {
    try {
      const value = JSON.parse(localStorage.getItem(storageKey) || 'null');
      if (validCustom(value)) return value;
      if (value?.id === 'folder' || presetSources[value?.id]) return { id: value.id, src: presetSources[value.id] };
    } catch { /* The icon can still be changed for this session. */ }
    return { id: 'copper', src: copperIcon };
  });
  const [draft, setDraft] = useState(selected);
  const [custom, setCustom] = useState<IconChoice | null>(selected.id === 'custom' ? selected : null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const uploadSequence = useRef(0);
  const close = () => { uploadSequence.current++; setOpen(false); setBusy(false); };
  const dialog = useDialogA11y(open, close);
  useEffect(() => () => { uploadSequence.current++; }, []);
  const choices = [
    { id: 'folder', label: uiText('文件夹', 'Folder'), src: undefined },
    { id: 'copper', label: uiText('铜矿', 'Copper ore'), src: copperIcon },
    { id: 'grass', label: uiText('草方块', 'Grass block'), src: grassIcon },
    { id: 'crystal', label: uiText('晶簇', 'Crystal'), src: crystalIcon },
    { id: 'lantern', label: uiText('灯笼', 'Lantern'), src: lanternIcon },
    ...(custom ? [{ ...custom, label: uiText('自定义', 'Custom') }] : [])
  ];
  const upload = async (file?: File) => {
    if (!file) return;
    if (!['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setError(uiText('请选择不超过 2 MB 的 PNG、JPG、WebP 或 GIF。', 'Choose a PNG, JPG, WebP or GIF no larger than 2 MB.')); return;
    }
    const sequence = ++uploadSequence.current;
    const url = URL.createObjectURL(file); setBusy(true); setError(null);
    try {
      const image = new Image(); image.src = url; await image.decode();
      if (sequence !== uploadSequence.current) return;
      const canvas = document.createElement('canvas'); canvas.width = 128; canvas.height = 128;
      const context = canvas.getContext('2d');
      if (!context || !image.naturalWidth || !image.naturalHeight) throw new Error('Invalid image');
      const scale = Math.min(128 / image.naturalWidth, 128 / image.naturalHeight);
      const width = image.naturalWidth * scale, height = image.naturalHeight * scale;
      context.imageSmoothingEnabled = false; context.drawImage(image, (128 - width) / 2, (128 - height) / 2, width, height);
      const value = { id: 'custom', src: canvas.toDataURL('image/png') }; setCustom(value); setDraft(value);
    } catch { if (sequence === uploadSequence.current) setError(uiText('无法读取图片，请选择其他文件。', 'Could not read the image. Choose another file.')); }
    finally { URL.revokeObjectURL(url); if (sequence === uploadSequence.current) setBusy(false); }
  };
  const save = () => {
    try { localStorage.setItem(storageKey, JSON.stringify(draft)); setSelected(draft); close(); }
    catch { setError(uiText('无法保存图标，请检查浏览器存储空间。', 'Could not save the icon. Check browser storage.')); }
  };
  return <>
    <button type="button" className="nav-workspace-icon" data-testid="workspace-icon-chooser-trigger"
      title={uiText('更换工作区图标', 'Change workspace icon')} aria-label={uiText('更换工作区图标', 'Change workspace icon')}
      onClick={() => { setDraft(selected); setError(null); setOpen(true); }}>
      {selected.src ? <img src={selected.src} alt="" /> : <FolderOpen size={19} aria-hidden="true" />}
    </button>
    {open && <div className="modal-overlay" onMouseDown={event => { if (event.target === event.currentTarget) close(); }}>
      <div className="modal-card workspace-icon-dialog" ref={dialog} role="dialog" aria-modal="true" aria-labelledby="workspace-icon-title">
        <header className="workspace-icon-heading"><div><h2 id="workspace-icon-title">{uiText('工作区图标', 'Workspace icon')}</h2><p>{name}</p></div>
          <button type="button" onClick={close} aria-label={uiText('关闭图标选择', 'Close icon chooser')}><X size={17} aria-hidden="true" /></button></header>
        <div className="workspace-icon-options" role="radiogroup" aria-label={uiText('选择工作区图标', 'Choose workspace icon')}
          onKeyDown={event => {
            if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return;
            event.preventDefault();
            const focused = event.target instanceof HTMLElement ? event.target.closest<HTMLButtonElement>('[data-icon-choice]')?.dataset.iconChoice : undefined;
            const current = choices.findIndex(choice => choice.id === (focused ?? draft.id));
            const index = event.key === 'Home' ? 0 : event.key === 'End' ? choices.length - 1 : (current + (['ArrowRight', 'ArrowDown'].includes(event.key) ? 1 : -1) + choices.length) % choices.length;
            setDraft(choices[index]); event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="radio"]')[index]?.focus();
          }}>
          {choices.map(choice => <button key={choice.id} type="button" role="radio" aria-checked={draft.id === choice.id}
            tabIndex={draft.id === choice.id ? 0 : -1} onClick={() => setDraft(choice)} data-icon-choice={choice.id}>
            {choice.src ? <img src={choice.src} alt="" /> : <FolderOpen size={36} aria-hidden="true" />}<span>{choice.label}</span></button>)}
        </div>
        <div className="workspace-icon-upload"><input ref={input} type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden
          aria-label={uiText('上传工作区图标', 'Upload workspace icon')} onChange={event => { void upload(event.target.files?.[0]); event.target.value = ''; }} />
          <button type="button" className="btn-secondary" onClick={() => input.current?.click()} disabled={busy}><Plus size={15} aria-hidden="true" />{uiText('上传图片', 'Upload image')}</button>
          <span>{uiText('最大 2 MB', 'Up to 2 MB')}</span></div>
        {error && <p className="workspace-icon-error" role="alert">{error}</p>}
        <footer className="workspace-icon-actions"><button type="button" className="btn-secondary" onClick={close}>{uiText('取消', 'Cancel')}</button>
          <button type="button" className="btn-primary" onClick={save} disabled={busy}>{busy ? uiText('读取图片…', 'Reading image…') : uiText('使用此图标', 'Use this icon')}</button></footer>
      </div>
    </div>}
  </>;
}
