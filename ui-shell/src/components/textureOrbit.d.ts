export interface TextureOrbitState { zoom: number; view: 'front' | 'top' | 'isometric' | 'custom'; grid: boolean }
export interface TextureOrbitController {
  reset(): void;
  setView(view: 'front' | 'top' | 'isometric'): void;
  orbit(dx: number, dy: number): void;
  pan(dx: number, dy: number): void;
  zoom(factor: number): void;
  toggleGrid(): void;
  dispose(): void;
}
export function createTextureOrbit(canvas: HTMLCanvasElement, pixels: ImageData,
  onChange: (state: TextureOrbitState) => void): TextureOrbitController;
