import { coreBridge } from './index';
import { safeRandomUUID } from './JcefCoreBridge';
import { t, uiText } from '../i18n';
import type { AssetPreviewProjection } from '../types/assetPreview';

export async function getAssetPreview(workspaceId: string, assetId: string, expectedSha256: string): Promise<AssetPreviewProjection> {
  const result = await coreBridge.sendQuery<AssetPreviewProjection>({ messageType: 'query', schemaVersion: '1.0',
    requestId: safeRandomUUID(), workspaceId, operation: 'get_asset_preview', payload: { assetId, expectedSha256 } });
  if (result.workspaceId !== workspaceId || result.status !== 'succeeded' || !result.data) {
    throw new Error(result.diagnostics.map(diagnostic => [t(diagnostic.message), diagnostic.message.args?.detail].filter(Boolean).join(' ')).join('\n')
      || uiText('无法读取资产预览。请刷新资产列表后重试。', 'Could not load the asset preview. Refresh the asset list and try again.'));
  }
  if (result.data.schemaVersion !== '1.0' || result.data.assetId !== assetId || result.data.sha256 !== expectedSha256)
    throw new Error(uiText('资产已更改，请刷新资产列表。', 'The asset changed. Refresh the asset list.'));
  return result.data;
}
