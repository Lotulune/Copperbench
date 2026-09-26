import { uiText } from '../i18n/locale';
import type { AssetProjection, AssetProjectionReference } from './contract';

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
  readonly outgoingReferences?: readonly string[];
  readonly outgoingResolution?: readonly AssetProjectionReference[];
  readonly usageAssessed?: boolean;
  readonly unused?: boolean;
  readonly workspaceReferenceCount?: number;
  readonly cleanupAssessed?: boolean;
  readonly safeUnused?: boolean;
  readonly issueCodes?: readonly string[];
  readonly inboundCount?: number;
  readonly outboundCount?: number;
  readonly duplicateContent?: boolean;
  readonly duplicatePaths?: readonly string[];
  readonly validation: AssetValidationStatus;
  readonly validationLabel: string;
  readonly description: string;
  readonly sha256?: string;
}

const categoryLabels = (): Record<AssetCategory, string> => ({
  model: uiText("模型", "Model"),
  texture: uiText("纹理", "Texture"),
  animation: uiText("动画", "Animation"),
  language: uiText("语言", "Language"),
  sound: uiText("声音", "Sound"),
  resource_pack: uiText("资源包", "Resource packs"),
  blockstate: uiText("方块状态", "Block states"),
  other: uiText("其他", "Other")
});

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

function validationFor(asset: AssetProjection['assets'][number]): Pick<AssetRecord, 'validation' | 'validationLabel'> {
  if (asset.health.status === 'ERROR') {
    return { validation: 'error', validationLabel: uiText("有错误", "Has errors") };
  }
  if (asset.health.status === 'WARNING') {
    return { validation: 'warning', validationLabel: uiText("警告", "Warnings") };
  }
  return { validation: 'ready', validationLabel: uiText("已校验", "Validated") };
}

/** Converts the wire projection into the richer view model used by the browser. */
export function assetRecordsFromProjection(projection: AssetProjection): AssetRecord[] {
  return projection.assets.map((asset) => {
    const category = categoryFromProjection(asset.category);
    const references = projection.references
      .filter((reference) => reference.targetAssetId === asset.id)
      .map((reference) => reference.sourcePath);
    const outgoingReferences = projection.references
      .filter((reference) => reference.sourceAssetId === asset.id)
      .map((reference) => reference.targetPath);
    const validation = validationFor(asset);
    return {
      id: asset.id,
      name: assetName(asset.relativePath),
      category,
      categoryLabel: categoryLabels()[category] ?? category,
      path: asset.relativePath,
      format: assetFormat(asset.relativePath),
      size: formatBytes(asset.size),
      sizeBytes: asset.size,
      updatedAt: asset.updatedAt,
      source: 'workspace',
      sourceLabel: uiText("工作区", "Workspace"),
      references,
      outgoingReferences,
      outgoingResolution: projection.references.filter(reference => reference.sourceAssetId === asset.id),
      usageAssessed: asset.health.usageAssessed,
      unused: asset.health.unused,
      workspaceReferenceCount: asset.health.workspaceReferenceCount,
      cleanupAssessed: asset.health.cleanupAssessed,
      safeUnused: asset.health.safeUnused,
      issueCodes: asset.health.issueCodes,
      inboundCount: asset.health.inboundCount,
      outboundCount: asset.health.outboundCount,
      duplicateContent: asset.health.duplicateContent,
      duplicatePaths: asset.health.duplicatePaths,
      ...validation,
      description: uiText("工作区真实资产，由 AssetWorkspaceService 实时索引。", "Workspace asset indexed by AssetWorkspaceService."),
      sha256: asset.sha256
    };
  });
}
