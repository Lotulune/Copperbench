import test from 'node:test';
import assert from 'node:assert/strict';
import {modelFaces,validateModelMesh} from '../src/components/modelGeometry.ts';

test('each face points outwards and partial cuboids use cropped default UVs',()=>{
  const directions=['down','up','north','south','west','east'];
  const faces=modelFaces([{from:[0,0,0],to:[16,8,16],faces:Object.fromEntries(directions.map(face=>[face,{texture:face}]))}]);
  const normals=[[0,-1,0],[0,1,0],[0,0,-1],[0,0,1],[-1,0,0],[1,0,0]];
  faces.forEach((face,index)=>{
    const p=face.positions,a=[p[3]-p[0],p[4]-p[1],p[5]-p[2]],b=[p[6]-p[0],p[7]-p[1],p[8]-p[2]];
    const cross=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
    assert.ok(cross.reduce((sum,n,i)=>sum+n*normals[index][i],0)>0);
  });
  assert.deepEqual(faces[2].uv,[0,.5,0,0,1,0,1,.5]);
});
test('face rotation and element rotation preserve the specified UVs and pivot',()=>{
  const [face]=modelFaces([{from:[0,0,0],to:[16,16,16],rotation:{origin:[8,8,8],axis:'y',angle:90},faces:{north:{texture:'test',uv:[0,0,16,16],rotation:90}}}]);
  assert.deepEqual(face.uv,[0,0,1,0,1,1,0,1]);
  assert.ok(Math.abs(face.positions[0])<1e-10);assert.ok(Math.abs(face.positions[2])<1e-10);
});
test('rejects invalid or unbounded geometry instead of rendering misleading content',()=>{
  for(const element of [{from:[0,0,0],to:[Infinity,16,16],faces:{}},{from:[1,0,0],to:[0,1,1],faces:{}},
    {from:[0,0,0],to:[16,16,16],faces:{north:{texture:'x',rotation:45}}}])assert.throws(()=>modelFaces([element]));
  assert.throws(()=>modelFaces([]));
});
test('mesh protocol validates complete normals, UVs and bounded finite coordinates',()=>{
  const mesh={positions:[0,0,0,1,0,0,0,1,0],normals:[0,0,1,0,0,1,0,0,1],uv:[0,0,1,0,0,1],color:[1,1,1],opacity:1,doubleSided:true};
  assert.doesNotThrow(()=>validateModelMesh(mesh));
  assert.throws(()=>validateModelMesh({...mesh,uv:[0,0]}));assert.throws(()=>validateModelMesh({...mesh,positions:[Infinity,...mesh.positions.slice(1)]}));
  assert.throws(()=>validateModelMesh({...mesh,opacity:2}));
});
