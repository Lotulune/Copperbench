import { renderUiMessage, t, type UiMessage } from '../i18n';
import type { Diagnostic } from '../types/contract';

/** Keep the receipt intact; choosing a display language belongs to rendering. */
export class OperationFailure extends Error {
  constructor(public readonly diagnostics: Diagnostic[], public readonly fallback: UiMessage) {
    super('Core operation did not succeed');
    this.name = 'OperationFailure';
  }
}

export function operationFailureText(failure: unknown): string {
  if (failure instanceof OperationFailure) {
    return failure.diagnostics.length ? failure.diagnostics.map(diagnostic => {
      const text = t(diagnostic.message, diagnostic.code);
      const detail = diagnostic.message.args?.detail;
      return typeof detail === 'string' && detail && !text.includes(detail) ? `${text} ${detail}` : text;
    }).join('\n') : renderUiMessage(failure.fallback);
  }
  return failure instanceof Error ? failure.message : String(failure);
}

export function operationDiagnostics(failure: unknown): Diagnostic[] {
  return failure instanceof OperationFailure ? failure.diagnostics : [];
}
