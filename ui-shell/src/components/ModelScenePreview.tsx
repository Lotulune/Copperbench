import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Grid2X2, Minus, Plus, RotateCcw } from 'lucide-react';
import { uiText } from '../i18n';
import type { ModelPreviewScene } from '../types/assetPreview';
import type { TextureOrbitState } from './textureOrbit';
import type { ModelCamera, ModelOrbitController } from './modelOrbit';
import './assetPreview.css';

function reasonText(reason?: string) {
  switch(reason) {
    case 'model_missing': return uiText('找不到绑定的模型。请检查模型选择，或在资产与模型中重新导入。', 'The bound model is missing. Check the model selection or import it in Assets & Models.');
    case 'model_parent_missing': return uiText('父模型尚未解析。请补齐模型文件；原版模型可在项目依赖准备完成后重试。', 'A parent model could not be resolved. Add the model, or retry after preparing project dependencies for vanilla models.');
    case 'model_parent_cycle': return uiText('模型的父级引用形成循环，请在 Blockbench 中修正。', 'The model has a parent cycle. Correct it in Blockbench.');
    case 'texture_missing': return uiText('选择贴图后即可预览外观。', 'Choose a texture to preview the appearance.');
    case 'model_too_complex': return uiText('模型超过内嵌预览的容量，请在 Blockbench 中查看。', 'This model exceeds the inline preview limit. View it in Blockbench.');
    case 'model_invalid': return uiText('无法解析模型或贴图，请检查文件后刷新。', 'Could not read the model or textures. Check the files and refresh.');
    case 'java_geometry_unsupported': return uiText('无法静态解析此 Java 模型。可使用 Blockbench 的标准 Java 模型导出，或查看技术详情。', 'Could not read this Java model as static geometry. Use a standard Blockbench Java export or check the technical details.');
    case 'manually_managed': return uiText('此元素由自定义代码控制外观。可在资产与模型中预览它使用的模型。', 'Custom code controls this element’s appearance. Preview its model in Assets & Models.');
    default: return uiText('此模型类型暂不支持立体预览，可在 Blockbench 中查看。', 'This model type does not support 3D preview yet. View it in Blockbench.');
  }
}

export function ModelScenePreview({ scene }: { scene: ModelPreviewScene }) {
  const canvas = useRef<HTMLCanvasElement>(null), controller = useRef<ModelOrbitController | null>(null), camera = useRef<ModelCamera | undefined>(undefined);
  const [state,setState] = useState<TextureOrbitState>({zoom:100,view:'isometric',grid:true});
  const [error,setError] = useState(''), [loading,setLoading] = useState(true), [retry,setRetry] = useState(0);
  const [pan,setPan] = useState(false);
  const contentKey = scene.fingerprint ?? JSON.stringify(scene);
  useEffect(()=> {
    if(scene.kind==='unavailable')return;
    const abort=new AbortController(); setLoading(true);setError('');
    void (async()=> {
      try {
        const {createModelOrbit}=await import('./modelOrbit');
        if(abort.signal.aborted || !canvas.current)return;
        const next=await createModelOrbit(canvas.current,scene,setState,abort.signal,camera.current);
        if(abort.signal.aborted){next.dispose();return;}
        controller.current=next;setLoading(false);
      } catch(failure) {if(!abort.signal.aborted){setError(failure instanceof Error?failure.message:String(failure));setLoading(false);}}
    })();
    return ()=> {abort.abort(); if(controller.current){camera.current=controller.current.camera();controller.current.dispose();controller.current=null;}};
  },[contentKey,retry]);
  if(scene.kind==='unavailable')return <div className="asset-preview-note" role="status"><p>{reasonText(scene.reason)}</p>
    {scene.detail && <details><summary>{uiText('技术详情','Technical details')}</summary><p>{scene.detail}</p></details>}</div>;
  const disabled=loading||Boolean(error), views=[['front',uiText('前视','Front')],['top',uiText('顶视','Top')],['isometric',uiText('透视','Perspective')]] as const;
  const move=(x:number,y:number)=>pan?controller.current?.pan(x,y):controller.current?.orbit(x,y);
  return <div className="asset-orbit model-scene-preview" data-testid="model-scene-preview" data-scene-fingerprint={scene.fingerprint} data-preview-state={loading?'loading':error?'error':'ready'}>
    <div className="asset-orbit-views" role="group" aria-label={uiText('观察方向','View direction')}>
      {views.map(([view,label])=><button key={view} type="button" disabled={disabled} aria-pressed={state.view===view} onClick={()=>controller.current?.setView(view)}>{label}</button>)}
      <button type="button" disabled={disabled} aria-label={uiText('地面网格','Ground grid')} aria-pressed={state.grid} onClick={()=>controller.current?.toggleGrid()}><Grid2X2 size={14}/></button>
    </div>
    <div className="asset-orbit-stage"><canvas key={`${scene.fingerprint ?? 'scene'}-${retry}`} ref={canvas} role="img" tabIndex={disabled?-1:0}
      aria-label={uiText('模型立体预览，可旋转、平移和缩放','3D model preview, rotatable, pannable and zoomable')} />
      {loading&&<p role="status">{uiText('正在准备立体预览…','Preparing 3D preview…')}</p>}
      {error&&<div className="model-preview-render-error" role="alert"><p>{uiText('无法显示立体预览，请重试或检查模型与贴图。','Could not display the 3D preview. Retry or check the model and textures.')}</p>
        <button type="button" onClick={()=>setRetry(n=>n+1)}>{uiText('重试预览','Retry preview')}</button><details><summary>{uiText('技术详情','Technical details')}</summary><p>{error}</p></details></div>}
    </div>
    <div className="asset-orbit-controls">
      <div role="group" aria-label={uiText('移动视角','Move view')}>
        <button type="button" aria-pressed={pan} disabled={disabled} onClick={()=>setPan(!pan)}>{pan?uiText('平移','Pan'):uiText('旋转','Rotate')}</button>
        <button type="button" disabled={disabled} aria-label={pan?uiText('向左平移','Pan left'):uiText('向左旋转','Rotate left')} onClick={()=>move(18,0)}><ArrowLeft size={14}/></button>
        <button type="button" disabled={disabled} aria-label={pan?uiText('向上平移','Pan up'):uiText('向上旋转','Rotate up')} onClick={()=>move(0,18)}><ArrowUp size={14}/></button>
        <button type="button" disabled={disabled} aria-label={pan?uiText('向下平移','Pan down'):uiText('向下旋转','Rotate down')} onClick={()=>move(0,-18)}><ArrowDown size={14}/></button>
        <button type="button" disabled={disabled} aria-label={pan?uiText('向右平移','Pan right'):uiText('向右旋转','Rotate right')} onClick={()=>move(-18,0)}><ArrowRight size={14}/></button>
      </div>
      <div role="group" aria-label={uiText('缩放','Zoom')}>
        <button type="button" disabled={disabled} aria-label={uiText('缩小','Zoom out')} onClick={()=>controller.current?.zoom(1/.86)}><Minus size={14}/></button>
        <output aria-label={uiText('缩放比例','Zoom level')}>{state.zoom}%</output>
        <button type="button" disabled={disabled} aria-label={uiText('放大','Zoom in')} onClick={()=>controller.current?.zoom(.86)}><Plus size={14}/></button>
        <button type="button" disabled={disabled} aria-label={uiText('重置视角','Reset view')} onClick={()=>controller.current?.reset()}><RotateCcw size={14}/></button>
      </div>
    </div>
    {Boolean(scene.missingTextures?.length)&&<p className="asset-preview-note" role="status">{uiText('部分贴图缺失，以紫色棋盘格显示。请补齐贴图后刷新。','Some textures are missing and appear as a purple checkerboard. Add the textures and refresh.')}</p>}
    {scene.untextured && <p className="asset-preview-note">{uiText('未绑定贴图的部分以材质颜色显示。', 'Parts without a bound texture use their material color.')}</p>}
    {scene.defaultPose && <p className="asset-preview-note">{uiText('默认姿态', 'Default pose')}</p>}
    <details className="asset-preview-help"><summary>{uiText('操作帮助','Interaction help')}</summary><p>{uiText('拖动或方向键旋转，Shift + 拖动或 Shift + 方向键平移，滚轮或 + / − 缩放，Home 重置。','Drag or use arrow keys to rotate. Shift + drag or Shift + arrow keys pans. Scroll or + / − zooms. Home resets the view.')}</p></details>
  </div>;
}
