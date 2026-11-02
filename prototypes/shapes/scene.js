// Renders the voxel scenes of shapes.js with three.js and binds the controls of index.html.
(function () {
  'use strict';

  const ROLES = {
    interior: { color: '#8c8f96', label: 'Interior' },
    wall: { color: '#d8c8a0', label: 'Maze wall' },
    path: { color: '#3f78d8', label: 'Maze path (groove floor)' },
    surfacePath: { color: '#7fd6f0', label: 'Surface path (flush)' },
    trussRed: { color: '#d0473c', label: 'Truss, red diagonal' },
    trussGreen: { color: '#4fae4f', label: 'Truss, green diagonal' },
    junction: { color: '#8a6fbf', label: 'Junction box' },
    bezel: { color: '#e39b2f', label: 'Bezel' },
  };
  const HOLE_COLOR = '#3fe0d0';
  const NODE_COLOR = '#ff3fd2';
  const CORNER_COLOR = '#ffffff';
  const RAMP = ['#fde725', '#5ec962', '#21918c', '#3b528b', '#440154'];

  const view = document.getElementById('view');
  const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(window.devicePixelRatio || 1);
  view.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.background = new THREE.Color('#23272e');
  scene.add(new THREE.HemisphereLight(0xffffff, 0x50505a, 0.75));
  const sun = new THREE.DirectionalLight(0xffffff, 0.55);
  sun.position.set(0.6, 1.0, 0.8);
  scene.add(sun);
  const fill = new THREE.DirectionalLight(0xffffff, 0.2);
  fill.position.set(-0.7, -0.3, -0.5);
  scene.add(fill);

  const persp = new THREE.PerspectiveCamera(40, 1, 0.1, 5000);
  const ortho = new THREE.OrthographicCamera(-1, 1, 1, -1, -5000, 5000);
  let camera = persp;
  let controls = null;

  const content = new THREE.Group();
  scene.add(content);

  // A block face texture with a dark border, so single blocks read in a flat face.
  const faceTexture = (() => {
    const c = document.createElement('canvas');
    c.width = c.height = 32;
    const g = c.getContext('2d');
    g.fillStyle = '#ffffff';
    g.fillRect(0, 0, 32, 32);
    g.strokeStyle = 'rgba(0,0,0,0.28)';
    g.lineWidth = 2;
    g.strokeRect(1, 1, 30, 30);
    const t = new THREE.CanvasTexture(c);
    t.magFilter = THREE.LinearFilter;
    t.anisotropy = 4;
    return t;
  })();

  const els = {};
  ['scene', 'h', 'c', 'pattern', 'lattice', 'res', 'cut', 'chunks', 'band', 'ortho', 'chunksOut', 'bandOut', 'legend', 'info', 'stats']
    .forEach((id) => { els[id] = document.getElementById(id); });

  const state = {
    scene: 'junction', h: 'bridge', c: 'ibeam', webPlane: 'x', pattern: 'serpentine',
    lattice: false, res: false, cut: false, labels: true, chunks: 2, band: 16, ortho: false, view: 'iso', cam: null,
  };

  // ------------------------------------------------------------ hash state

  function readHash() {
    const q = new URLSearchParams(location.hash.slice(1));
    for (const [k, v] of q) {
      if (!(k in state)) continue;
      if (typeof state[k] === 'boolean') state[k] = v === '1';
      else if (typeof state[k] === 'number') state[k] = Number(v);
      else if (k === 'cam') state.cam = v.split(',').map(Number);
      else state[k] = v;
    }
  }

  function writeHash() {
    const q = new URLSearchParams();
    for (const k of Object.keys(state)) {
      if (k === 'cam') continue;
      const v = state[k];
      q.set(k, typeof v === 'boolean' ? (v ? '1' : '0') : String(v));
    }
    history.replaceState(null, '', '#' + q.toString());
  }

  function syncControls() {
    els.scene.value = state.scene;
    els.h.value = state.h;
    els.c.value = state.c;
    els.pattern.value = state.pattern;
    els.lattice.checked = state.lattice;
    els.res.checked = state.res;
    els.cut.checked = state.cut;
    els.chunks.value = state.chunks;
    els.band.value = state.band;
    els.ortho.checked = state.ortho;
    els.chunksOut.textContent = state.chunks;
    els.bandOut.textContent = state.band;
    document.querySelectorAll('input[name=plane]').forEach((r) => { r.checked = r.value === state.webPlane; });
  }

  // ------------------------------------------------------------ build

  let bounds = new THREE.Box3();

  function clearContent() {
    for (const o of [...content.children]) {
      content.remove(o);
      o.traverse((n) => {
        if (n.geometry) n.geometry.dispose();
        if (n.material && n.material !== blockMaterial) {
          if (n.material.map && n.material.map !== faceTexture) n.material.map.dispose();
          n.material.dispose();
        }
      });
    }
  }

  const blockMaterial = new THREE.MeshLambertMaterial({ map: faceTexture });

  function rampColor(t) {
    const x = Math.max(0, Math.min(1, t)) * (RAMP.length - 1);
    const i = Math.min(RAMP.length - 2, Math.floor(x));
    return new THREE.Color(RAMP[i]).lerp(new THREE.Color(RAMP[i + 1]), x - i);
  }

  function rebuild(refit) {
    const t0 = performance.now();
    const vox = Shapes.build(state);
    clearContent();

    const visible = [];
    for (const b of vox.map.values()) {
      if (!vox.has(b.x + 1, b.y, b.z) || !vox.has(b.x - 1, b.y, b.z) || !vox.has(b.x, b.y + 1, b.z) ||
          !vox.has(b.x, b.y - 1, b.z) || !vox.has(b.x, b.y, b.z + 1) || !vox.has(b.x, b.y, b.z - 1)) visible.push(b);
    }
    const colors = {};
    for (const k of Object.keys(ROLES)) colors[k] = new THREE.Color(ROLES[k].color);
    const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), blockMaterial, Math.max(1, visible.length));
    const m = new THREE.Matrix4();
    bounds = new THREE.Box3();
    const p = new THREE.Vector3();
    visible.forEach((b, i) => {
      m.makeTranslation(b.x, b.y, b.z);
      mesh.setMatrixAt(i, m);
      mesh.setColorAt(i, state.res ? rampColor(b.res) : colors[b.role]);
      bounds.expandByPoint(p.set(b.x, b.y, b.z));
    });
    mesh.count = visible.length;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    content.add(mesh);

    if (state.lattice && vox.markers.length) {
      const mm = new THREE.InstancedMesh(new THREE.BoxGeometry(0.34, 0.34, 0.34), new THREE.MeshBasicMaterial(), vox.markers.length);
      const node = new THREE.Color(NODE_COLOR), corner = new THREE.Color(CORNER_COLOR);
      vox.markers.forEach((k, i) => {
        m.makeTranslation(k.x, k.y, k.z);
        mm.setMatrixAt(i, m);
        mm.setColorAt(i, k.corner ? corner : node);
      });
      content.add(mm);
    }

    for (const h of vox.holes) {
      const g = new THREE.BoxGeometry(h.x1 - h.x0, h.y1 - h.y0, h.z1 - h.z0);
      const c = new THREE.Vector3((h.x0 + h.x1) / 2, (h.y0 + h.y1) / 2, (h.z0 + h.z1) / 2);
      const fillMesh = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color: HOLE_COLOR, transparent: true, opacity: 0.12, depthWrite: false }));
      fillMesh.position.copy(c);
      const lines = new THREE.LineSegments(new THREE.EdgesGeometry(g), new THREE.LineBasicMaterial({ color: HOLE_COLOR }));
      lines.position.copy(c);
      content.add(fillMesh, lines);
    }

    for (const l of state.labels ? vox.labels : []) {
      content.add(label(l.text, l.x, l.y, l.z));
      bounds.expandByPoint(p.set(l.x, l.y + 2, l.z));
    }

    els.stats.textContent = vox.map.size + ' blocks, ' + visible.length + ' drawn, ' + vox.markers.length + ' nodes, ' + Math.round(performance.now() - t0) + ' ms';
    legend();
    info();
    writeHash();
    if (refit) fit(state.view);
  }

  function label(text, x, y, z) {
    const c = document.createElement('canvas');
    const g = c.getContext('2d');
    const font = '600 44px -apple-system, Helvetica, Arial, sans-serif';
    g.font = font;
    c.width = Math.ceil(g.measureText(text).width) + 24;
    c.height = 64;
    g.font = font;
    g.fillStyle = 'rgba(20,22,26,0.75)';
    g.fillRect(0, 0, c.width, c.height);
    g.fillStyle = '#ffffff';
    g.textBaseline = 'middle';
    g.fillText(text, 12, 34);
    const t = new THREE.CanvasTexture(c);
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, depthTest: false }));
    s.position.set(x, y, z);
    s.scale.set(c.width / 24, c.height / 24, 1);
    s.renderOrder = 10;
    return s;
  }

  // ------------------------------------------------------------ camera

  const VIEWS = {
    iso: [1, 0.75, 1.3],
    side: [0, 0, 1],
    end: [1, 0, 0],
    top: [0, 1, 0.0001],
  };

  function makeControls() {
    if (controls) controls.dispose();
    controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = false;
    controls.addEventListener('change', render);
  }

  function fit(name) {
    state.view = name;
    const size = new THREE.Vector3();
    const center = new THREE.Vector3();
    bounds.getSize(size);
    bounds.getCenter(center);
    const radius = Math.max(8, size.length() / 2 + 1);
    const dir = new THREE.Vector3(...VIEWS[name]).normalize();
    const aspect = view.clientWidth / Math.max(1, view.clientHeight);
    persp.position.copy(center).addScaledVector(dir, radius / Math.sin(THREE.MathUtils.degToRad(persp.fov / 2)) * 0.9);
    ortho.position.copy(center).addScaledVector(dir, radius * 4);
    const h = radius * 1.05;
    ortho.left = -h * aspect; ortho.right = h * aspect; ortho.top = h; ortho.bottom = -h;
    ortho.zoom = 1;
    ortho.updateProjectionMatrix();
    if (state.cam) {
      const [px, py, pz, tx, ty, tz, zoom] = state.cam;
      camera.position.set(px, py, pz);
      center.set(tx, ty, tz);
      if (zoom) {
        ortho.zoom = zoom;
        ortho.updateProjectionMatrix();
      }
      state.cam = null;
    }
    camera.lookAt(center);
    makeControls();
    controls.target.copy(center);
    controls.update();
    render();
  }

  function resize() {
    const w = view.clientWidth, h = view.clientHeight;
    renderer.setSize(w, h);
    persp.aspect = w / Math.max(1, h);
    persp.updateProjectionMatrix();
    const half = (ortho.top - ortho.bottom) / 2;
    ortho.left = -half * persp.aspect; ortho.right = half * persp.aspect;
    ortho.updateProjectionMatrix();
    render();
  }

  function render() { renderer.render(scene, camera); }

  // ------------------------------------------------------------ panel text

  function legend() {
    let html = '';
    if (state.res) {
      html += '<div class="ramp" style="background:linear-gradient(90deg,' + RAMP.join(',') + ')"></div>';
      html += '<div class="ramp-labels"><span>0 surface</span><span>0.5</span><span>1 never erodes</span></div>';
      html += '<p class="note">Trusses take ' + Shapes.constants.TRUSS_RESISTANCE + '. The box takes the largest of the box value and each pillar value.</p>';
    } else {
      for (const k of Object.keys(ROLES)) html += swatch(ROLES[k].color, ROLES[k].label);
    }
    html += swatch(HOLE_COLOR, 'Hole (bridge through a box)', true);
    if (state.lattice) {
      html += swatch(NODE_COLOR, 'Node');
      html += swatch(CORNER_COLOR, 'Corner node (no axis edge)');
    }
    els.legend.innerHTML = html;
  }

  function swatch(color, text, outline) {
    const style = outline ? 'border:2px solid ' + color + ';background:transparent' : 'background:' + color;
    return '<div class="sw"><span style="' + style + '"></span>' + text + '</div>';
  }

  function info() {
    const keys = [...new Set([state.h, state.c])];
    let html = '';
    for (const k of keys) {
      const tables = Shapes.ringTable(k);
      tables.forEach((t, i) => {
        html += '<h4>' + Shapes.SHAPES[k].name + (tables.length > 1 ? (i === 0 ? ', top plate' : ', bottom plate') : '') + '</h4>';
        html += '<p>Ring ' + t.length + ' blocks (' + (t.even ? 'even' : 'odd') + '), ' + t.nodes + ' nodes, ' + t.exposed + ' exposed.</p>';
        html += '<table><tr><th>#</th><th>(u,v)</th><th>kind</th><th>node</th><th>floor</th></tr>';
        for (const c of t.corners) {
          html += '<tr><td>' + c.index + '</td><td>(' + c.u + ',' + c.v + ')</td><td>' + c.kind + '</td><td>' + (c.node ? 'yes' : 'edge') + '</td><td>' + c.floor + '</td></tr>';
        }
        html += '</table>';
      });
    }
    els.info.innerHTML = html;
  }

  // ------------------------------------------------------------ events

  function bind() {
    const on = (el, ev, fn) => el.addEventListener(ev, fn);
    on(els.scene, 'change', () => { state.scene = els.scene.value; rebuild(true); });
    on(els.h, 'change', () => { state.h = els.h.value; rebuild(true); });
    on(els.c, 'change', () => { state.c = els.c.value; rebuild(true); });
    on(els.pattern, 'change', () => { state.pattern = els.pattern.value; rebuild(false); });
    on(els.lattice, 'change', () => { state.lattice = els.lattice.checked; rebuild(false); });
    on(els.res, 'change', () => { state.res = els.res.checked; rebuild(false); });
    on(els.cut, 'change', () => { state.cut = els.cut.checked; rebuild(false); });
    on(els.chunks, 'input', () => { state.chunks = Number(els.chunks.value); els.chunksOut.textContent = state.chunks; rebuild(true); });
    on(els.band, 'input', () => { state.band = Number(els.band.value); els.bandOut.textContent = state.band; rebuild(false); });
    on(els.ortho, 'change', () => { state.ortho = els.ortho.checked; camera = state.ortho ? ortho : persp; fit(state.view); writeHash(); });
    document.querySelectorAll('input[name=plane]').forEach((r) => on(r, 'change', () => { state.webPlane = r.value; rebuild(false); }));
    document.querySelectorAll('button[data-view]').forEach((b) => on(b, 'click', () => { fit(b.dataset.view); writeHash(); }));
    window.addEventListener('resize', resize);
  }

  // The screenshot tool reads the canvas through this function.
  window.shotData = () => { render(); return renderer.domElement.toDataURL('image/png'); };

  readHash();
  camera = state.ortho ? ortho : persp;
  syncControls();
  bind();
  resize();
  rebuild(true);
})();
