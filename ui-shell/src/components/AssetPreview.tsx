import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Grid2X2, Minus, Plus, RotateCcw } from 'lucide-react';
import { getAssetPreview, renderAssetPreviewFailure } from '../bridge/assetPreviewBridge';
import { uiText, useUiLocale } from '../i18n';
import type { AssetRecord } from '../types/assets';
import type { AssetPreviewImage, AssetPreviewProjection } from '../types/assetPreview';
import { createTextureOrbit, type TextureOrbitController, type TextureOrbitState } from './textureOrbit.js';
import './assetPreview.css';

function reasonText(reason?: string, isModel = false): string {
  switch (reason) {
    case 'image_too_large': return uiText('图像超过 2 MiB，无法在此预览。', 'This image exceeds the 2 MiB preview limit.');
    case 'image_dimensions_exceeded': return uiText('图像尺寸超过 2048 × 2048。', 'Image dimensions exceed 2048 × 2048.');
    case 'document_too_large': return uiText('模型 JSON 超过 256 KiB，无法在此预览。', 'This model JSON exceeds the 256 KiB preview limit.');
    case 'invalid_image': return uiText('无法解码此图像。', 'This image could not be decoded.');
    default: return isModel
      ? uiText('此模型暂无内嵌预览，可在 Blockbench 中打开。', 'This model has no inline preview. Open it in Blockbench.')
      : uiText('此格式暂无内嵌预览。', 'This format has no inline preview.');
  }
}

function imageUrl(image: AssetPreviewImage): string | null {
  if (!image.base64 || !['image/png', 'image/jpeg'].includes(image.mediaType)
      || image.base64.length > 2_800_000 || !image.width || !image.height || image.width > 2048 || image.height > 2048) return null;
  return `data:${image.mediaType};base64,${image.base64}`;
}

function TextureOrbit({ image }: { image: AssetPreviewImage }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const controller = useRef<TextureOrbitController | null>(null);
  const [state, setState] = useState<TextureOrbitState>({ zoom: 100, view: 'isometric', grid: true });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [sampleSize, setSampleSize] = useState('');
  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(false);
    void (async () => {
      let bitmap: ImageBitmap | undefined;
      try {
        if (!imageUrl(image) || !image.base64) throw new Error('Invalid image payload');
        const bytes = Uint8Array.from(atob(image.base64), char => char.charCodeAt(0));
        bitmap = await createImageBitmap(new Blob([bytes], { type: image.mediaType }));
        if (cancelled || !canvas.current) return;
        if (bitmap.width !== image.width || bitmap.height !== image.height) throw new Error('Image dimensions changed');
        const factor = Math.min(1, 64 / Math.max(bitmap.width, bitmap.height));
        const buffer = document.createElement('canvas');
        buffer.width = Math.max(1, Math.round(bitmap.width * factor)); buffer.height = Math.max(1, Math.round(bitmap.height * factor));
        const context = buffer.getContext('2d', { willReadFrequently: true });
        if (!context) throw new Error('Canvas unavailable');
        context.imageSmoothingEnabled = false; context.drawImage(bitmap, 0, 0, buffer.width, buffer.height);
        controller.current = createTextureOrbit(canvas.current, context.getImageData(0, 0, buffer.width, buffer.height), setState);
        setSampleSize(factor < 1 ? `${buffer.width} × ${buffer.height}` : '');
        setLoading(false);
      } catch { if (!cancelled) { setError(true); setLoading(false); } }
      finally { bitmap?.close(); }
    })();
    return () => { cancelled = true; controller.current?.dispose(); controller.current = null; };
  }, [image]);
  const disabled = loading || error;
  const views = [['front', uiText('前视', 'Front')], ['top', uiText('顶视', 'Top')], ['isometric', uiText('透视', 'Perspective')]] as const;
  return <div className="asset-orbit" data-testid="asset-texture-orbit" data-preview-state={loading ? 'loading' : error ? 'error' : 'ready'}>
    <div className="asset-orbit-views" role="group" aria-label={uiText('观察方向', 'View direction')}>
      {views.map(([view, label]) => <button key={view} type="button" disabled={disabled} aria-pressed={state.view === view}
        onClick={() => controller.current?.setView(view)}>{label}</button>)}
      <button type="button" disabled={disabled} aria-pressed={state.grid} aria-label={uiText('地面网格', 'Ground grid')}
        onClick={() => controller.current?.toggleGrid()}><Grid2X2 size={14} /></button>
    </div>
    <div className="asset-orbit-stage"><canvas ref={canvas} tabIndex={disabled ? -1 : 0} role="img"
      aria-label={uiText('纹理挤出预览，可旋转和平移', 'Texture extrusion preview, rotatable and pannable')} />
      {loading && <p role="status">{uiText('正在读取纹理…', 'Loading texture…')}</p>}
      {error && <p role="alert">{uiText('无法显示旋转预览。图像可能完全透明或无法解码。', 'Could not render the orbit preview. The image may be fully transparent or could not be decoded.')}</p>}
    </div>
    <div className="asset-orbit-controls">
      <div role="group" aria-label={uiText('旋转视角', 'Rotate view')}>
        <button type="button" disabled={disabled} aria-label={uiText('向左旋转', 'Rotate left')} onClick={() => controller.current?.orbit(18, 0)}><ArrowLeft size={14} /></button>
        <button type="button" disabled={disabled} aria-label={uiText('向上旋转', 'Rotate up')} onClick={() => controller.current?.orbit(0, 18)}><ArrowUp size={14} /></button>
        <button type="button" disabled={disabled} aria-label={uiText('向下旋转', 'Rotate down')} onClick={() => controller.current?.orbit(0, -18)}><ArrowDown size={14} /></button>
        <button type="button" disabled={disabled} aria-label={uiText('向右旋转', 'Rotate right')} onClick={() => controller.current?.orbit(-18, 0)}><ArrowRight size={14} /></button>
      </div>
      <div role="group" aria-label={uiText('缩放', 'Zoom')}>
        <button type="button" disabled={disabled} aria-label={uiText('缩小', 'Zoom out')} onClick={() => controller.current?.zoom(1 / .86)}><Minus size={14} /></button>
        <output aria-label={uiText('缩放比例', 'Zoom level')}>{state.zoom}%</output>
        <button type="button" disabled={disabled} aria-label={uiText('放大', 'Zoom in')} onClick={() => controller.current?.zoom(.86)}><Plus size={14} /></button>
        <button type="button" disabled={disabled} aria-label={uiText('重置视角', 'Reset view')} onClick={() => controller.current?.reset()}><RotateCcw size={14} /></button>
      </div>
    </div>
    <details className="asset-preview-help"><summary>{uiText('操作帮助', 'Interaction help')}</summary>
      <p>{uiText('拖动或方向键旋转，Shift + 拖动或 Shift + 方向键平移，滚轮或 + / − 缩放，Home 重置。', 'Drag or use arrow keys to rotate. Shift + drag or Shift + arrow keys pans. Scroll or + / − zooms. Home resets the view.')}</p>
      {sampleSize && <p>{uiText(`纹理采样 ${sampleSize}`, `Texture sampling ${sampleSize}`)}</p>}
    </details>
  </div>;
}

function ImagePreview({ image, initialMode = 'flat' }: { image: AssetPreviewImage; initialMode?: 'flat' | 'orbit' }) {
  const [mode, setMode] = useState<'flat' | 'orbit'>(initialMode);
  const [broken, setBroken] = useState(false);
  const url = imageUrl(image);
  if (!url || image.reason) return <p className="asset-preview-note">{reasonText(image.reason)}</p>;
  return <div className="asset-image-preview">
    <div className="asset-preview-mode" role="group" aria-label={uiText('图像预览方式', 'Image preview mode')}>
      <button type="button" aria-pressed={mode === 'flat'} onClick={() => setMode('flat')}>{uiText('图像', 'Image')}</button>
      <button type="button" aria-pressed={mode === 'orbit'} onClick={() => setMode('orbit')}>{uiText('立体纹理', 'Texture extrusion')}</button>
    </div>
    {mode === 'flat' ? <div className="asset-image-flat">{broken ? <p role="alert">{reasonText('invalid_image')}</p>
      : <img src={url} alt={image.relativePath} onError={() => setBroken(true)} />}</div> : <TextureOrbit image={image} />}
    <div className="asset-preview-caption"><span title={image.relativePath}>{image.relativePath.split('/').pop()}</span><span>{image.width} × {image.height}</span></div>
  </div>;
}

function ModelPreview({ preview }: { preview: AssetPreviewProjection }) {
  const [textureId, setTextureId] = useState(preview.textures[0]?.assetId);
  const selected = preview.textures.find(image => image.assetId === textureId) ?? preview.textures[0];
  return <>
    {selected && <div className="asset-model-textures">
      {preview.textures.length > 1 && <label className="asset-model-texture-choice"><span>{uiText('贴图', 'Texture')}</span>
        <select value={selected.assetId} aria-label={uiText('模型贴图', 'Model texture')} onChange={event => setTextureId(event.target.value)}>
          {preview.textures.map(image => <option key={image.assetId} value={image.assetId}>{image.relativePath.split('/').pop()}</option>)}
        </select></label>}
      <ImagePreview key={selected.assetId} image={selected} initialMode="orbit" />
    </div>}
    <details className="asset-model-json" open={!selected}><summary>{uiText('模型 JSON', 'Model JSON')}</summary><pre tabIndex={0}>{preview.document}</pre>
      {selected?.rawValue && <code className="asset-model-texture-reference">{selected.rawValue}</code>}
    </details>
  </>;
}

export function AssetPreview({ workspaceId, asset }: { workspaceId?: string; asset: AssetRecord }) {
  useUiLocale();
  const [preview, setPreview] = useState<AssetPreviewProjection | null>(null);
  const [error, setError] = useState<{ failure: unknown } | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setPreview(null); setError(null);
    if (!workspaceId || !asset.sha256) return;
    void getAssetPreview(workspaceId, asset.id, asset.sha256).then(result => { if (!cancelled) setPreview(result); })
      .catch(failure => { if (!cancelled) setError({ failure }); });
    return () => { cancelled = true; };
  }, [workspaceId, asset.id, asset.sha256, reload]);
  return <section className="asset-content-preview" aria-label={uiText('资产预览', 'Asset preview')} data-testid="asset-content-preview">
    {!workspaceId || !asset.sha256 ? <p className="asset-preview-note">{uiText('尚未取得资产内容。', 'Asset content is not available.')}</p>
      : error ? <div className="asset-preview-error" role="alert"><p>{renderAssetPreviewFailure(error.failure)}</p><button type="button" onClick={() => setReload(value => value + 1)}>{uiText('重试预览', 'Retry preview')}</button></div>
        : !preview ? <p className="asset-preview-note" role="status">{uiText('正在读取资产…', 'Loading asset…')}</p>
          : preview.kind === 'image' && preview.image ? <ImagePreview key={preview.sha256} image={preview.image} />
            : preview.kind === 'model_json' ? <ModelPreview key={preview.sha256} preview={preview} />
              : <p className="asset-preview-note">{reasonText(preview.reason, asset.category === 'model')}</p>}
  </section>;
}
