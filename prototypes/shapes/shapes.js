// Voxel geometry of the pillar shapes Pillar, I-beam and Bridge, and of their junctions.
// The module has no DOM dependency. It sets globalThis.Shapes.
(function (root) {
  'use strict';

  const OUTER = 6;
  const BOX = 7;
  const PATCH_EDGE = 4;
  const HOLE = 5;

  // ---------------------------------------------------------------- cross sections
  // A cross section uses the offsets (u, v), each from -6 to 6.
  // v is the stacking direction: flange to flange, or plate to plate.

  function pillarSolid(u, v) {
    return Math.abs(u) <= OUTER && Math.abs(v) <= OUTER;
  }

  function ibeamSolid(u, v) {
    const au = Math.abs(u), av = Math.abs(v);
    if (au > OUTER || av > OUTER) return false;
    return av >= 3 || au <= 2;
  }

  function bridgeSolid(u, v) {
    const au = Math.abs(u), av = Math.abs(v);
    return au <= OUTER && av >= 4 && av <= OUTER;
  }

  // The truss diagonal at the offset and the axis position, or null.
  // Row r runs from 0 at v = 6 down to 12 at v = -6, like bridge.png.
  function bridgeTruss(u, v, s) {
    if (Math.abs(v) > 3) return null;
    const r = 6 - v;
    const m = mod(s, 8);
    const red = m === mod(5 - r, 8) || m === mod(6 - r, 8);
    const green = m === mod(r + 1, 8) || m === mod(r + 2, 8);
    if (u === 6 || u === -5) return red ? 'trussRed' : null;
    if (u === -6 || u === 5) return green ? 'trussGreen' : null;
    return null;
  }

  // The number of solid blocks from the offset to the first air block, along one direction.
  function run(solid, u, v, du, dv) {
    let n = 0;
    while (solid(u + du * (n + 1), v + dv * (n + 1))) n++;
    return n;
  }

  // Resistance of the current pillar: 0 at the outer layer, 1 at the 3x3 core.
  function pillarResistance(u, v) {
    const o = Math.max(Math.abs(u), Math.abs(v));
    return o > OUTER ? 0 : Math.min(1, (OUTER - o) / (OUTER - 1));
  }

  // Resistance of a plate: the middle layer is 1, each surface layer is 0.
  function plateResistance(solid, u, v, alongV, half) {
    const d = alongV
      ? Math.min(run(solid, u, v, 0, 1), run(solid, u, v, 0, -1))
      : Math.min(run(solid, u, v, 1, 0), run(solid, u, v, -1, 0));
    return Math.min(1, d / half);
  }

  function ibeamResistance(u, v) {
    if (!ibeamSolid(u, v)) return 0;
    // The flanges are 4 thick, so 1 layer separates a surface from the middle layers.
    if (Math.abs(v) >= 3) return plateResistance(ibeamSolid, u, v, true, 1);
    // The web is 5 thick, so 2 layers separate a surface from the middle layer.
    return plateResistance(ibeamSolid, u, v, false, 2);
  }

  function bridgeResistance(u, v) {
    if (!bridgeSolid(u, v)) return 0;
    return plateResistance(bridgeSolid, u, v, true, 1);
  }

  const TRUSS_RESISTANCE = 0.5;

  // ---------------------------------------------------------------- rings

  const DIRS = [[1, 0], [0, -1], [-1, 0], [0, 1]];

  // A ring block is a solid block with an air block among its 8 neighbors.
  // The walk starts at `start` and takes its first step along +u.
  function traceRing(solid, start) {
    const isRing = (u, v) => {
      if (!solid(u, v)) return false;
      for (let du = -1; du <= 1; du++)
        for (let dv = -1; dv <= 1; dv++)
          if ((du || dv) && !solid(u + du, v + dv)) return true;
      return false;
    };
    const ring = [];
    let [u, v] = start;
    let pu = u - 1, pv = v;
    const seen = new Set();
    for (;;) {
      ring.push([u, v]);
      seen.add(u + ',' + v);
      let next = null;
      for (const [du, dv] of DIRS) {
        const nu = u + du, nv = v + dv;
        if ((nu !== pu || nv !== pv) && isRing(nu, nv) && !seen.has(nu + ',' + nv)) { next = [nu, nv]; break; }
      }
      if (!next) break;
      pu = u; pv = v; [u, v] = next;
    }
    return ring.map(([u, v], i) => classify(solid, u, v, i));
  }

  function classify(solid, u, v, index) {
    const air = DIRS.filter(([du, dv]) => !solid(u + du, v + dv));
    let kind, normal, floor;
    if (air.length === 0) {
      kind = 'concave';
      // The outward normal points at the air diagonal.
      normal = [0, 0];
      for (const du of [-1, 1]) for (const dv of [-1, 1]) if (!solid(u + du, v + dv)) normal = [du, dv];
      floor = DIRS.map(([du, dv]) => [u + du, v + dv]).filter(([a, b]) => solid(a, b) && !onSurface(solid, a, b));
    } else {
      kind = air.length >= 2 ? 'convex' : 'face';
      normal = [air.reduce((t, d) => t + d[0], 0), air.reduce((t, d) => t + d[1], 0)];
      floor = [[u - normal[0], v - normal[1]]].filter(([a, b]) => solid(a, b));
    }
    const node = index % 2 === 0;
    return { index, u, v, kind, normal, floor, node, cornerNode: node && kind === 'convex' };
  }

  function onSurface(solid, u, v) {
    for (let du = -1; du <= 1; du++)
      for (let dv = -1; dv <= 1; dv++)
        if ((du || dv) && !solid(u + du, v + dv)) return true;
    return false;
  }

  // ---------------------------------------------------------------- shapes

  const SHAPES = {
    pillar: {
      key: 'pillar', name: 'Pillar',
      solid: pillarSolid, resistance: pillarResistance,
      rings: [traceRing(pillarSolid, [-OUTER, OUTER])],
      flush: () => false, truss: () => null,
    },
    ibeam: {
      key: 'ibeam', name: 'I-beam',
      solid: ibeamSolid, resistance: ibeamResistance,
      rings: [traceRing(ibeamSolid, [-OUTER, OUTER])],
      flush: () => false, truss: () => null,
    },
    bridge: {
      key: 'bridge', name: 'Bridge',
      solid: bridgeSolid, resistance: bridgeResistance,
      rings: [traceRing(bridgeSolid, [-OUTER, OUTER]), traceRing(bridgeSolid, [-OUTER, -OUTER])],
      // The inner faces of the plates show the pattern as flush surface path blocks.
      flush: (u, v) => Math.abs(v) === 4,
      truss: bridgeTruss,
    },
  };

  // ---------------------------------------------------------------- pattern

  // The placeholder pattern: each ruling is one ring path with one gap.
  // One axis edge at the gap node joins the ruling to the next ruling.
  function gapNode(ring, ringIndex, k) {
    const n = ring.length / 2;
    let g = mod(3 + 5 * k + 7 * ringIndex, n);
    while (ring[2 * g].cornerNode) g = mod(g + 1, n);
    return g;
  }

  // The carved ring indices of the ruling k.
  function rulingCarve(ring, ringIndex, k, pattern) {
    const carved = new Set();
    if (pattern === 'none') return carved;
    const L = ring.length;
    const g = gapNode(ring, ringIndex, k);
    const gapEdge = mod(2 * g + 1, L);
    for (let i = 0; i < L; i++) if (i !== gapEdge) carved.add(i);
    // A corner node of degree one is dropped with its edge.
    for (let i = 0; i < L; i += 2) {
      if (!ring[i].cornerNode) continue;
      const a = mod(i - 1, L), b = mod(i + 1, L);
      const deg = (carved.has(a) ? 1 : 0) + (carved.has(b) ? 1 : 0);
      if (deg === 1) {
        carved.delete(i);
        carved.delete(carved.has(a) ? a : b);
      }
    }
    return carved;
  }

  // ---------------------------------------------------------------- voxel store

  class Voxels {
    constructor() { this.map = new Map(); this.markers = []; this.holes = []; this.labels = []; this.cz = 0; }
    static key(x, y, z) { return ((x + 1024) * 2048 + (y + 1024)) * 2048 + (z + 1024); }
    get(x, y, z) { return this.map.get(Voxels.key(x, y, z)); }
    set(x, y, z, block) { this.map.set(Voxels.key(x, y, z), { cz: this.cz, ...block, x, y, z }); }
    delete(x, y, z) { this.map.delete(Voxels.key(x, y, z)); }
    has(x, y, z) { return this.map.has(Voxels.key(x, y, z)); }
  }

  // Maps (s, u, v) of a pillar to world (x, y, z).
  function horizontalFrame(z0) {
    return (s, u, v) => [s, v, u + z0];
  }

  function columnFrame(webPlane, x0, z0) {
    return webPlane === 'x'
      ? (s, u, v) => [v + x0, s, u + z0]
      : (s, u, v) => [u + x0, s, v + z0];
  }

  // Paints one pillar of the shape over the axis positions s0 to s1, inclusive.
  function paintPillar(out, shape, s0, s1, frame, opts) {
    const skip = opts.skip || (() => false);
    for (let s = s0; s <= s1; s++) {
      if (skip(s)) continue;
      for (let u = -OUTER; u <= OUTER; u++) {
        for (let v = -OUTER; v <= OUTER; v++) {
          const [x, y, z] = frame(s, u, v);
          if (shape.solid(u, v)) {
            out.set(x, y, z, { role: 'interior', res: shape.resistance(u, v), owner: shape.key });
          } else {
            const t = shape.truss(u, v, s);
            if (t) out.set(x, y, z, { role: t, res: TRUSS_RESISTANCE, owner: shape.key });
          }
        }
      }
      shape.rings.forEach((ring) => {
        for (const p of ring) {
          const [x, y, z] = frame(s, p.u, p.v);
          const b = out.get(x, y, z);
          if (b) b.role = 'wall';
        }
      });
    }
    // Carving runs after the base pass, so a floor block never returns to wall.
    shape.rings.forEach((ring, ri) => {
      for (let s = s0; s <= s1; s++) {
        if (skip(s)) continue;
        let carved;
        if (mod(s, 2) === 1) {
          carved = rulingCarve(ring, ri, (s - 1) / 2, opts.pattern);
        } else {
          carved = new Set();
          if (opts.pattern !== 'none') carved.add(2 * gapNode(ring, ri, (s - 2) / 2));
        }
        for (const i of carved) carve(out, shape, ring[i], frame, s);
      }
      if (opts.lattice) {
        for (let s = s0; s <= s1; s++) {
          if (skip(s) || mod(s, 2) !== 1) continue;
          for (const p of ring) {
            if (!p.node) continue;
            const [x, y, z] = frame(s, p.u, p.v);
            const [nx, ny, nz] = frame(0, p.normal[0], p.normal[1]).map((c, i) => c - frame(0, 0, 0)[i]);
            out.markers.push({ x: x + nx * 0.5, y: y + ny * 0.5, z: z + nz * 0.5, corner: p.cornerNode, cz: out.cz });
          }
        }
      }
    });
  }

  function carve(out, shape, p, frame, s) {
    const [x, y, z] = frame(s, p.u, p.v);
    const b = out.get(x, y, z);
    if (!b) return;
    if (shape.flush(p.u, p.v)) {
      b.role = 'surfacePath';
      return;
    }
    out.delete(x, y, z);
    for (const [fu, fv] of p.floor) {
      const [fx, fy, fz] = frame(s, fu, fv);
      const f = out.get(fx, fy, fz);
      if (f && f.role === 'interior') f.role = 'path';
    }
  }

  // ---------------------------------------------------------------- shear

  function hash3(x, y, z, seed) {
    let h = Math.imul(x, 0x27d4eb2d) ^ Math.imul(y, 0x165667b1) ^ Math.imul(z, 0x1b873593) ^ Math.imul(seed, 0x5bd1e995);
    h = Math.imul(h ^ (h >>> 15), 0x85ebca6b);
    h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }

  function valueNoise(x, y, z, scale, seed) {
    const fx = x / scale, fy = y / scale, fz = z / scale;
    const ix = Math.floor(fx), iy = Math.floor(fy), iz = Math.floor(fz);
    const tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
    let r = 0;
    for (let a = 0; a < 2; a++)
      for (let b = 0; b < 2; b++)
        for (let c = 0; c < 2; c++) {
          const w = (a ? tx : 1 - tx) * (b ? ty : 1 - ty) * (c ? tz : 1 - tz);
          r += w * hash3(ix + a, iy + b, iz + c, seed);
        }
    return r;
  }

  function smooth(t) { return t * t * (3 - 2 * t); }

  function shearNoise(x, y, z, seed) {
    return 0.65 * valueNoise(x, y, z, 4, seed) + 0.35 * valueNoise(x, y, z, 2, seed + 1);
  }

  // Removes column blocks in the band below `top` and above `bottom`.
  // The threshold rises from 0 at the inner band edge to 1 at the end.
  function shearColumn(out, owner, x0, z0, bottom, top, band, seed) {
    for (const b of [...out.map.values()]) {
      if (b.owner !== owner || Math.abs(b.x - x0) > OUTER || Math.abs(b.z - z0) > OUTER) continue;
      let t = 0;
      if (b.y > top - band) t = (b.y - (top - band)) / band;
      if (b.y < bottom + band) t = ((bottom + band) - b.y) / band;
      if (t > 0 && shearNoise(b.x, b.y, b.z, seed) < t) out.delete(b.x, b.y, b.z);
    }
    out.markers = out.markers.filter((m) => {
      if (Math.abs(m.x - x0) > OUTER + 1 || Math.abs(m.z - z0) > OUTER + 1) return true;
      return m.y <= top - band && m.y >= bottom + band;
    });
  }

  // ---------------------------------------------------------------- scenes

  // One horizontal pillar along X, `chunks` chunks long.
  function horizontalScene(shapeKey, opts, out = new Voxels(), z0 = 0) {
    const n = opts.chunks * 16;
    out.cz = z0;
    paintPillar(out, SHAPES[shapeKey], -n / 2, n / 2 - 1, horizontalFrame(z0), opts);
    out.labels.push({ text: SHAPES[shapeKey].name, x: 0, y: 10, z: z0 });
    return out;
  }

  function columnLength(opts) {
    return Math.max(opts.chunks * 16, 2 * (opts.band + 8));
  }

  // One column along Y with sheared ends.
  function columnScene(shapeKey, opts, out = new Voxels(), x0 = 0) {
    const h = columnLength(opts);
    const owner = 'column' + x0;
    const shape = SHAPES[shapeKey];
    const tmp = new Voxels();
    out.cz = tmp.cz = 0;
    paintPillar(tmp, shape, 0, h - 1, columnFrame(opts.webPlane, x0, 0), opts);
    for (const b of tmp.map.values()) b.owner = owner;
    shearColumn(tmp, owner, x0, 0, 0, h - 1, opts.band, 11 + x0);
    for (const b of tmp.map.values()) out.set(b.x, b.y, b.z, b);
    out.markers.push(...tmp.markers);
    out.labels.push({ text: shape.name, x: x0, y: h + 4, z: 0 });
    return out;
  }

  // The 15x15x15 junction of a horizontal pillar along X and a column, centered on the origin.
  function junctionScene(hKey, cKey, opts, out = new Voxels(), x0 = 0, z0 = 0) {
    const n = opts.chunks * 16;
    const hShape = SHAPES[hKey], cShape = SHAPES[cKey];
    const hFrame = (s, u, v) => [s + x0, v, u + z0];
    out.cz = z0;
    paintPillar(out, hShape, -n, n - 1, hFrame, opts);

    const half = BOX + Math.max(n, 8 + opts.band);
    const owner = 'column' + x0 + ',' + z0;
    const tmp = new Voxels();
    tmp.cz = z0;
    paintPillar(tmp, cShape, -half, half, columnFrame(opts.webPlane, x0, z0), { ...opts, skip: (s) => Math.abs(s) <= BOX });
    for (const b of tmp.map.values()) b.owner = owner;
    shearColumn(tmp, owner, x0, z0, -half, half, opts.band, 7 + x0 + 3 * z0);
    for (const b of tmp.map.values()) out.set(b.x, b.y, b.z, b);
    out.markers.push(...tmp.markers);

    const hole = hKey === 'bridge';
    out.markers = out.markers.filter((m) => !(Math.abs(m.x - x0) <= BOX + 0.5 && Math.abs(m.y) <= BOX + 0.5 && Math.abs(m.z - z0) <= BOX + 0.5));
    for (let x = -BOX; x <= BOX; x++)
      for (let y = -BOX; y <= BOX; y++)
        for (let z = -BOX; z <= BOX; z++) {
          if (hole && Math.abs(y) <= HOLE && Math.abs(z) <= HOLE) continue;
          const box = (BOX - Math.max(Math.abs(x), Math.abs(y), Math.abs(z))) / (BOX - 1);
          const cuv = opts.webPlane === 'x' ? [z, x] : [x, z];
          const res = Math.max(box, hShape.resistance(z, y), cShape.resistance(cuv[0], cuv[1]));
          let role = 'junction';
          if (Math.abs(z) === BOX && Math.max(Math.abs(x), Math.abs(y)) > PATCH_EDGE) role = 'bezel';
          else if (Math.abs(z) === BOX) role = 'wall';
          out.set(x + x0, y, z + z0, { role, res, owner: 'box' });
        }
    // The patch on +Z serves the horizontal pillar. The patch on -Z serves the column.
    paintPatch(out, x0, z0, 1, false, opts);
    paintPatch(out, x0, z0, -1, true, opts);
    if (hole) out.holes.push({ x0: x0 - BOX - 0.5, x1: x0 + BOX + 0.5, y0: -HOLE - 0.5, y1: HOLE + 0.5, z0: z0 - HOLE - 0.5, z1: z0 + HOLE + 0.5 });
    out.labels.push({ text: hShape.name + ' + ' + cShape.name + ' column', x: x0, y: half + 4, z: z0 });
    return out;
  }

  // A boustrophedon over the 5x5 patch nodes, rows along X on +Z and along Y on -Z.
  function paintPatch(out, x0, z0, side, vertical, opts) {
    const z = side * BOX;
    const cells = [];
    for (let row = -2; row <= 2; row++) {
      const b = 2 * row;
      for (let a = -PATCH_EDGE; a <= PATCH_EDGE; a++) cells.push([a, b]);
      if (row < 2) {
        const end = mod(row, 2) === 0 ? PATCH_EDGE : -PATCH_EDGE;
        cells.push([end, b + 1]);
      }
    }
    if (opts.pattern !== 'none') {
      for (const [a, b] of cells) {
        const [x, y] = vertical ? [b, a] : [a, b];
        out.delete(x + x0, y, z + z0);
        const f = out.get(x + x0, y, z - side + z0);
        if (f) f.role = 'path';
      }
    }
    if (opts.lattice) {
      for (let a = -PATCH_EDGE; a <= PATCH_EDGE; a += 2)
        for (let b = -PATCH_EDGE; b <= PATCH_EDGE; b += 2)
          out.markers.push({ x: a + x0, y: b, z: z + side * 0.5 + z0, corner: false, cz: z0 });
    }
  }

  function shapesScene(opts) {
    const out = new Voxels();
    ['pillar', 'ibeam', 'bridge'].forEach((k, i) => horizontalScene(k, opts, out, (i - 1) * 24));
    return out;
  }

  function columnsScene(opts) {
    const out = new Voxels();
    ['pillar', 'ibeam', 'bridge'].forEach((k, i) => columnScene(k, opts, out, (i - 1) * 24));
    return out;
  }

  function junctionsScene(opts) {
    const out = new Voxels();
    const keys = ['pillar', 'ibeam', 'bridge'];
    const step = 2 * opts.chunks * 16 + 16;
    keys.forEach((h, i) => keys.forEach((c, j) => junctionScene(h, c, opts, out, (j - 1) * step, (i - 1) * 40)));
    return out;
  }

  function build(opts) {
    let out;
    switch (opts.scene) {
      case 'shapes': out = shapesScene(opts); break;
      case 'pillar': out = horizontalScene(opts.h, opts); break;
      case 'column': out = columnScene(opts.c, opts); break;
      case 'columns': out = columnsScene(opts); break;
      case 'junctions': out = junctionsScene(opts); break;
      default: out = junctionScene(opts.h, opts.c, opts);
    }
    if (opts.cut) {
      for (const b of [...out.map.values()]) if (b.z > b.cz) out.delete(b.x, b.y, b.z);
      out.markers = out.markers.filter((m) => m.cz === undefined || m.z <= m.cz);
    }
    return out;
  }

  // The ring table of a shape: length, node count and each corner.
  function ringTable(shapeKey) {
    return SHAPES[shapeKey].rings.map((ring) => ({
      length: ring.length,
      even: ring.length % 2 === 0,
      nodes: ring.filter((p) => p.node).length,
      corners: ring.filter((p) => p.kind !== 'face').map((p) => ({
        index: p.index, u: p.u, v: p.v, kind: p.kind, node: p.node,
        floor: p.floor.map(([a, b]) => '(' + a + ',' + b + ')').join(' '),
      })),
      exposed: ring.filter((p) => p.kind !== 'concave').length,
    }));
  }

  function mod(a, n) { return ((a % n) + n) % n; }

  root.Shapes = { SHAPES, build, ringTable, Voxels, constants: { OUTER, BOX, PATCH_EDGE, HOLE, TRUSS_RESISTANCE } };
})(typeof globalThis !== 'undefined' ? globalThis : this);
