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
  // Rows 2 and 10 are the anchors in the inner plate layers.
  function bridgeTruss(u, v, s) {
    if (Math.abs(v) > 4) return null;
    const r = 6 - v;
    const m = mod(s, 8);
    const red = m === mod(5 - r, 8) || m === mod(6 - r, 8);
    const green = m === mod(r + 1, 8) || m === mod(r + 2, 8);
    if (u === 4 || u === -5) return red ? 'trussRed' : null;
    if (u === -4 || u === 5) return green ? 'trussGreen' : null;
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
      flush: (u, v) => Math.abs(v) === 4 && Math.abs(u) <= 5,
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
    return carved;
  }

  // A corner node of degree one is dropped with its edge, unless a crossing holds it.
  function dropLeaves(ring, carved, keep) {
    const L = ring.length;
    for (let i = 0; i < L; i += 2) {
      if (!ring[i].cornerNode || !carved.has(i) || (keep && keep.has(i))) continue;
      const a = mod(i - 1, L), b = mod(i + 1, L);
      const deg = (carved.has(a) ? 1 : 0) + (carved.has(b) ? 1 : 0);
      if (deg === 1) {
        carved.delete(i);
        carved.delete(carved.has(a) ? a : b);
      }
    }
  }

  // ---------------------------------------------------------------- voxel store

  class Voxels {
    constructor() { this.map = new Map(); this.markers = []; this.holes = []; this.labels = []; this.links = []; this.checks = []; this.cz = 0; }
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
    finishPillar(out, preparePillar(out, shape, s0, s1, frame, opts, null), opts);
  }

  // Paints the base blocks and computes the carved ring positions of each axis position.
  // A linked pillar drops the rulings in its cuts and each axis edge that touches a dropped ruling.
  function preparePillar(out, shape, s0, s1, frame, opts, link) {
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
    const gone = (s) => skip(s) || (link !== null && link.cuts.some(([lo, hi]) => s >= lo && s <= hi));
    const sets = shape.rings.map((ring, ri) => {
      const m = new Map();
      for (let s = s0; s <= s1; s++) {
        if (gone(s)) continue;
        if (mod(s, 2) === 1) {
          m.set(s, rulingCarve(ring, ri, (s - 1) / 2, opts.pattern));
        } else {
          const c = new Set();
          const joined = link === null || (s - 1 >= s0 && s + 1 <= s1 && !gone(s - 1) && !gone(s + 1));
          if (opts.pattern !== 'none' && joined) c.add(2 * gapNode(ring, ri, (s - 2) / 2));
          m.set(s, c);
        }
      }
      return m;
    });
    return { shape, s0, s1, frame, skip, gone, sets, keep: shape.rings.map(() => new Map()) };
  }

  // Drops corner leaves, carves the blocks, places the lattice markers and the truss anchors.
  function finishPillar(out, P, opts) {
    const { shape, s0, s1, frame, skip } = P;
    // Carving runs after the base pass, so a floor block never returns to wall.
    shape.rings.forEach((ring, ri) => {
      for (const [s, set] of P.sets[ri]) {
        if (mod(s, 2) === 1) dropLeaves(ring, set, P.keep[ri].get(s));
        for (const i of set) carve(out, shape, ring[i], frame, s);
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
    // A truss anchor replaces the plate block, also on a flush surface path.
    for (let s = s0; s <= s1; s++) {
      if (skip(s)) continue;
      for (let u = -OUTER; u <= OUTER; u++) {
        for (let v = -OUTER; v <= OUTER; v++) {
          if (!shape.solid(u, v)) continue;
          const t = shape.truss(u, v, s);
          if (!t) continue;
          const [x, y, z] = frame(s, u, v);
          out.set(x, y, z, { role: t, res: TRUSS_RESISTANCE, owner: shape.key });
        }
      }
    }
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

  // ---------------------------------------------------------------- junction link

  class UnionFind {
    constructor() { this.parent = new Map(); }
    add(k) { if (!this.parent.has(k)) this.parent.set(k, k); }
    has(k) { return this.parent.has(k); }
    find(k) {
      let r = k;
      while (this.parent.get(r) !== r) r = this.parent.get(r);
      while (this.parent.get(k) !== r) { const n = this.parent.get(k); this.parent.set(k, r); k = n; }
      return r;
    }
    union(a, b) {
      this.add(a); this.add(b);
      const ra = this.find(a), rb = this.find(b);
      if (ra === rb) return false;
      this.parent.set(ra, rb);
      return true;
    }
  }

  function nodeKey(pid, ri, s, p) { return pid + '|' + ri + '|' + s + '|' + p; }

  // Adds the placeholder ring edges of a prepared pillar to the union-find.
  function ringGraph(P, uf, pid) {
    P.shape.rings.forEach((ring, ri) => {
      const L = ring.length;
      for (const [s, set] of P.sets[ri]) {
        if (mod(s, 2) === 1) {
          for (const p of set) {
            if (p % 2 === 0) uf.add(nodeKey(pid, ri, s, p));
            else if (set.has(mod(p - 1, L)) && set.has(mod(p + 1, L))) uf.union(nodeKey(pid, ri, s, mod(p - 1, L)), nodeKey(pid, ri, s, mod(p + 1, L)));
          }
        } else {
          for (const p of set) uf.union(nodeKey(pid, ri, s - 1, p), nodeKey(pid, ri, s + 1, p));
        }
      }
    });
  }

  // The node pairs of the patch boustrophedon, in patch coordinates (a, b).
  function patchEdges() {
    const edges = [];
    for (let row = -2; row <= 2; row++) {
      const b = 2 * row;
      for (let a = -PATCH_EDGE; a < PATCH_EDGE; a += 2) edges.push([[a, b], [a + 2, b]]);
      if (row < 2) {
        const end = mod(row, 2) === 0 ? PATCH_EDGE : -PATCH_EDGE;
        edges.push([[end, b], [end, b + 2]]);
      }
    }
    return edges;
  }

  function strHash(text) {
    let h = 0x811c9dc5;
    for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 0x01000193);
    return hash3(h, 17, 29, 5);
  }

  // The crossing candidates of a patch, as offsets t across the pillar and box sides sgn.
  // `facing` is 'edge' when the flange edges or the plate sides face the patch, else 'face'.
  function crossingCandidates(shapeKey, facing, choiceKey) {
    const both = (ts) => [1, -1].flatMap((sgn) => ts.map((t) => ({ t, sgn })));
    if (shapeKey === 'pillar') return both([-2, 0, 2]);
    if (shapeKey === 'ibeam') return facing === 'edge' ? both([4, -4]) : both([-2, 0, 2]);
    // A bridge patch takes exactly one crossing from one plate, from one side of the box.
    const h = Math.floor(strHash(choiceKey) * 4);
    const sgn = (h & 2) ? -1 : 1;
    if (facing === 'edge') return [{ t: (h & 1) ? -4 : 4, sgn }];
    return [{ t: 0, sgn }];
  }

  function shuffled(list, key) {
    return list.map((c, i) => ({ c, k: strHash(key + '#' + i) })).sort((a, b) => a.k - b.k).map((e) => e.c);
  }

  // Counts the components of each ring of a finished pillar.
  function ringComponents(P, uf, pid) {
    return P.shape.rings.map((ring, ri) => {
      const roots = new Set();
      for (const [s, set] of P.sets[ri]) {
        if (mod(s, 2) !== 1) continue;
        for (const p of set) if (p % 2 === 0) roots.add(uf.find(nodeKey(pid, ri, s, p)));
      }
      return roots;
    });
  }

  // The column ring offset (u, v) on the column face toward axis e ('x' or 'z') with sign sigma, at offset t along the other axis.
  function columnFaceUV(plane, e, sigma, t) {
    const xoff = e === 'x' ? sigma * OUTER : t;
    const zoff = e === 'z' ? sigma * OUTER : t;
    return plane === 'x' ? [zoff, xoff] : [xoff, zoff];
  }

  // Junctions of horizontal pillars and columns.
  // hs: [{ axis: 'x', y, z, centers }] runs along X at z0 + z; [{ axis: 'z', y, x, centers }] runs along Z at x0 + x.
  // The centers of a horizontal pillar are offsets along its axis. cs: [{ x, z, centers }] with centers as Y.
  // A box stands where a column meets a horizontal pillar at a center of both.
  // The box exposes the faces across the horizontal axis: the patch on the + face serves the horizontal pillar,
  // the patch on the - face serves the column.
  // With the junction link, the ring pattern is cut at each box and the patches join it through crossings.
  function junctionLayout(hKey, cKey, hs, cs, opts, out, x0, z0) {
    const link = !!opts.link && opts.pattern !== 'none';
    const n = opts.chunks * 16;
    const hShape = SHAPES[hKey], cShape = SHAPES[cKey];
    const half = BOX + Math.max(n, 8 + opts.band);
    out.cz = z0;
    const uf = new UnionFind();

    const H = hs.map((h) => {
      const ax = h.axis || 'x';
      const frame = ax === 'x'
        ? (s, u, v) => [s + x0, v + h.y, u + z0 + (h.z || 0)]
        : (s, u, v) => [u + x0 + h.x, v + h.y, s + z0];
      const id = ax === 'x' ? 'h' + x0 + ',' + z0 + (h.y ? ',' + h.y : '') : 'hz' + (x0 + h.x) + ',' + h.y;
      // A horizontal Bridge runs through its holes, so its plate trees stay uncut.
      const cuts = hKey === 'bridge' ? [] : h.centers.map((c) => [c - BOX, c + BOX]);
      const P = preparePillar(out, hShape, h.centers[0] - n, h.centers[h.centers.length - 1] + n - 1, frame, opts, link ? { cuts } : null);
      if (link) ringGraph(P, uf, id);
      return { ...h, axis: ax, P, id, frame };
    });

    const C = cs.map((c) => {
      const cx = x0 + c.x, cz = z0 + (c.z || 0);
      const owner = 'column' + cx + ',' + cz;
      const tmp = new Voxels();
      tmp.cz = z0;
      const frame = columnFrame(opts.webPlane, cx, cz);
      const lo = c.centers[0] - half, hi = c.centers[c.centers.length - 1] + half;
      const skip = (s) => c.centers.some((by) => Math.abs(s - by) <= BOX);
      const P = preparePillar(tmp, cShape, lo, hi, frame, { ...opts, skip }, link ? { cuts: [] } : null);
      if (link) ringGraph(P, uf, owner);
      return { ...c, z: c.z || 0, P, tmp, owner, cx, cz, lo, hi, frame, touches: new Set(), matched: 0 };
    });

    const boxes = [];
    for (const h of H) {
      for (const c of C) {
        const rel = h.axis === 'x' ? c.x : c.z;
        const lines = h.axis === 'x' ? (h.z || 0) === c.z : h.x === c.x;
        if (lines && h.centers.includes(rel) && c.centers.includes(h.y)) {
          boxes.push({ h, c, rel, bx: c.cx, by: h.y, bz: c.cz, e: h.axis === 'x' ? 'z' : 'x' });
        }
      }
    }
    for (const b of boxes) {
      const { bx, by, bz, e } = b;
      // (p, q): p runs along the horizontal axis, q along Y.
      b.world = (sign, p, q) => e === 'z' ? [bx + p, by + q, bz + sign * BOX] : [bx + sign * BOX, by + q, bz + p];
      b.under = (sign) => e === 'z' ? [0, 0, -sign] : [-sign, 0, 0];
    }

    const accepted = [];
    const patches = [];
    if (link) {
      for (const b of boxes) {
        const { h, c, rel, by, e } = b;
        const pkey = (sign, p, q) => 'patch|' + b.world(sign, p, q).join(',');
        const patchGraph = (sign) => {
          for (const [[a1, b1], [a2, b2]] of patchEdges()) {
            if (sign > 0) uf.union(pkey(sign, a1, b1), pkey(sign, a2, b2));
            else uf.union(pkey(sign, b1, a1), pkey(sign, b2, a2));
          }
          patches.push(pkey(sign, 0, 0));
        };
        const attempt = (cand) => {
          const [u, v] = cand.uv;
          const ri = cand.P.shape.rings.findIndex((ring) => ring.some((q) => q.u === u && q.v === v));
          if (ri < 0) return false;
          const p = cand.P.shape.rings[ri].findIndex((q) => q.u === u && q.v === v);
          const rk = nodeKey(cand.pid, ri, cand.sAt(9), p);
          if (!uf.has(rk)) return false;
          // The patch node faces the ring node: a side node of the patch, or a patch corner when |t| is 4.
          if (!uf.union(rk, pkey(cand.sign, ...cand.node(PATCH_EDGE)))) return false;
          const P = cand.P;
          accepted.push({ P, ri, p, sAt: cand.sAt, world: (k) => b.world(cand.sign, ...cand.node(k)), under: b.under(cand.sign), cz: z0 });
          if (!P.keep[ri].has(cand.sAt(9))) P.keep[ri].set(cand.sAt(9), new Set());
          P.keep[ri].get(cand.sAt(9)).add(p);
          const rim = P.sets[ri].get(cand.sAt(8));
          if (rim) rim.add(p);
          return true;
        };

        // The patch on the + face serves the horizontal pillar.
        patchGraph(1);
        let list = crossingCandidates(hKey, 'edge', opts.bridgeChoice === 'junction' ? h.id + '@' + rel : h.id);
        if (hKey !== 'bridge') list = shuffled(list, h.id + '@' + rel);
        for (const { t, sgn } of list) {
          attempt({ P: h.P, pid: h.id, uv: [OUTER, t], sAt: (k) => rel + sgn * k, sign: 1, node: (k) => [sgn * k, t] });
        }

        // The patch on the - face serves the column.
        patchGraph(-1);
        const key = c.owner + '@' + by;
        const columnCand = (sigma, t, sgn) => ({
          P: c.P, pid: c.owner, uv: columnFaceUV(opts.webPlane, e, sigma, t),
          sAt: (k) => by + sgn * k, sign: sigma, node: (k) => [t, sgn * k],
        });
        const sideNodes = (salt) => shuffled([1, -1].flatMap((sgn) => [-2, 0, 2].map((t) => ({ t, sgn }))), key + salt);
        if (cKey !== 'bridge') {
          const facing = opts.webPlane === e ? 'face' : 'edge';
          for (const { t, sgn } of shuffled(crossingCandidates(cKey, facing, key), key)) attempt(columnCand(-1, t, sgn));
        } else if (opts.webPlane !== e) {
          // Matched junction: both plates face the column patch with their side faces.
          c.matched++;
          for (const t of [4, -4]) for (const sgn of [1, -1]) attempt(columnCand(-1, t, sgn));
        } else {
          // Mismatched junction: the near plate joins the column patch like a Pillar face.
          for (const { t, sgn } of sideNodes('#near')) attempt(columnCand(-1, t, sgn));
          // The far plate joins the patch of the horizontal pillar, on its top and bottom sides.
          for (const { t, sgn } of sideNodes('#far')) if (attempt(columnCand(1, t, sgn))) c.touches.add(h);
        }
      }
    }

    for (const h of H) finishPillar(out, h.P, opts);
    for (const c of C) {
      finishPillar(c.tmp, c.P, opts);
      for (const b of c.tmp.map.values()) b.owner = c.owner;
      shearColumn(c.tmp, c.owner, c.cx, c.cz, c.lo, c.hi, opts.band, 7 + c.cx + 3 * c.cz);
      for (const b of c.tmp.map.values()) out.set(b.x, b.y, b.z, b);
      out.markers.push(...c.tmp.markers);
    }

    const hole = hKey === 'bridge';
    for (const b of boxes) {
      const { bx, by, bz, e } = b;
      out.markers = out.markers.filter((m) => !(Math.abs(m.x - bx) <= BOX + 0.5 && Math.abs(m.y - by) <= BOX + 0.5 && Math.abs(m.z - bz) <= BOX + 0.5));
      for (let x = -BOX; x <= BOX; x++)
        for (let y = -BOX; y <= BOX; y++)
          for (let z = -BOX; z <= BOX; z++) {
            // ea is the offset across the horizontal axis, ha the offset along it.
            const ea = e === 'z' ? z : x, ha = e === 'z' ? x : z;
            if (hole && Math.abs(y) <= HOLE && Math.abs(ea) <= HOLE) continue;
            const box = (BOX - Math.max(Math.abs(x), Math.abs(y), Math.abs(z))) / (BOX - 1);
            const cuv = opts.webPlane === 'x' ? [z, x] : [x, z];
            const res = Math.max(box, hShape.resistance(ea, y), cShape.resistance(cuv[0], cuv[1]));
            let role = 'junction';
            if (Math.abs(ea) === BOX && Math.max(Math.abs(ha), Math.abs(y)) > PATCH_EDGE) role = 'bezel';
            else if (Math.abs(ea) === BOX) role = 'wall';
            out.set(x + bx, y + by, z + bz, { role, res, owner: 'box' });
          }
      paintPatch(out, b, 1, opts);
      paintPatch(out, b, -1, opts);
      if (hole) {
        out.holes.push(e === 'z'
          ? { x0: bx - BOX - 0.5, x1: bx + BOX + 0.5, y0: by - HOLE - 0.5, y1: by + HOLE + 0.5, z0: bz - HOLE - 0.5, z1: bz + HOLE + 0.5 }
          : { x0: bx - HOLE - 0.5, x1: bx + HOLE + 0.5, y0: by - HOLE - 0.5, y1: by + HOLE + 0.5, z0: bz - BOX - 0.5, z1: bz + BOX + 0.5 });
      }
    }

    // Each crossing runs along the outer layer to the box, then notches through the bezel at face offsets 7, 6 and 5.
    for (const { P, ri, p, sAt, world, under } of accepted) {
      const q = P.shape.rings[ri][p];
      for (const [fu, fv] of q.floor) {
        const [fx, fy, fz] = P.frame(sAt(8), fu, fv);
        const f = out.get(fx, fy, fz);
        if (f && f.role === 'path') f.role = 'crossing';
      }
      const points = [P.frame(sAt(9), q.u, q.v), P.frame(sAt(8), q.u, q.v)];
      for (let k = BOX; k >= PATCH_EDGE; k--) {
        const [x, y, z] = world(k);
        points.push([x, y, z]);
        if (k === PATCH_EDGE) break;
        out.delete(x, y, z);
        const f = out.get(x + under[0], y + under[1], z + under[2]);
        if (f) f.role = 'crossing';
      }
      out.links.push({ points, cz: z0 });
    }

    const name = hShape.name + ' + ' + cShape.name + ' column';
    const xs = H.flatMap((h) => (h.axis === 'x' ? h.centers : [h.x]));
    out.labels.push({ text: name, x: x0 + (Math.min(...xs) + Math.max(...xs)) / 2, y: Math.max(...C.map((c) => c.hi)) + 4, z: z0 });
    if (link) {
      const pillarRoots = new Set();
      let ok = true;
      const label = (h) => (H.length > 1 && H.some((g) => g.axis !== h.axis) ? (h.axis === 'x' ? 'X ' : 'Z ') : '');
      const hTexts = H.map((h) => {
        const comps = ringComponents(h.P, uf, h.id);
        comps.forEach((r) => r.forEach((x) => pillarRoots.add(x)));
        if (!comps.every((r) => r.size === 1)) ok = false;
        return label(h) + (comps.length > 1 ? comps.map((r, i) => (i === 0 ? 'top ' : 'bottom ') + r.size).join(', ') : String(comps[0].size));
      });
      const cTexts = C.map((c) => {
        const comps = ringComponents(c.P, uf, c.owner);
        comps.forEach((r) => r.forEach((x) => pillarRoots.add(x)));
        if (cKey !== 'bridge') {
          if (!comps.every((r) => r.size === 1)) ok = false;
          return String(comps[0].size);
        }
        // A column Bridge is one tree with both plates and its patches. A touched horizontal pillar joins that tree.
        const group = new Set(comps.flatMap((r) => [...r]));
        let target = 1;
        for (const h of c.touches) {
          ringComponents(h.P, uf, h.id).forEach((r) => r.forEach((x) => group.add(x)));
          target += h.P.shape.rings.length - 1;
        }
        // A column of the mod has one matched junction. Without it, the two plates stay apart.
        if (c.matched === 0) target++;
        if (group.size !== target) ok = false;
        const touched = [...c.touches].map((h) => label(h) + hShape.name).join(', ');
        return 'whole ' + group.size + ' (target ' + target + (touched ? ', touches ' + touched : '') + (c.matched === 0 ? ', no matched junction' : '') + ')';
      });
      const floating = patches.filter((k) => !pillarRoots.has(uf.find(k))).length;
      if (floating) ok = false;
      out.checks.push({ ok, text: name + ': ' + hShape.name + ' ' + hTexts.join(' / ') + '; column ' + cTexts.join(' / ') + '; floating patches ' + floating + '; crossings ' + accepted.length });
    }
    return out;
  }

  function junctionScene(hKey, cKey, opts, out = new Voxels(), x0 = 0, z0 = 0) {
    return junctionLayout(hKey, cKey, [{ y: 0, centers: [0] }], [{ x: 0, centers: [0] }], opts, out, x0, z0);
  }

  // Three junctions on one horizontal pillar, 3 chunks apart.
  function runScene(opts) {
    const centers = [-48, 0, 48];
    return junctionLayout(opts.h, opts.c, [{ y: 0, centers }], centers.map((x) => ({ x, centers: [0] })), opts, new Voxels(), 0, 0);
  }

  // One column with an X junction below and a Z junction above, 3 chunks apart.
  function columnXZScene(opts) {
    const hs = [{ axis: 'x', y: -24, z: 0, centers: [0] }, { axis: 'z', y: 24, x: 0, centers: [0] }];
    return junctionLayout(opts.h, opts.c, hs, [{ x: 0, z: 0, centers: [-24, 24] }], opts, new Voxels(), 0, 0);
  }

  // A boustrophedon over the 5x5 patch nodes, rows along the horizontal axis on the + face and along Y on the - face.
  function paintPatch(out, b, sign, opts) {
    const cells = [];
    for (let row = -2; row <= 2; row++) {
      const r = 2 * row;
      for (let a = -PATCH_EDGE; a <= PATCH_EDGE; a++) cells.push([a, r]);
      if (row < 2) {
        const end = mod(row, 2) === 0 ? PATCH_EDGE : -PATCH_EDGE;
        cells.push([end, r + 1]);
      }
    }
    const under = b.under(sign);
    if (opts.pattern !== 'none') {
      for (const [a, r] of cells) {
        const [x, y, z] = sign > 0 ? b.world(sign, a, r) : b.world(sign, r, a);
        out.delete(x, y, z);
        const f = out.get(x + under[0], y + under[1], z + under[2]);
        if (f) f.role = 'path';
      }
    }
    if (opts.lattice) {
      for (let a = -PATCH_EDGE; a <= PATCH_EDGE; a += 2)
        for (let r = -PATCH_EDGE; r <= PATCH_EDGE; r += 2) {
          const [x, y, z] = b.world(sign, a, r);
          out.markers.push({ x: x - under[0] * 0.5, y: y - under[1] * 0.5, z: z - under[2] * 0.5, corner: false, cz: b.bz });
        }
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
      case 'run': out = runScene(opts); break;
      case 'columnXZ': out = columnXZScene(opts); break;
      default: out = junctionScene(opts.h, opts.c, opts);
    }
    if (opts.cut) {
      for (const b of [...out.map.values()]) if (b.z > b.cz) out.delete(b.x, b.y, b.z);
      out.markers = out.markers.filter((m) => m.cz === undefined || m.z <= m.cz);
      out.links = out.links.filter((l) => l.points.every((q) => q[2] <= l.cz));
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
