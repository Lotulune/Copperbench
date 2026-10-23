import type { ModelElement, ModelFace, ModelVector, ModelMesh } from '../types/assetPreview';

export function validateModelMesh(mesh: ModelMesh) {
  const arrays=[mesh.positions,mesh.uv,mesh.normals];
  if(arrays.some(values=>!Array.isArray(values)||values.some(n=>!Number.isFinite(n)||Math.abs(n)>1_000_000))
    || !mesh.positions.length || mesh.positions.length%9!==0 || mesh.positions.length>360_000
    || mesh.normals.length!==mesh.positions.length || mesh.uv.length!==mesh.positions.length/3*2
    || !Array.isArray(mesh.color) || mesh.color.length!==3 || mesh.color.some(n=>!Number.isFinite(n)||n<0)
    || !Number.isFinite(mesh.opacity)||mesh.opacity<0||mesh.opacity>1)throw new Error('Invalid model mesh');
}

export interface PreviewFace { positions: number[]; uv: number[]; texture: string; shade: boolean }
const directions = ['down', 'up', 'north', 'south', 'west', 'east'] as const;
type Direction = typeof directions[number];
function vector(value: ModelVector) {
  if (!Array.isArray(value) || value.length !== 3 || value.some(n => !Number.isFinite(n) || Math.abs(n) > 1024)) throw new Error('Invalid model coordinates');
  return value;
}

/** Java model face orientation, default UV projection and element rotation. No renderer or DOM required. */
export function modelFaces(elements: ModelElement[]): PreviewFace[] {
  if (!Array.isArray(elements) || elements.length > 512) throw new Error('Model exceeds the geometry limit');
  const result: PreviewFace[] = [];
  for (const element of elements) {
    const [x, y, z] = vector(element.from), [X, Y, Z] = vector(element.to);
    if (x > X || y > Y || z > Z || !element.faces || typeof element.faces !== 'object') throw new Error('Invalid model element');
    const positions: Record<Direction, ModelVector[]> = {
      down: [[x,y,Z], [x,y,z], [X,y,z], [X,y,Z]], up: [[x,Y,z], [x,Y,Z], [X,Y,Z], [X,Y,z]],
      north: [[X,Y,z], [X,y,z], [x,y,z], [x,Y,z]], south: [[x,Y,Z], [x,y,Z], [X,y,Z], [X,Y,Z]],
      west: [[x,Y,z], [x,y,z], [x,y,Z], [x,Y,Z]], east: [[X,Y,Z], [X,y,Z], [X,y,z], [X,Y,z]],
    };
    const defaults: Record<Direction, number[]> = {
      down: [x,16-Z,X,16-z], up: [x,z,X,Z], north: [16-X,16-Y,16-x,16-y],
      south: [x,16-Y,X,16-y], west: [z,16-Y,Z,16-y], east: [16-Z,16-Y,16-z,16-y],
    };
    for (const direction of Object.keys(element.faces)) {
      if (!directions.includes(direction as Direction)) throw new Error('Unsupported model face');
      const face: ModelFace = element.faces[direction as Direction]!;
      const uv = face.uv ?? defaults[direction as Direction];
      if (!Array.isArray(uv) || uv.length !== 4 || uv.some(n => !Number.isFinite(n) || Math.abs(n) > 4096)
        || typeof face.texture !== 'string' || ![0,90,180,270].includes(face.rotation ?? 0)) throw new Error('Invalid model UV');
      const corners = [[uv[0],uv[1]], [uv[0],uv[3]], [uv[2],uv[3]], [uv[2],uv[1]]];
      const shifted = corners.map((_, index) => corners[(index + (face.rotation ?? 0) / 90) % 4]);
      const points = positions[direction as Direction].map(point => rotateElement(point, element.rotation));
      result.push({ positions: points.flat().map(n => n / 16), uv: shifted.flatMap(([u,v]) => [u / 16, 1 - v / 16]),
        texture: face.texture, shade: element.shade !== false });
    }
  }
  if (!result.length) throw new Error('Model has no visible faces');
  return result;
}

function rotateElement(point: ModelVector, rotation: ModelElement['rotation']): ModelVector {
  if (!rotation) return point;
  const origin = vector(rotation.origin), axis = ['x','y','z'].indexOf(rotation.axis);
  if (axis < 0 || !Number.isFinite(rotation.angle) || Math.abs(rotation.angle) > 360) throw new Error('Invalid model rotation');
  const angle = rotation.angle * Math.PI / 180, c = Math.cos(angle), s = Math.sin(angle);
  if (rotation.rescale && Math.abs(c) < .01) throw new Error('Invalid model rescale');
  const p = point.map((n,i) => (n - origin[i]) * (rotation.rescale && i !== axis ? 1 / Math.abs(c) : 1));
  const a = (axis + 1) % 3, b = (axis + 2) % 3;
  const av = p[a], bv = p[b]; p[a] = av * c - bv * s; p[b] = av * s + bv * c;
  return p.map((n,i) => n + origin[i]) as ModelVector;
}
