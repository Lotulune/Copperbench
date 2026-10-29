import { uiText } from '../i18n';
import { receiptCompatibility } from '../onboarding/firstCreationModel';

/** Uses only recorded artifact metadata; an unknown historical environment stays unknown. */
export function ExportCompatibility({ environment }: { environment: unknown }) {
  const compatibility = receiptCompatibility(environment);
  const unknown = uiText('回执未记录', 'Not recorded in receipt');
  return <dl className="delivery-facts" aria-label={uiText('此次产物的兼容范围', 'Compatibility of this artifact')} data-testid="export-receipt-compatibility">
    <div><dt>{uiText('产物加载器', 'Artifact loader')}</dt><dd>{compatibility.loader ?? unknown}</dd></div>
    <div><dt>{uiText('产物 Minecraft 版本', 'Artifact Minecraft version')}</dt><dd>{compatibility.minecraftVersion ?? unknown}</dd></div>
    <div><dt>{uiText('加载器版本', 'Loader version')}</dt><dd>{compatibility.loaderVersion ?? unknown}</dd></div>
  </dl>;
}
