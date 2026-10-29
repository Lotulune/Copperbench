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
      const detail = diagnostic.message.args?.detail;
      return [t(diagnostic.message), typeof detail === 'string' ? detail : ''].filter(Boolean).join(' ');
    }).join('\n') : renderUiMessage(failure.fallback);
  }
  return failure instanceof Error ? failure.message : String(failure);
}

export function operationDiagnostics(failure: unknown): Diagnostic[] {
  return failure instanceof OperationFailure ? failure.diagnostics : [];
}
