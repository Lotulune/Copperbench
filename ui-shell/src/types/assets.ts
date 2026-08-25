import type { AssetProjection } from './contract';

export type AssetCategory =
  | 'model'
  | 'texture'
  | 'animation'
  | 'language'
  | 'sound'
  | 'resource_pack'
  | 'blockstate'
  | 'other';

export type AssetValidationStatus = 'ready' | 'draft' | 'warning' | 'error';

export interface AssetRecord {
  readonly id: string;
  readonly name: string;
  readonly category: AssetCategory;
  readonly categoryLabel: string;
  readonly path: string;
  readonly format: string;
  readonly size: string;
  readonly sizeBytes: number;
  readonly dimensions?: string;
  readonly updatedAt?: string;
  readonly source: 'workspace' | 'minecraft' | 'blockbench';
  readonly sourceLabel: string;
  readonly references: readonly string[];
  readonly validation: AssetValidationStatus;
  readonly validationLabel: string;
  readonly description: string;
  readonly sha256?: string;
}

const CATEGORY_LABELS: Record<AssetCategory, string> = {
  model: '模型',
  texture: '纹理',
  animation: '动画',
  language: '语言',
  sound: '声音',
  resource_pack: '资源包',
  blockstate: '方块状态',
  other: '其他'
};

function categoryFromProjection(category: string): AssetCategory {
  return category.toLowerCase() as AssetCategory;
}

function assetName(path: string): string {
  const file = path.split('/').pop() ?? path;
  return file.replace(/\.[^.]+$/, '');
}

function assetFormat(path: string): string {
  const file = path.split('/').pop() ?? path;
  const extension = file.includes('.') ? file.slice(file.lastIndexOf('.') + 1) : '';
  return extension ? extension.toUpperCase() : 'FILE';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function validationFor(path: string, projection: AssetProjection): Pick<AssetRecord, 'validation' | 'validationLabel'> {
  const diagnostics = projection.diagnostics.filter(
    (diagnostic) => diagnostic.sourcePath === path || diagnostic.targetPath === path
  );
  if (diagnostics.some((diagnostic) => diagnostic.severity === 'ERROR')) {
    return { validation: 'error', validationLabel: '有错误' };
  }
  if (diagnostics.some((diagnostic) => diagnostic.severity === 'WARNING')) {
    return { validation: 'warning', validationLabel: '需检查' };
  }
  return { validation: 'ready', validationLabel: '已校验' };
}

/** Converts the wire projection into the richer view model used by the browser. */
export function assetRecordsFromProjection(projection: AssetProjection): AssetRecord[] {
  return projection.assets.map((asset) => {
    const category = categoryFromProjection(asset.category);
    const references = projection.references
      .filter((reference) => reference.targetAssetId === asset.id)
      .map((reference) => reference.sourcePath);
    const validation = validationFor(asset.relativePath, projection);
    return {
      id: asset.id,
      name: assetName(asset.relativePath),
      category,
      categoryLabel: CATEGORY_LABELS[category] ?? category,
      path: asset.relativePath,
      format: assetFormat(asset.relativePath),
      size: formatBytes(asset.size),
      sizeBytes: asset.size,
      updatedAt: asset.updatedAt,
      source: 'workspace',
      sourceLabel: '工作区',
      references,
      ...validation,
      description: '工作区真实资产，由 AssetWorkspaceService 实时索引。',
      sha256: asset.sha256
    };
  });
}
