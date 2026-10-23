import { expect, test, type Page } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

async function installPreview(page: Page, mode='ready') {
  await page.addInitScript(()=>localStorage.setItem('copperbench.ui.locale','en'));
  await page.goto('/');await page.waitForSelector('[data-testid="app-shell"]');
  await page.evaluate(async mode=> {
    const url=performance.getEntriesByType('resource').map(e=>e.name).find(url=>new URL(url).pathname==='/src/bridge/index.ts')??'/src/bridge/index.ts';
    const {coreBridge}=await import(url); const original=coreBridge.sendQuery.bind(coreBridge);
    const image=(name:string,color:string)=> {
      const canvas=document.createElement('canvas');canvas.width=canvas.height=16;const context=canvas.getContext('2d')!;
      context.fillStyle=color;context.fillRect(0,0,16,16);context.fillStyle='#2f493f';
      for(let x=0;x<16;x++)for(let y=0;y<16;y++)if((x+y)%7===0)context.fillRect(x,y,1,1);
      if(name==='item'){context.clearRect(0,0,5,16);context.clearRect(11,0,5,16);}
      const base64=canvas.toDataURL('image/png').split(',')[1];
      return {assetId:`asset:${'a'.repeat(64)}`,relativePath:`assets/test/textures/${name}.png`,sha256:'a'.repeat(64),width:16,height:16,mediaType:'image/png',size:atob(base64).length,base64};
    };
    const palette={down:image('bottom','#775039'),up:image('top','#559672'),north:image('front','#bc7741'),south:image('back','#ba7138'),west:image('left','#ac6533'),east:image('right','#c88448')};
    const requests:unknown[]=[];(window as any).__ELEMENT_PREVIEW_REQUESTS__=requests;
    let currentType='block';
    coreBridge.sendQuery=async(query:any)=> {
      if(query.operation==='get_mod_element_editor') {
        const result=await original(query);currentType=result.data.element.type;
        result.data.sections = result.data.sections.filter((section:any) => section.id !== 'preview.visual');
        for(const section of result.data.sections) section.fields = section.fields.filter((field:any) => !['/textureTop','/modelResource'].includes(field.path));
        result.data.sections.push({id:'preview.visual',title:{key:'preview.visual',fallback:'Appearance'},fields:[
          {path:'/textureTop',label:{key:'field.textureTop',fallback:'Top texture'},control:'text',value:'green',required:false,readOnly:false,options:[],diagnostics:[]},
          {path:'/modelResource',label:{key:'field.modelResource',fallback:'Model resource'},control:'text',value:'',required:false,readOnly:false,options:[],diagnostics:[]}
        ]});return result;
      }
      if(query.operation!=='get_element_visual_preview'&&query.operation!=='get_asset_preview')return original(query);
      requests.push(query.payload);
      const change=(query.payload.changes??[]).find((v:any)=>v.path==='/textureTop')?.value;
      const custom=(query.payload.changes??[]).some((v:any)=>v.path==='/modelResource'&&v.value==='custom');
      const textures={...palette,up:change==='blue'?image('top','#406bd0'):palette.up};
      const item=currentType==='item';
      const faces=Object.fromEntries(Object.keys(palette).map(face=>[face,{texture:face}]));
      let scene:any={kind:item?'item':'model',fingerprint:`${item?'item':custom?'custom':'block'}-${change??'green'}`,
        elements:[{from:custom?[3,0,3]:[0,0,0],to:custom?[13,16,13]:[16,16,16],faces,...(custom?{rotation:{origin:[8,8,8],axis:'y',angle:22.5}}:{})}],
        textures:item?{item:image('item','#c88448')}:textures,layers:item?['item']:[],missingTextures:[]};
      if(mode==='unsupported')scene={kind:'unavailable',reason:'model_parent_missing'};
      if(mode==='missing') {delete scene.textures.up;scene.missingTextures=['up'];}
      if(mode==='delayed'&&change==='red')await new Promise(resolve=>setTimeout(resolve,700));
      const asset=query.operation==='get_asset_preview';
      const data=asset?{schemaVersion:'1.0',assetId:query.payload.assetId,sha256:query.payload.expectedSha256,kind:'model_json',textures:[],document:'{"parent":"minecraft:block/cube"}',scene}
        :{schemaVersion:'1.0',elementId:query.payload.elementId,scene};
      return {messageType:'query_result',schemaVersion:'1.0',requestId:query.requestId,workspaceId:query.workspaceId,operation:query.operation,status:'succeeded',revision:42,data,diagnostics:[]};
    };
  },mode);
}
async function openLamp(page:Page) {await page.getByTestId('nav-elements').click();await page.locator('.element-card').filter({hasText:'Copper Lamp'}).click();}
async function ready(page:Page) {const preview=page.getByTestId('element-visual-preview');await expect(preview.getByTestId('model-scene-preview')).toHaveAttribute('data-preview-state','ready');return preview;}
async function capture(page:Page,name:string) {const directory=path.resolve('../output/element-preview-validation');await mkdir(directory,{recursive:true});await page.screenshot({path:path.join(directory,name)});}

test('block geometry renders, keyboard and buttons orbit, pan, zoom and reset',async({page},info)=>{
  await installPreview(page);await openLamp(page);const preview=await ready(page);
  const canvas=preview.locator('canvas'),drawing=()=>canvas.evaluate((c:HTMLCanvasElement)=>c.toDataURL());
  const first=await drawing();await preview.getByRole('button',{name:'Rotate left',exact:true}).click();await expect.poll(drawing).not.toBe(first);
  await preview.getByRole('button',{name:'Zoom in',exact:true}).click();await expect(preview.getByRole('status',{name:'Zoom level'})).toHaveText('116%');
  await preview.getByRole('button',{name:'Reset view',exact:true}).click();await expect(preview.getByRole('status',{name:'Zoom level'})).toHaveText('100%');
  await canvas.focus();await page.keyboard.press('ArrowRight');await expect.poll(drawing).not.toBe(first);await page.keyboard.press('Home');
  await preview.getByRole('button',{name:'Rotate',exact:true}).click();await preview.getByRole('button',{name:'Pan left',exact:true}).click();
  await preview.getByRole('button',{name:'Reset view',exact:true}).click();
  await capture(page,`block-${info.project.name}.png`);
  await page.evaluate(()=>{document.documentElement.dataset.theme='dark';});await page.waitForTimeout(300);await capture(page,`block-dark-${info.project.name}.png`);
});
test('appearance drafts refresh geometry without saving and stale responses cannot replace them',async({page},info)=>{
  await installPreview(page,'delayed');await openLamp(page);await ready(page);
  await page.getByTestId('field-textureTop').fill('red');
  await expect.poll(()=>page.evaluate(()=>(window as any).__ELEMENT_PREVIEW_REQUESTS__.some((p:any)=>p.changes?.some((c:any)=>c.value==='red')))).toBe(true);
  await page.getByTestId('field-textureTop').fill('blue');const preview=await ready(page);
  await expect(preview.getByTestId('model-scene-preview')).toHaveAttribute('data-scene-fingerprint','block-blue');
  await page.waitForTimeout(800);await expect(preview.getByTestId('model-scene-preview')).toHaveAttribute('data-scene-fingerprint','block-blue');
  await page.getByTestId('field-modelResource').fill('custom');await ready(page);
  await expect(preview.getByTestId('model-scene-preview')).toHaveAttribute('data-scene-fingerprint','custom-blue');
  await preview.scrollIntoViewIfNeeded();await expect(preview).toContainText('Current draft');
  await capture(page,`custom-model-${info.project.name}.png`);
});
test('items render their alpha contour and refreshing retains the view',async({page},info)=>{
  await installPreview(page);await page.getByTestId('nav-elements').click();await page.locator('.element-card').filter({hasText:'Trail Compass'}).click();const preview=await ready(page);
  await expect(preview.getByTestId('model-scene-preview')).toHaveAttribute('data-scene-fingerprint','item-green');
  await preview.getByRole('button',{name:'Zoom in',exact:true}).click();await preview.getByRole('button',{name:'Refresh preview',exact:true}).click();await ready(page);
  await expect(preview.getByRole('status',{name:'Zoom level'})).toHaveText('116%');await capture(page,`item-${info.project.name}.png`);
});
test('missing parents explain the issue and missing textures keep the real geometry',async({page})=>{
  await installPreview(page,'unsupported');await openLamp(page);const preview=page.getByTestId('element-visual-preview');
  await expect(preview).toContainText('A parent model could not be resolved');await expect(preview.locator('canvas')).toHaveCount(0);
  await installPreview(page,'missing');await openLamp(page);await ready(page);await expect(page.getByTestId('element-visual-preview')).toContainText('Some textures are missing');
});
test('narrow editor keeps the preview and editing controls reachable',async({page},info)=>{
  await page.setViewportSize({width:620,height:900});await installPreview(page);await openLamp(page);const preview=await ready(page);
  await preview.getByRole('button',{name:'Zoom in',exact:true}).click();await expect(preview.getByRole('status',{name:'Zoom level'})).toHaveText('116%');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await capture(page,`compact-${info.project.name}.png`);await page.getByTestId('field-textureTop').fill('blue');await ready(page);
});
test('assets use the same actual geometry viewer',async({page},info)=>{
  await installPreview(page);await page.getByTestId('nav-assets').click();await page.getByTestId(`asset-card-asset:${'1'.repeat(64)}`).click();
  await expect(page.getByTestId('asset-content-preview').getByTestId('model-scene-preview')).toHaveAttribute('data-preview-state','ready');
  await capture(page,`asset-model-${info.project.name}.png`);
});
test('simple creation selects the element appearance and can switch back to assets',async({page},info)=>{
  await page.addInitScript(()=>localStorage.setItem('copperbench.workbench.mode','simple'));await installPreview(page);
  await page.locator('[data-testid^="simple-element-"]').filter({hasText:'Copper Lamp'}).click();
  const preview=await ready(page);
  const appearance=page.getByRole('button',{name:'Element appearance',exact:true}),assets=page.getByRole('button',{name:'Textures and models',exact:true});
  const background=(locator:typeof appearance)=>locator.evaluate(element=>getComputedStyle(element).backgroundColor);
  await expect.poll(()=>background(appearance)).not.toBe(await background(assets));
  const front=preview.getByRole('button',{name:'Front',exact:true}),perspective=preview.getByRole('button',{name:'Perspective',exact:true});
  await expect.poll(()=>background(perspective)).not.toBe(await background(front));
  await front.click();await expect.poll(()=>background(front)).not.toBe(await background(perspective));
  const grid=preview.getByRole('button',{name:'Ground grid',exact:true});const selectedGrid=await background(grid);await grid.click();await expect(grid).toHaveAttribute('aria-pressed','false');await page.mouse.move(0,0);await expect.poll(()=>background(grid)).not.toBe(selectedGrid);
  await grid.click();await perspective.click();await page.waitForTimeout(200);await capture(page,`simple-${info.project.name}.png`);
  await page.getByRole('button',{name:'Textures and models',exact:true}).click();await expect(page.locator('#creation-asset')).toBeVisible();
  await page.getByRole('button',{name:'Element appearance',exact:true}).click();await ready(page);
});
