import { elementVisual } from './elementVisual';

/** The element's identity plate: its type icon on its index-tab tint, identical wherever the element appears. */
export function ElementPlate({ type, size = 17, className }: { type: string; size?: number; className?: string }) {
  const { Icon, tone } = elementVisual(type);
  return <span className={`element-plate tone-${tone}${className ? ` ${className}` : ''}`} aria-hidden="true"><Icon size={size} /></span>;
}
