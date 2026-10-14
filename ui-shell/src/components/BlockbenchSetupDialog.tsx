import { X } from 'lucide-react';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { uiText } from '../i18n';
import { BlockbenchSetupPanel } from './BlockbenchSetupPanel';

export function BlockbenchSetupDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const dialogRef = useDialogA11y(open, onClose);
  if (!open) return null;
  return <div className="modal-overlay" data-testid="blockbench-setup-dialog">
    <div ref={dialogRef} className="modal-card blockbench-setup-dialog" role="dialog" aria-modal="true"
      aria-label={uiText('Blockbench 设置', 'Blockbench setup')}>
      <div className="modal-header">
        <h2>{uiText('Blockbench 设置', 'Blockbench setup')}</h2>
        <button type="button" onClick={onClose} aria-label={uiText('关闭 Blockbench 设置', 'Close Blockbench setup')}><X size={16} /></button>
      </div>
      <BlockbenchSetupPanel embedded />
    </div>
  </div>;
}
