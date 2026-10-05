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
}

export interface AssetPreviewProjection extends AssetPreviewImage {
  schemaVersion: '1.0';
  kind: 'image' | 'model_json' | 'unsupported';
  image?: AssetPreviewImage;
  document?: string;
  textures: AssetPreviewImage[];
  linkedTextureCount?: number;
}
