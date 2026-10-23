import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { modelFaces, validateModelMesh, type PreviewFace } from './modelGeometry';
import type { ModelPreviewScene, AssetPreviewImage, ModelMesh } from '../types/assetPreview';
import type { TextureOrbitController, TextureOrbitState } from './textureOrbit';

export interface ModelCamera { position: number[]; target: number[]; focus: number[]; extent: number; view: TextureOrbitState['view']; grid: boolean }
export interface ModelOrbitController extends TextureOrbitController { camera(): ModelCamera }

async function decode(image: AssetPreviewImage): Promise<ImageBitmap> {
  if (!image.base64 || image.base64.length > 2_800_000 || !['image/png','image/jpeg'].includes(image.mediaType)) throw new Error('Invalid preview texture');
  const bytes = Uint8Array.from(atob(image.base64), c => c.charCodeAt(0));
  const bitmap = await createImageBitmap(new Blob([bytes], { type: image.mediaType }), { imageOrientation: 'flipY', premultiplyAlpha: 'none' });
  if (bitmap.width !== image.width || bitmap.height !== image.height || bitmap.width > 2048 || bitmap.height > 2048) { bitmap.close(); throw new Error('Texture dimensions changed'); }
  return bitmap;
}

export async function createModelOrbit(canvas: HTMLCanvasElement, projection: ModelPreviewScene,
  onChange: (state: TextureOrbitState) => void, signal: AbortSignal, saved?: ModelCamera): Promise<ModelOrbitController> {
  const bitmaps = new Map<string, ImageBitmap>(), textures: THREE.Texture[] = [], materials: THREE.Material[] = [], geometries: THREE.BufferGeometry[] = [];
  let renderer: THREE.WebGLRenderer | undefined;
  let cleanListeners = () => {};
  const dispose = () => { cleanListeners(); geometries.forEach(g => g.dispose()); materials.forEach(m => m.dispose());
    textures.forEach(t => t.dispose()); bitmaps.forEach(b => b.close()); renderer?.dispose(); renderer?.forceContextLoss(); };
  try {
    if (Object.keys(projection.textures ?? {}).length > 32) throw new Error('Too many model textures');
    for (const [key,image] of Object.entries(projection.textures ?? {})) {
      if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
      bitmaps.set(key, await decode(image));
    }
    if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
    renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, preserveDrawingBuffer: true });
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2)); renderer.setClearColor(0, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    const world = new THREE.Scene(), object = new THREE.Group(); world.add(object);
    world.add(new THREE.AmbientLight(0xffffff, 1.7));
    const light = new THREE.DirectionalLight(0xffffff, 2.1); light.position.set(-3,5,4); world.add(light);
    type Batch = { positions:number[]; uv:number[]; normals:number[]; indices:number[]; texture?:string; shade:boolean; color:[number,number,number]; opacity:number; doubleSided:boolean; repeatTexture:boolean; map?:THREE.Texture };
    const batches = new Map<string,Batch>(), translucent = new Set<string>();
    const missing = new THREE.DataTexture(new Uint8Array([170,42,170,255, 35,30,35,255, 35,30,35,255, 170,42,170,255]), 2, 2);
    missing.needsUpdate = true; missing.magFilter = THREE.NearestFilter; missing.minFilter = THREE.NearestFilter;
    missing.colorSpace = THREE.SRGBColorSpace; textures.push(missing);
    const maps = new Map<string, THREE.Texture>();
    for (const [key, bitmap] of bitmaps) {
      const map = new THREE.Texture(bitmap); map.needsUpdate = true; map.flipY = false;
      map.magFilter = THREE.NearestFilter; map.minFilter = THREE.NearestFilter; map.generateMipmaps = false; map.colorSpace = THREE.SRGBColorSpace;
      const frame = projection.textures?.[key].frame;
      if (frame) { map.repeat.set(frame.width / bitmap.width, frame.height / bitmap.height); map.offset.set(frame.x / bitmap.width, 1 - (frame.y + frame.height) / bitmap.height); }
      maps.set(key, map); textures.push(map);
      if(projection.textures?.[key].mediaType==='image/png') {
        const surface=document.createElement('canvas');surface.width=bitmap.width;surface.height=bitmap.height;
        const context=surface.getContext('2d',{willReadFrequently:true});if(!context)throw new Error('Canvas unavailable');
        context.drawImage(bitmap,0,0);const pixels=context.getImageData(0,0,bitmap.width,bitmap.height).data;
        for(let i=3;i<pixels.length;i+=4)if(pixels[i]>0&&pixels[i]<255){translucent.add(key);break;}
        surface.width=surface.height=1;
      }
    }
    function batch(texture:string|undefined,shade=true,color:[number,number,number]=[1,1,1],opacity=1,doubleSided=false,repeatTexture=false,map?:THREE.Texture) {
      const key=JSON.stringify([texture,shade,color,opacity,doubleSided,repeatTexture]);let result=batches.get(key);
      if(!result){result={positions:[],uv:[],normals:[],indices:[],texture,shade,color,opacity,doubleSided,repeatTexture,map};batches.set(key,result);}
      return result;
    }
    function addFace(face: PreviewFace, mapOverride?: THREE.Texture) {
      const result=batch(face.texture,face.shade,[1,1,1],1,false,false,mapOverride),offset=result.positions.length/3,p=face.positions;
      const normal=new THREE.Vector3(p[3]-p[0],p[4]-p[1],p[5]-p[2]).cross(new THREE.Vector3(p[6]-p[0],p[7]-p[1],p[8]-p[2])).normalize().toArray();
      result.positions.push(...p);result.uv.push(...face.uv);for(let i=0;i<4;i++)result.normals.push(...normal);result.indices.push(offset,offset+1,offset+2,offset,offset+2,offset+3);
    }
    function addMesh(mesh:ModelMesh) {
      validateModelMesh(mesh);const result=batch(mesh.texture,true,mesh.color,mesh.opacity,mesh.doubleSided,mesh.repeatTexture),offset=result.positions.length/3;
      for(const n of mesh.positions)result.positions.push(n);for(const n of mesh.uv)result.uv.push(n);for(const n of mesh.normals)result.normals.push(n);
      for(let i=0;i<mesh.positions.length/3;i++)result.indices.push(offset+i);
    }
    if (projection.kind === 'item') {
      const layers = projection.layers ?? [];
      if (!layers.length || layers.length > 5) throw new Error('Missing item layers');
      const surface = document.createElement('canvas');
      surface.width = surface.height = Math.min(128, Math.max(16, ...[...bitmaps.values()].map(b => b.width)));
      const ctx = surface.getContext('2d', { willReadFrequently: true }); if (!ctx) throw new Error('Canvas unavailable');
      // Bitmaps were decoded upside down for WebGL. Restore the image's top-left origin for alpha contour extraction.
      ctx.translate(0, surface.height); ctx.scale(1,-1); ctx.imageSmoothingEnabled = false;
      for (const key of layers) { const b = bitmaps.get(key); if (b) {
        const frame = projection.textures?.[key].frame ?? {x:0,y:0,width:b.width,height:b.height};
        ctx.drawImage(b, frame.x, b.height-frame.y-frame.height, frame.width, frame.height, 0,0,surface.width,surface.height);
      } }
      const pixels = ctx.getImageData(0,0,surface.width,surface.height);
      for(let i=3;i<pixels.data.length;i+=4)if(pixels.data[i]>0&&pixels.data[i]<255){translucent.add('@item');break;}
      const map = new THREE.CanvasTexture(surface); map.magFilter = map.minFilter = THREE.NearestFilter; map.colorSpace = THREE.SRGBColorSpace; textures.push(map);
      const side = 1 / surface.width, depth = 1 / 32;
      const pixel = (x:number,y:number) => x >= 0 && y >= 0 && x < surface.width && y < surface.height && pixels.data[(y*surface.width+x)*4+3] > 0;
      let count = 0;
      const quad = (positions:number[], uv:number[]) => addFace({ positions, uv, texture:'@item', shade:true }, map);
      quad([0,1,depth,0,0,depth,1,0,depth,1,1,depth], [0,1,0,0,1,0,1,1]);
      quad([1,1,-depth,1,0,-depth,0,0,-depth,0,1,-depth], [1,1,1,0,0,0,0,1]);
      for (let y=0;y<surface.height;y++) for(let x=0;x<surface.width;x++) if(pixel(x,y)) {
        count++; const a=x*side, b=1-(y+1)*side, A=a+side, B=b+side;
        const uv = Array(4).fill([(x+.5)*side,1-(y+.5)*side]).flat();
        if(!pixel(x-1,y)) quad([a,B,-depth,a,b,-depth,a,b,depth,a,B,depth],uv);
        if(!pixel(x+1,y)) quad([A,B,depth,A,b,depth,A,b,-depth,A,B,-depth],uv);
        if(!pixel(x,y-1)) quad([a,B,-depth,a,B,depth,A,B,depth,A,B,-depth],uv);
        if(!pixel(x,y+1)) quad([a,b,depth,a,b,-depth,A,b,-depth,A,b,depth],uv);
      }
      if (!count) throw new Error('Item texture has no visible pixels');
    } else if(projection.kind==='mesh') {
      const meshes=projection.meshes??[];
      if(!meshes.length||meshes.length>64||meshes.reduce((sum,mesh)=>sum+mesh.positions.length,0)>360_000)throw new Error('Model exceeds the mesh limit');
      meshes.forEach(addMesh);
    } else {
      modelFaces(projection.elements ?? []).forEach(face => addFace(face));
      if (projection.rotationX) object.rotation.x = projection.rotationX * Math.PI / 180;
    }
    const blended: {mesh:THREE.Mesh; geometry:THREE.BufferGeometry; indices:number[]; centers:THREE.Vector3[]; order:number[]}[]=[];
    for(const group of batches.values()) {
      const geometry=new THREE.BufferGeometry();geometries.push(geometry);
      geometry.setAttribute('position',new THREE.Float32BufferAttribute(group.positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(group.uv,2));geometry.setAttribute('normal',new THREE.Float32BufferAttribute(group.normals,3));geometry.setIndex(group.indices);
      let map=group.map??(group.texture?maps.get(group.texture)??missing:null);
      if(map&&group.repeatTexture){map=map.clone();map.wrapS=map.wrapT=THREE.RepeatWrapping;map.needsUpdate=true;textures.push(map);}
      const transparent=group.opacity<1||Boolean(group.texture&&translucent.has(group.texture));
      const parameters={map,color:new THREE.Color().setRGB(...group.color),opacity:group.opacity,alphaTest:.01,transparent,depthWrite:!transparent,side:group.doubleSided?THREE.DoubleSide:THREE.FrontSide};
      const material=group.shade?new THREE.MeshLambertMaterial(parameters):new THREE.MeshBasicMaterial(parameters);materials.push(material);
      const mesh=new THREE.Mesh(geometry,material);object.add(mesh);
      if(transparent){const centers:THREE.Vector3[]=[],order:number[]=[];
        for(let i=0;i<group.indices.length;i+=3){const center=new THREE.Vector3();for(let j=0;j<3;j++)center.add(new THREE.Vector3().fromArray(group.positions,group.indices[i+j]*3));centers.push(center.multiplyScalar(1/3));order.push(i/3);}
        blended.push({mesh,geometry,indices:group.indices,centers,order});
      }
    }
    canvas.dataset.meshCount=String(batches.size);
    const bounds = new THREE.Box3().setFromObject(object), center = bounds.getCenter(new THREE.Vector3()), size = bounds.getSize(new THREE.Vector3());
    const extent = Math.max(size.x,size.y,size.z,.1);
    object.position.sub(center); object.position.y += size.y / 2;
    const target = new THREE.Vector3(0,size.y/2,0), distance = extent * 3;
    const camera = new THREE.PerspectiveCamera(35,1,extent*.01,extent*100);
    const controls = new OrbitControls(camera,canvas); controls.target.copy(target); controls.minDistance=extent*.7; controls.maxDistance=extent*12;
    controls.enableDamping=false; controls.autoRotate=false; controls.cursorStyle='grab';
    let view: TextureOrbitState['view']='isometric', disposed=false, frame=0;
    const grid = new THREE.GridHelper(extent*4,16); world.add(grid); geometries.push(grid.geometry);
    materials.push(...(Array.isArray(grid.material) ? grid.material : [grid.material]));
    const request = () => { if(disposed || frame) return; frame=requestAnimationFrame(()=> {frame=0;
      world.updateMatrixWorld(true);camera.updateMatrixWorld(true);
      for(const group of blended){const matrix=new THREE.Matrix4().multiplyMatrices(camera.matrixWorldInverse,group.mesh.matrixWorld);const depths=group.centers.map(center=>center.clone().applyMatrix4(matrix).z);
        group.order.sort((a,b)=>depths[a]-depths[b]);let at=0;for(const triangle of group.order)for(let j=0;j<3;j++)group.geometry.index!.setX(at++,group.indices[triangle*3+j]);group.geometry.index!.needsUpdate=true;
      }
      renderer!.render(world,camera);
      onChange({zoom:Math.round(distance/controls.getDistance()*100),view,grid:grid.visible}); }); };
    const setView = (next:'front'|'top'|'isometric') => {
      view=next; const d=controls.getDistance() || distance;
      controls.target.copy(target); const direction=next==='front' ? new THREE.Vector3(0,0,-1) : next==='top' ? new THREE.Vector3(0,1,-.001) : new THREE.Vector3(1,.75,1).normalize();
      camera.position.copy(target).addScaledVector(direction,d); controls.update(); request();
    };
    camera.position.copy(target).add(new THREE.Vector3(1,.75,1).normalize().multiplyScalar(distance)); controls.update();
    if(saved) { const focus=new THREE.Vector3().fromArray(saved.focus),ratio=extent/saved.extent;
      camera.position.fromArray(saved.position).sub(focus).multiplyScalar(ratio).add(target);
      controls.target.fromArray(saved.target).sub(focus).multiplyScalar(ratio).add(target); view=saved.view; grid.visible=saved.grid; controls.update(); }
    const orbit=(dx:number,dy:number)=> { view='custom'; controls.rotateLeft(dx*.009); controls.rotateUp(dy*.009); controls.update(); request(); };
    const pan=(dx:number,dy:number)=> { controls.pan(dx,dy); controls.update(); request(); };
    const zoom=(factor:number)=> { controls.dollyIn(factor); controls.update(); request(); };
    const reset=()=> { camera.position.copy(target).add(new THREE.Vector3(1,.75,1).normalize().multiplyScalar(distance)); controls.target.copy(target); view='isometric'; controls.update(); request(); };
    const listeners=new AbortController();
    canvas.addEventListener('pointerdown',()=>canvas.focus({preventScroll:true}),{signal:listeners.signal});
    canvas.addEventListener('dblclick',reset,{signal:listeners.signal});
    canvas.addEventListener('keydown',event=> {
      if(event.altKey||event.ctrlKey||event.metaKey)return;
      const directions:Record<string,[number,number]>={ArrowLeft:[18,0],ArrowRight:[-18,0],ArrowUp:[0,18],ArrowDown:[0,-18]};
      if(directions[event.key]) (event.shiftKey ? pan:orbit)(...directions[event.key]);
      else if(event.key==='+'||event.key==='=')zoom(.86); else if(event.key==='-'||event.key==='_')zoom(1/.86); else if(event.key==='Home')reset(); else return;
      event.preventDefault();event.stopPropagation();
    },{signal:listeners.signal});
    controls.addEventListener('change',request); controls.addEventListener('start',()=>{view='custom';});
    const resize = new ResizeObserver(entries=> { const {width,height}=entries[0].contentRect; if(width<1||height<1)return;
      renderer!.setSize(width,height,false); camera.aspect=width/height; camera.updateProjectionMatrix(); request(); }); resize.observe(canvas);
    const theme=()=> { const color=getComputedStyle(canvas).getPropertyValue('--border-subtle').trim(); if(color) {
      const material=Array.isArray(grid.material)?grid.material:[grid.material]; material.forEach(m=>{m.vertexColors=false;(m as THREE.LineBasicMaterial).color.set(color);}); } request(); };
    const observer=new MutationObserver(theme); observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme','class']}); theme();
    cleanListeners=()=> {disposed=true; if(frame)cancelAnimationFrame(frame); listeners.abort(); controls.dispose(); resize.disconnect(); observer.disconnect();};
    request();
    return { reset,setView,orbit,pan,zoom,toggleGrid(){grid.visible=!grid.visible;request();},
      camera:()=>({position:camera.position.toArray(),target:controls.target.toArray(),focus:target.toArray(),extent,view,grid:grid.visible}), dispose };
  } catch(error) {dispose();throw error;}
}
