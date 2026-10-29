import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { coreBridge } from '../bridge';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import { OperationFailure, operationFailureText, operationDiagnostics } from '../bridge/operationFailure';
import { uiMessage, uiText } from '../i18n';
import type { FieldChange, ModElementSummary } from '../types/contract';
import type { ModelPreviewScene } from '../types/assetPreview';
import { ModelScenePreview } from './ModelScenePreview';
import { DiagnosticDetails } from './DiagnosticDetails';

export const appearancePath = /^\/(?:fields\/)?(?:texture(?:Top|Left|Front|Right|Back|Bottom|Base64)?|renderType|customModelName|modelResource|blockBase|plantType|mobModelName|mobModelTexture)$/;
const supported = new Set(['block','item','tool','rangeditem','food','plant','livingentity']);
export function hasElementVisualPreview(type: string) { return supported.has(type); }
export function ElementVisualPreview(props: {workspaceId:string; element:ModElementSummary; changes:FieldChange[]}) {
  if(!supported.has(props.element.type))return null;
  return <VisualPreview key={`${props.workspaceId}:${props.element.id}`} {...props}/>;
}
function VisualPreview({workspaceId,element,changes}: {workspaceId:string; element:ModElementSummary; changes:FieldChange[]}) {
  const [scene,setScene]=useState<ModelPreviewScene|null>(null),[error,setError]=useState<unknown>(null),[loading,setLoading]=useState(true);
  const [reload,setReload]=useState(0),[open,setOpen]=useState(true);
  const serialized=JSON.stringify(changes.filter(change=>appearancePath.test(change.path)));
  useEffect(()=> {
    if(!open)return;
    let cancelled=false;setLoading(true);setError(null);
    const timeout=setTimeout(()=>void (async()=> {
      try {
        const result=await coreBridge.sendQuery<{schemaVersion:string;elementId:string;scene:ModelPreviewScene}>({messageType:'query',schemaVersion:'1.0',requestId:safeRandomUUID(),workspaceId,
          operation:'get_element_visual_preview',payload:{elementId:element.id,expectedUpdatedAt:element.updatedAt,changes:JSON.parse(serialized)}});
        if(result.status!=='succeeded'||result.workspaceId!==workspaceId||result.data?.schemaVersion!=='1.0'||result.data.elementId!==element.id||!result.data.scene)
          throw new OperationFailure(result.diagnostics,uiMessage('无法读取元素外观，请刷新后重试。','Could not read the element appearance. Refresh and try again.'));
        if(!cancelled)setScene(result.data.scene);
      } catch(failure){if(!cancelled)setError(failure);}
      finally {if(!cancelled)setLoading(false);}
    })(),180);
    return ()=> {cancelled=true;clearTimeout(timeout);};
  },[workspaceId,element.id,element.updatedAt,serialized,reload,open]);
  return <section className="asset-content-preview element-visual-preview" data-testid="element-visual-preview" aria-label={uiText('元素立体预览','Element 3D preview')}>
    <div className="element-preview-heading"><button type="button" aria-expanded={open} onClick={()=>setOpen(!open)}>{uiText('立体预览','3D preview')}</button>
      <div>{serialized!=='[]'&&<span>{uiText('当前草稿','Current draft')}</span>}<button type="button" disabled={loading||!open} aria-label={uiText('刷新预览','Refresh preview')} onClick={()=>setReload(n=>n+1)}><RefreshCw size={14}/></button></div>
    </div>
    {open&&<div className="element-preview-body" aria-busy={loading}>
      {error?<div role="alert" className="asset-preview-error"><p>{operationFailureText(error)}</p><DiagnosticDetails diagnostics={operationDiagnostics(error)}/><button type="button" onClick={()=>setReload(n=>n+1)}>{uiText('重试预览','Retry preview')}</button></div>
        :scene?<ModelScenePreview scene={scene}/>:<div className="element-preview-placeholder"/>}
      {loading&&<p className="element-preview-loading" role="status">{uiText('正在读取外观…','Loading appearance…')}</p>}
    </div>}
  </section>;
}
