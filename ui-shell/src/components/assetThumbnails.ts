import { useEffect, useState } from 'react';
import { getAssetPreview } from '../bridge/assetPreviewBridge';
import type { AssetProjectionAsset } from '../types/contract';

type ThumbnailAsset = Pick<AssetProjectionAsset, 'id' | 'sha256'>;

const MAX_CONCURRENT = 4, MAX_CACHED = 400;
const cache = new Map<string, Promise<string | null>>();
const waiting: Array<() => void> = [];
let running = 0;

function schedule<T>(task: () => Promise<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const run = () => {
      running++;
      task().then(resolve, reject).finally(() => { running--; waiting.shift()?.(); });
    };
    if (running < MAX_CONCURRENT) run(); else waiting.push(run);
  });
}

/**
 * A data URL for an image asset, read through the same Core preview query as the asset browser. Results are cached
 * per content hash; a failed read is not cached, so reopening the picker retries it. `null` means no image to show.
 */
export function loadAssetThumbnail(workspaceId: string, asset: ThumbnailAsset): Promise<string | null> {
  const key = `${workspaceId}:${asset.id}:${asset.sha256}`;
  const cached = cache.get(key);
  if (cached) return cached;
  const pending = schedule(() => getAssetPreview(workspaceId, asset.id, asset.sha256)).then(preview => {
    const image = preview.kind === 'image' ? preview.image : undefined;
    return image?.base64 && ['image/png', 'image/jpeg'].includes(image.mediaType) && !image.reason
      ? `data:${image.mediaType};base64,${image.base64}` : null;
  }, () => { cache.delete(key); return null; });
  cache.set(key, pending);
  if (cache.size > MAX_CACHED) cache.delete(cache.keys().next().value!);
  return pending;
}

/** `undefined` while loading, `null` when there is nothing to show. */
export function useAssetThumbnail(workspaceId: string | undefined, asset: ThumbnailAsset | undefined): string | null | undefined {
  const [url, setUrl] = useState<string | null | undefined>(undefined);
  useEffect(() => {
    if (!workspaceId || !asset?.sha256) { setUrl(null); return; }
    let cancelled = false;
    setUrl(undefined);
    void loadAssetThumbnail(workspaceId, asset).then(value => { if (!cancelled) setUrl(value); });
    return () => { cancelled = true; };
  }, [workspaceId, asset?.id, asset?.sha256]);
  return url;
}
