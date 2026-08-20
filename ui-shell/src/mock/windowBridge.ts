/**
 * Window bridge stub (U1).
 *
 * The real native window bridge (drag, eight-way resize, Snap Layout,
 * minimize/maximize/close) is owned by the Java host and lands in stage 4.
 * Until then the product shell talks to this interface-only stub so the
 * call sites keep the final API shape (NFR-UI-05/06).
 */
export interface WindowBridge {
  minimize(): void;
  toggleMaximize(): void;
  close(): void;
}

class MockWindowBridge implements WindowBridge {
  public lastAction: string | null = null;

  minimize(): void {
    this.lastAction = 'minimize';
  }

  toggleMaximize(): void {
    this.lastAction = 'toggle_maximize';
  }

  close(): void {
    this.lastAction = 'close';
  }
}

export const windowBridge: WindowBridge = new MockWindowBridge();
