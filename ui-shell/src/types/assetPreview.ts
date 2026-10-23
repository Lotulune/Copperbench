export interface AssetPreviewImage {
  assetId: string;
  relativePath: string;
  sha256: string;
  mediaType: string;
  size: number;
  base64?: string;
  width?: number;
  height?: number;
  reason?: string;
  sourcePointer?: string;
  rawValue?: string;
  frame?: { x: number; y: number; width: number; height: number };
}

export interface AssetPreviewProjection extends AssetPreviewImage {
  schemaVersion: '1.0';
  kind: 'image' | 'model_json' | 'model' | 'unsupported';
  image?: AssetPreviewImage;
  document?: string;
  textures: AssetPreviewImage[];
  linkedTextureCount?: number;
  scene?: ModelPreviewScene;
}

export type ModelVector = [number, number, number];
export interface ModelFace { texture: string; uv?: [number, number, number, number]; rotation?: number; tintindex?: number }
export interface ModelElement {
  from: ModelVector;
  to: ModelVector;
  faces: Partial<Record<'down' | 'up' | 'north' | 'south' | 'west' | 'east', ModelFace>>;
  rotation?: { origin: ModelVector; axis: 'x' | 'y' | 'z'; angle: number; rescale?: boolean };
  shade?: boolean;
}
export interface ModelPreviewScene {
  kind: 'model' | 'item' | 'mesh' | 'unavailable';
  meshes?: ModelMesh[];
  format?: 'obj' | 'bbmodel' | 'java';
  defaultPose?: boolean;
  untextured?: boolean;
  reason?: string;
  detail?: string;
  elements?: ModelElement[];
  textures?: Record<string, AssetPreviewImage>;
  layers?: string[];
  missingTextures?: string[];
  rotationX?: number;
  fingerprint?: string;
}

export interface ModelMesh {
  positions: number[];
  uv: number[];
  normals: number[];
  texture?: string;
  color: [number, number, number];
  opacity: number;
  doubleSided: boolean;
  repeatTexture?: boolean;
}
