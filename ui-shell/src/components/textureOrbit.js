// Camera, texel geometry and input behavior adapted from the accepted workbench element preview.
// Only caller-provided ImageData is rendered; this module has no sample geometry or asset loading.
const TAU = Math.PI * 2;
const clamp = (value, low, high) => Math.min(high, Math.max(low, value));
const add = (a, b) => a.map((value, index) => value + b[index]);
const sub = (a, b) => a.map((value, index) => value - b[index]);
const scale = (a, factor) => a.map(value => value * factor);
const dot = (a, b) => a.reduce((sum, value, index) => sum + value * b[index], 0);
  function quad(origin, u, v, normal, color) {
    const vertices = [origin, add(origin, u), add(add(origin, u), v), add(origin, v)];
    return { vertices, normal, color, center: add(origin, scale(add(u, v), 0.5)) };
  }

  function textureGeometry({ width, height, data }) {
    if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1
      || width > 64 || height > 64 || data.length !== width * height * 4) {
      throw new Error('Invalid texture preview dimensions');
    }
    const faces = [], size = 1.7 / Math.max(width, height), depth = 1.7 / 32;
    const pixel = (x, y) => x < 0 || y < 0 || x >= width || y >= height ? null
      : data[(y * width + x) * 4 + 3] ? Array.from(data.slice((y * width + x) * 4, (y * width + x + 1) * 4)) : null;
    const same = (a, b) => b && a.every((channel, index) => channel === b[index]);
    let opaquePixels = 0;
    for (let y = 0; y < height; y++) for (let x = 0; x < width;) {
      const color = pixel(x, y);
      if (!color) { x++; continue; }
      let end = x + 1;
      while (end < width && same(color, pixel(end, y))) end++;
      // Merge only identical adjacent texels; alpha boundaries and every color
      // change remain exact. Internal faces between occupied texels are omitted.
      const left = (x - width / 2) * size, bottom = (height / 2 - y - 1) * size + .88;
      const span = (end - x) * size;
      faces.push(quad([left, bottom, depth], [span, 0, 0], [0, size, 0], [0, 0, 1], color));
      faces.push(quad([left + span, bottom, -depth], [-span, 0, 0], [0, size, 0], [0, 0, -1], color));
      opaquePixels += end - x;
      for (let column = x; column < end; column++) {
        const edgeX = (column - width / 2) * size;
        if (!pixel(column - 1, y)) faces.push(quad([edgeX, bottom, -depth], [0, 0, depth * 2], [0, size, 0], [-1, 0, 0], color));
        if (!pixel(column + 1, y)) faces.push(quad([edgeX + size, bottom, depth], [0, 0, -depth * 2], [0, size, 0], [1, 0, 0], color));
        if (!pixel(column, y - 1)) faces.push(quad([edgeX, bottom + size, depth], [size, 0, 0], [0, 0, -depth * 2], [0, 1, 0], color));
        if (!pixel(column, y + 1)) faces.push(quad([edgeX, bottom, -depth], [size, 0, 0], [0, 0, depth * 2], [0, -1, 0], color));
      }
      x = end;
    }
    if (!opaquePixels) throw new Error('The texture has no visible pixels');
    return faces;
  }

export function createTextureOrbit(canvas, pixels, onChange) {
    const ctx = canvas.getContext('2d', {alpha:true});
    if (!ctx) throw new Error('Canvas is not available');
    const faces = textureGeometry(pixels), textureReady = true;
    const initialDistance = 4.25;
    const state = {yaw:.35,pitch:.2,distance:initialDistance,target:[0,.88,0],grid:true,mode:'orbit',view:'isometric'};
    let width = 0, height = 0, frame = 0, disposed = false;
    const abort = new AbortController(), pointers = new Map();
    const listen = (target, name, handler, options = {}) => target.addEventListener(name,handler,{...options,signal:abort.signal});
    function basis() {
      const sy = Math.sin(state.yaw), cy = Math.cos(state.yaw), sp = Math.sin(state.pitch), cp = Math.cos(state.pitch);
      return { right: [cy, 0, -sy], up: [-sy * sp, cp, -cy * sp], outward: [sy * cp, sp, cy * cp] };
    }
    function updateControls() { onChange({zoom:Math.round(initialDistance / state.distance * 100),view:state.view,grid:state.grid}); }
    function requestDraw() {
      if (disposed || frame) return;
      frame = requestAnimationFrame(() => { frame = 0; draw(); });
    }
    function draw() {
      if (!width || !height || disposed) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      if (canvas.width !== Math.round(width * dpr) || canvas.height !== Math.round(height * dpr)) {
        canvas.width = Math.round(width * dpr); canvas.height = Math.round(height * dpr);
      }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, width, height);
      const camera = basis(), eye = add(state.target, scale(camera.outward, state.distance));
      const focal = Math.min(width, height) * 1.35;
      const centerX = width / 2, centerY = height / 2 + 6;
      const cameraPoint = point => {
        const relative = sub(point, state.target);
        return [dot(relative, camera.right), dot(relative, camera.up), state.distance - dot(relative, camera.outward)];
      };
      const projectCamera = point => [centerX + point[0] * focal / point[2], centerY - point[1] * focal / point[2]];
      const project = point => projectCamera(cameraPoint(point));
      function polygon(points, fill, stroke) {
        ctx.beginPath(); points.forEach((point, index) => index ? ctx.lineTo(...point) : ctx.moveTo(...point)); ctx.closePath();
        ctx.fillStyle = fill; ctx.fill();
        if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = .45; ctx.stroke(); }
      }
      function line(a, b, color, weight = 1) {
        let from = cameraPoint(a), to = cameraPoint(b);
        const near = .2;
        if (from[2] < near && to[2] < near) return;
        if (from[2] < near) from = add(from, scale(sub(to, from), (near - from[2]) / (to[2] - from[2])));
        if (to[2] < near) to = add(to, scale(sub(from, to), (near - to[2]) / (from[2] - to[2])));
        ctx.beginPath(); ctx.moveTo(...projectCamera(from)); ctx.lineTo(...projectCamera(to));
        ctx.strokeStyle = color; ctx.lineWidth = weight; ctx.stroke();
      }
      const styles = getComputedStyle(canvas);
      if (state.grid) {
        const gridColor = styles.getPropertyValue('--border-subtle').trim() || '#e1e3dd';
        for (let tick = -6; tick <= 6; tick++) {
          const value = tick * .5;
          if (tick) { line([-3, 0, value], [3, 0, value], gridColor); line([value, 0, -3], [value, 0, 3], gridColor); }
        }
        line([-3, 0, 0], [3, 0, 0], '#b5706270');
        line([0, 0, -3], [0, 0, 3], '#6788ae70');
      }
      for (let layer = faces.length ? 5 : 0; layer >= 1; layer--) {
        const radius = .4 + layer * .1;
        const shadow = Array.from({ length: 40 }, (_, index) => [Math.cos(index / 40 * TAU) * radius, .002, Math.sin(index / 40 * TAU) * radius * .78]);
        if (shadow.every(point => cameraPoint(point)[2] > .2)) polygon(shadow.map(project), 'rgba(41,35,27,.026)');
      }
      const visible = faces.filter(face => dot(face.normal, sub(eye, face.center)) > 0)
        .map(face => ({ face, depth: cameraPoint(face.center)[2] })).sort((a, b) => b.depth - a.depth);
      visible.forEach(({ face }) => {
        if (face.vertices.some(point => cameraPoint(point)[2] < .2)) return;
        const light = .7 + .3 * Math.max(0, dot(face.normal, [-.36, .8, .48]));
        const alpha = (face.color[3] ?? 255) / 255;
        const color = `rgba(${face.color.slice(0, 3).map(channel => Math.round(channel * light)).join(',')},${alpha})`;
        polygon(face.vertices.map(project), color, alpha === 1 ? color : null);
      });
      // A screen-space orientation triad follows the actual camera basis.
      const axes = [{ vector: [1, 0, 0], label: 'X', color: '#b96353' }, { vector: [0, 1, 0], label: 'Y', color: '#649066' }, { vector: [0, 0, 1], label: 'Z', color: '#6288ad' }];
      const anchor = [42, height - 40];
      axes.sort((a, b) => dot(a.vector, camera.outward) - dot(b.vector, camera.outward)).forEach(axis => {
        const end = [anchor[0] + dot(axis.vector, camera.right) * 24, anchor[1] - dot(axis.vector, camera.up) * 24];
        ctx.beginPath(); ctx.moveTo(...anchor); ctx.lineTo(...end); ctx.strokeStyle = axis.color; ctx.lineWidth = 1.5; ctx.stroke();
        ctx.beginPath(); ctx.arc(...end, 7, 0, TAU); ctx.fillStyle = styles.getPropertyValue('--bg-base').trim() || '#fff'; ctx.fill();
        ctx.fillStyle = axis.color; ctx.font = '600 10px "Segoe UI", sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(axis.label, ...end);
      });
      updateControls();
    }

    function orbit(dx, dy) {
      state.yaw = (state.yaw - dx * .009) % TAU;
      state.pitch = clamp(state.pitch + dy * .009, -1.48, 1.56);
      state.view = 'custom'; requestDraw();
    }
    function pan(dx, dy) {
      const camera = basis(), factor = state.distance / Math.max(1, Math.min(width, height) * 1.35);
      state.target = add(state.target, add(scale(camera.right, -dx * factor), scale(camera.up, dy * factor)));
      requestDraw();
    }
    function zoom(factor) { state.distance = clamp(state.distance * factor, 2.1, 10); requestDraw(); }
    function setView(view) {
      if (!['front', 'top', 'isometric'].includes(view)) return;
      state.view = view;
      state.yaw = view === 'isometric' ? .35 : 0;
      state.pitch = view === 'top' ? Math.PI / 2 - .001 : view === 'front' ? 0 : .2;
      state.target = [0, .88, 0];
      requestDraw();
    }
    function reset() {
      state.distance = initialDistance; setView('isometric');
    }
    const pointerPair = () => {
      const [a, b] = [...pointers.values()];
      return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, distance: Math.hypot(a.x - b.x, a.y - b.y) };
    };
    listen(canvas, 'pointerdown', event => {
      if (!textureReady || event.button !== 0 && event.button !== 1) return;
      event.preventDefault(); canvas.focus({ preventScroll: true });
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY, pan: event.button === 1 || event.shiftKey });
      canvas.setPointerCapture(event.pointerId); canvas.classList.add('is-dragging');
    });
    listen(canvas, 'pointermove', event => {
      const previous = pointers.get(event.pointerId); if (!previous) return;
      const pairBefore = pointers.size >= 2 ? pointerPair() : null;
      pointers.set(event.pointerId, { ...previous, x: event.clientX, y: event.clientY });
      if (pairBefore) {
        const after = pointerPair(); pan(after.x - pairBefore.x, after.y - pairBefore.y);
        if (pairBefore.distance > 5 && after.distance > 5) zoom(pairBefore.distance / after.distance);
      } else {
        const dx = event.clientX - previous.x, dy = event.clientY - previous.y;
        if (previous.pan || event.shiftKey || state.mode === 'pan') pan(dx, dy); else orbit(dx, dy);
      }
    });
    const endPointer = event => {
      pointers.delete(event.pointerId);
      if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
      if (!pointers.size) canvas.classList.remove('is-dragging');
    };
    listen(canvas, 'pointerup', endPointer); listen(canvas, 'pointercancel', endPointer); listen(canvas, 'lostpointercapture', endPointer);
    listen(canvas, 'wheel', event => {
      if (!textureReady) return;
      event.preventDefault();
      const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? height : 1);
      zoom(Math.exp(clamp(delta, -300, 300) * .0018));
    }, { passive: false });
    listen(canvas, 'dblclick', reset);
    listen(canvas, 'keydown', event => {
      if (!textureReady || event.ctrlKey || event.metaKey || event.altKey) return;
      const directions = { ArrowLeft: [18, 0], ArrowRight: [-18, 0], ArrowUp: [0, 18], ArrowDown: [0, -18] };
      if (directions[event.key]) (event.shiftKey ? pan : orbit)(...directions[event.key]);
      else if (event.key === '+' || event.key === '=') zoom(.86);
      else if (event.key === '-' || event.key === '_') zoom(1 / .86);
      else if (event.key === 'Home') reset();
      else return;
      event.preventDefault(); event.stopPropagation();
    });
    const resize = new ResizeObserver(entries => {
      const { width: nextWidth, height: nextHeight } = entries[0].contentRect;
      width = nextWidth; height = nextHeight; requestDraw();
    });
    resize.observe(canvas);
    const theme = new MutationObserver(requestDraw);
    theme.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] });
    const controller = {
      reset, setView, orbit, pan, zoom, toggleGrid() { state.grid = !state.grid; requestDraw(); },
      dispose() {
        if (disposed) return;
        disposed = true; abort.abort(); resize.disconnect(); theme.disconnect();
        if (frame) cancelAnimationFrame(frame);
        for (const pointerId of pointers.keys()) if (canvas.hasPointerCapture(pointerId)) canvas.releasePointerCapture(pointerId);
        pointers.clear();
      }
    };
    requestDraw(); return controller;
}
