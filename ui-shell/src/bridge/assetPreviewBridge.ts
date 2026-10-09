import { coreBridge } from './index';
import { safeRandomUUID } from './JcefCoreBridge';
import { t, uiMessage, renderUiMessage, type UiMessage } from '../i18n';
import type { AssetPreviewProjection } from '../types/assetPreview';
import type { Diagnostic } from '../types/contract';

class AssetPreviewFailure extends Error {
  constructor(readonly diagnostics: readonly Diagnostic[], readonly fallback: UiMessage) {
    super(renderFailure(diagnostics, fallback));
  }
}

function renderFailure(diagnostics: readonly Diagnostic[], fallback: UiMessage): string {
  return diagnostics.map(diagnostic => {
    const text = t(diagnostic.message, diagnostic.code);
    const detail = diagnostic.message?.args?.detail;
    return typeof detail === 'string' && detail && !text.includes(detail) ? `${text} ${detail}` : text;
  }).join('\n') || renderUiMessage(fallback);
}

/** Re-render retained Core diagnostics without issuing another preview request. */
export function renderAssetPreviewFailure(failure: unknown): string {
  return failure instanceof AssetPreviewFailure ? renderFailure(failure.diagnostics, failure.fallback)
    : failure instanceof Error ? failure.message : String(failure);
}

export async function getAssetPreview(workspaceId: string, assetId: string, expectedSha256: string): Promise<AssetPreviewProjection> {
  const result = await coreBridge.sendQuery<AssetPreviewProjection>({ messageType: 'query', schemaVersion: '1.0',
    requestId: safeRandomUUID(), workspaceId, operation: 'get_asset_preview', payload: { assetId, expectedSha256 } });
  if (result.workspaceId !== workspaceId || result.status !== 'succeeded' || !result.data) {
    throw new AssetPreviewFailure(result.diagnostics,
      uiMessage('无法读取资产预览。请刷新资产列表后重试。', 'Could not load the asset preview. Refresh the asset list and try again.'));
  }
  if (result.data.schemaVersion !== '1.0' || result.data.assetId !== assetId || result.data.sha256 !== expectedSha256)
    throw new AssetPreviewFailure([], uiMessage('资产已更改，请刷新资产列表。', 'The asset changed. Refresh the asset list.'));
  return result.data;
}
