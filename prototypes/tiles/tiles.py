#!/usr/bin/env python3
"""Tiles prototype for the pillar outer layer.

The outer layer is 48 blocks around and infinite along the pillar axis. A chunk
is 16 blocks along the axis. Axis grooves run along the pillar. Ring grooves run
around it. The grooves cut the outer layer into tiles. Two sizes exist:

- small: 3x3 tiles, axis grooves at face columns 2, 6 and 10, ring grooves every 4 rows.
- large: 7x7 tiles, axis grooves at face columns 2 and 10, ring grooves every 8 rows.
  The corner strip (column 11, the corner, column 1) is a 3x7 tile.

Stagger x shifts every other course by half a tile period. Stagger y shifts every
other tile column by half a tile height. Encased keeps the corner strip solid.
scales.py adds the tail of the scales to the small x layout.

surfacepaths.py gives the groove graph, the loop share rationing, the surface
path enumeration, the checks and the renders.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md
"""

import argparse
import os
import random
import sys
from collections import namedtuple
from functools import lru_cache

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, os.pardir, "patterns"))
import surfacepaths as sp  # noqa: E402

CHUNK = 16
FACE = 12
RING = 4 * FACE
CORNERS = tuple(range(0, RING, FACE))
SIZES = ("small", "large")
STAGGERS = ("none", "x", "y")
PERIOD = {"small": 4, "large": 8}
BOX_HALF = 7
PATCH_HALF = 4
PATCH_FACES = (1, 3)
DEFAULT_BOX = 89
MARGIN = 4
OUT_DIR = os.path.join(HERE, "images")

# tail: None for tiles, else "up", "down" or "seeded". over: surface paths may run over the tail.
# orphans: cut paths may leave a wall block with grooves on all four sides.
Params = namedtuple("Params", "size stagger encased tail over seed pillar surface cut share box chance orphans")
Tile = namedtuple("Tile", "top height cells tail")
Scene = namedtuple("Scene", "band removed graph stats base_pluses tails")


def tile_params(size="small", stagger="none", encased=False, seed=1, pillar=0, surface=False, cut=False,
                share=1.0, box=None, chance=0.75, orphans=False):
    """The parameters of one tiles render."""
    return Params(size, stagger, encased, None, False, seed, pillar, surface or cut, cut, share, box, chance, orphans)


def refusal(P):
    """The reason why the rules exclude this parameter set, or None."""
    if P.stagger == "x" and P.size != "small":
        return "stagger x exists for small tiles only"
    if P.stagger == "x" and P.encased:
        return "encased keeps the corner strip solid, so it excludes stagger x"
    if P.tail is not None and (P.size, P.stagger, P.encased) != ("small", "x", False):
        return "scales use small tiles with stagger x"
    if P.over and not (P.surface or P.cut):
        return "paths over tails need surface paths or cutting"
    return None


def layout_name(P):
    """The name of the groove layout. It also keys the random streams."""
    if P.tail is not None:
        return "scales"
    return f"{P.size}-{P.stagger}" + ("-encased" if P.encased else "")


def rng(P, c, stream):
    """The random of one stream of chunk c. It mixes the seed, the pillar, the layout and the chunk."""
    return random.Random(f"{P.seed}:{P.pillar}:{layout_name(P)}:{c}:{stream}")


@lru_cache(maxsize=None)
def pillar_bit(seed, pillar, what):
    """0 or 1 from the pillar random."""
    return random.Random(f"{seed}:{pillar}:{what}").randrange(2)


def pillar_flip(P):
    """0 or 1 from the pillar random. It picks which tile columns of stagger y take phase 0."""
    return pillar_bit(P.seed, P.pillar, "flip")


def tail_sign(P):
    """+1 when the tail points toward +axis, -1 toward -axis, 0 without a tail."""
    if P.tail is None:
        return 0
    if P.tail == "seeded":
        return 1 if pillar_bit(P.seed, P.pillar, "tail") == 0 else -1
    return 1 if P.tail == "down" else -1


def in_strip(r):
    """True when ring position r is in a corner strip: column 11, the corner or column 1."""
    return r % FACE in (FACE - 1, 0, 1)


def axis_groove(P, r, a):
    """True when (r, a) lies on an axis groove."""
    r %= RING
    if P.stagger == "x":
        return r % 4 == (3 if (a // 4) % 2 == 0 else 1)
    if P.size == "small":
        return r % 4 == 2
    return r % FACE in (2, FACE - 2)


def column(P, r):
    """The tile column index of ring position r, counted from the corner tile at ring position 0."""
    r %= RING
    if P.size == "small":
        return ((r + 1) // 4) % 12
    return 2 * (((r + 1) // FACE) % 4) + (0 if in_strip(r) else 1)


def ring_row(P, r):
    """The ring groove row of the tile column of r, modulo the period. None for an encased corner strip."""
    t = PERIOD[P.size]
    if P.encased and in_strip(r):
        return None
    if P.stagger == "y" and (column(P, r) + pillar_flip(P)) % 2:
        return t // 2 - 1
    return t - 1


def is_groove(P, r, a):
    """True when (r, a) is a groove of the layout, before the tails."""
    if axis_groove(P, r, a):
        return True
    g = ring_row(P, r)
    return g is not None and a % PERIOD[P.size] == g


def x_center(k):
    """The ring offset of the tile centers of course k of stagger x, modulo 4."""
    return 1 if k % 2 == 0 else 3


def tail_of(P, k, center):
    """The tail block of the stagger x tile of course k with center column `center`."""
    return (center % RING, 4 * k + 3) if tail_sign(P) > 0 else (center % RING, 4 * k - 1)


def tail_blocks(P, lo, hi):
    """Every tail block with an axis position in [lo, hi)."""
    if P.tail is None:
        return set()
    out = set()
    for k in range(lo // 4 - 2, hi // 4 + 2):
        for j in range(12):
            t = tail_of(P, k, 4 * j + x_center(k))
            if lo <= t[1] < hi:
                out.add(t)
    return out


def is_node(P, r):
    """True when ring position r holds nodes on the odd rows."""
    return r % 2 == (1 if P.stagger == "x" else 0)


def is_box(P, p):
    """True when p is a box block. The patch inside the bezel of each exposed face is not."""
    if P.box is None or abs(p[1] - P.box) > BOX_HALF:
        return False
    f, col = divmod(p[0] % RING, FACE)
    inner = abs(col - FACE // 2) <= PATCH_HALF and abs(p[1] - P.box) <= PATCH_HALF
    return not (f in PATCH_FACES and inner)


def layout_key(P):
    """The parameters that the groove layout and the node sets depend on."""
    return P._replace(over=False, surface=False, cut=False, share=1.0, chance=0.0, orphans=False)


@lru_cache(maxsize=None)
def _base_band(P, lo, hi):
    band = sp.Band(RING, lo * CHUNK, (hi - lo) * CHUNK, CORNERS)
    tails = tail_blocks(P, band.axis0, band.axis0 + band.length)
    for r in range(RING):
        node_col = is_node(P, r)
        for a in range(band.axis0, band.axis0 + band.length):
            p = (r, a)
            groove = is_groove(P, r, a) and p not in tails
            if groove:
                band.set(p, sp.GROOVE)
            if node_col and a % 2 == 1:
                (band.groove_nodes if groove else band.face_nodes).add(p)
    if P.box is not None:
        for r in range(RING):
            for a in range(P.box - BOX_HALF, P.box + BOX_HALF + 1):
                p = (r, a)
                if band.inside(p) and is_box(P, p):
                    band.set(p, sp.BOX)
                    band.groove_nodes.discard(p)
                    band.face_nodes.discard(p)
    return band


def base_band(P, lo, hi):
    """A copy of the grooves, node sets, tails and box of chunks [lo, hi), with no surface path."""
    src = _base_band(layout_key(P), lo, hi)
    return src.crop(src.axis0, src.length)


def tile_columns(P):
    """The tile columns of stagger none and y as (ring positions, is corner strip)."""
    if P.size == "small":
        return [([4 * j - 1, 4 * j, 4 * j + 1], j % 3 == 0) for j in range(12)]
    out = []
    for f in range(4):
        out.append(([FACE * f - 1, FACE * f, FACE * f + 1], True))
        out.append((list(range(FACE * f + 3, FACE * f + 10)), False))
    return out


def center_row(tile):
    """The axis position that assigns a tile to a chunk."""
    return tile.top + (tile.height - 1) // 2


@lru_cache(maxsize=None)
def chunk_tiles(P, c):
    """The tiles whose center row lies in chunk c, in axis then ring sequence.

    An encased corner strip is cut into pieces of one period. Each piece counts as a tile.
    """
    P = layout_key(P)
    lo, hi = c * CHUNK, (c + 1) * CHUNK
    t = PERIOD[P.size]
    out = []
    if P.stagger == "x":
        for k in range(lo // 4 - 1, hi // 4 + 1):
            for j in range(12):
                mid = 4 * j + x_center(k)
                cells = tuple((x % RING, a) for x in (mid - 1, mid, mid + 1) for a in range(4 * k, 4 * k + 3))
                tail = tail_of(P, k, mid) if P.tail is not None else None
                out.append(Tile(4 * k, 3, cells, tail))
    else:
        for cols, strip in tile_columns(P):
            g = ring_row(P, cols[1])
            if g is None:
                tops, h = range((lo // t - 1) * t, hi + t, t), t
            else:
                tops, h = range((lo // t - 1) * t + g + 1, hi + t, t), t - 1
            for top in tops:
                cells = tuple((x % RING, a) for x in cols for a in range(top, top + h))
                out.append(Tile(top, h, cells, None))
    out = [tl for tl in out if lo <= center_row(tl) < hi]
    out.sort(key=lambda tl: (tl.top, min(sp.akey(p) for p in tl.cells)))
    return tuple(out)


def is_last(P, tile):
    """True when the tile belongs to the last tiles of its chunk. They draw first."""
    return center_row(tile) % CHUNK >= CHUNK - PERIOD[P.size]


def region_of(band, P, tile):
    """The region of a tile: its blocks without box blocks, plus its tail when paths run over tails."""
    region = {p for p in tile.cells if band.inside(p) and band.get(p) != sp.BOX}
    if P.over and tile.tail is not None and band.inside(tile.tail) and band.get(tile.tail) == sp.WALL:
        region.add(tile.tail)
    return region


def fits(band, path, P, tails):
    """sp.fits, plus the tail exemption and the orphan rule.

    A tail block of the path may touch the two ring grooves beside it. In cut mode, the path
    must leave no wall block with grooves on all four sides, unless orphans are allowed.
    """
    blocks = path.blocks
    own = set(blocks)
    if len(own) != len(blocks):
        return False
    if any(band.get(b) != sp.WALL for b in blocks):
        return False

    def on(q):
        return q in own or sp.is_path(band.get(q))

    seq = [path.ends[0]] + list(blocks) + list(path.ends[1:])
    for i in range(1, len(blocks) + 1):
        allowed = {seq[i - 1]}
        if i + 1 < len(seq):
            allowed.add(seq[i + 1])
        if seq[i] in tails:
            allowed |= {band.step(seq[i], d) for d in sp.RING_DIRS}
        for q in sp.neighbors(band, seq[i]):
            if on(q) and q not in allowed:
                return False
    for a, b in zip(seq, seq[1:]):
        if not sp.linked(band, a, b):
            return False
    for g in path.ends:
        if sum(1 for q in sp.neighbors(band, g) if on(q)) >= 4:
            return False
    for b in blocks:
        for dr in (0, -1):
            for da in (0, -1):
                p0 = (b[0] + dr, b[1] + da)
                square = [(p0[0] + x, p0[1] + y) for x in (0, 1) for y in (0, 1)]
                if all(band.inside(q) and on((q[0] % band.ring, q[1])) for q in square):
                    return False
    if P.cut and not P.orphans:
        near = {q for b in blocks for q in sp.neighbors(band, b)} - own
        for w in near:
            if band.get(w) != sp.WALL:
                continue
            ns = sp.neighbors(band, w)
            if len(ns) == 4 and all(on(q) and band.get(q) != sp.SURFACE for q in ns):
                return False
    return True


def pick_path(band, candidates, rng_, P, tails, dead_end_share=0.5):
    """sp.pick_path with the fits of this module. The draws are the same."""
    ok = [p for p in candidates if fits(band, p, P, tails)]
    through = [p for p in ok if len(p.ends) == 2]
    dead = [p for p in ok if len(p.ends) == 1]
    want_dead = rng_.random() < dead_end_share
    pool = (dead or through) if want_dead else (through or dead)
    if not pool:
        return None
    return pool[rng_.randrange(len(pool))]


def choose(band, P, tiles, r):
    """Pick and place the surface paths of the tiles in their sequence. Returns them.

    An encased corner strip gets no cut path, because no groove crosses it.
    """
    tails = {t.tail for t in tiles if t.tail is not None} if P.over else set()
    out = []
    for tile in tiles:
        if r.random() >= P.chance:
            continue
        if P.encased and P.cut and all(in_strip(p[0]) for p in tile.cells):
            continue
        region = region_of(band, P, tile)
        nodes = {p for p in band.face_nodes if p in region and p != tile.tail}
        path = pick_path(band, sp.surface_candidates(band, region, nodes), r, P, tails)
        if path is not None:
            sp.place(band, path, P.cut)
            out.append(path)
    return out


def path_key(P):
    """The parameters that the surface paths depend on."""
    return P._replace(share=1.0, surface=True)


@lru_cache(maxsize=None)
def last_paths(P, c):
    """The surface paths of the last tiles of chunk c. They need no other chunk."""
    band = base_band(P, c - 1, c + 2)
    tiles = [t for t in chunk_tiles(P, c) if is_last(P, t)]
    return tuple(choose(band, P, tiles, rng(P, c, "paths-last")))


@lru_cache(maxsize=None)
def chunk_paths(P, c):
    """The surface paths of chunk c: the last tiles first, then the others around them.

    The other tiles fit around the last tiles of chunk c-1 and chunk c.
    """
    band = base_band(P, c - 1, c + 2)
    for path in last_paths(P, c - 1) + last_paths(P, c):
        sp.place(band, path, P.cut)
    tiles = [t for t in chunk_tiles(P, c) if not is_last(P, t)]
    return last_paths(P, c) + tuple(choose(band, P, tiles, rng(P, c, "paths")))


def attachments(band, core):
    """The core blocks that hold a groove outside the core. A removal there strands that groove."""
    out = set()
    for p in core:
        for q in sp.neighbors(band, p):
            if q not in core and band.get(q) == sp.GROOVE and sp.linked(band, p, q):
                out.add(p)
                break
    return out


def split_long_edges(graph, segment_of):
    """The groove graph with each edge that spans more than two neighbor segments cut into pieces.

    sp.sample_segments takes only edges between one segment and the same or the next one.
    A long edge gets a new vertex at each block where its chain enters another segment.
    """
    verts = set(graph.vertices)
    edges = []
    for e in graph.edges:
        if abs(segment_of(e.u) - segment_of(e.v)) <= 1:
            edges.append(e)
            continue
        seq = [e.u] + list(e.chain) + [e.v]
        start = 0
        for i in range(1, len(seq) - 1):
            if segment_of(seq[i]) != segment_of(seq[i - 1]):
                verts.add(seq[i])
                edges.append(sp.Edge(*sp._canonical(seq[start], seq[i], seq[start + 1:i])))
                start = i
        edges.append(sp.Edge(*sp._canonical(seq[start], seq[-1], seq[start + 1:-1])))
    return sp.GrooveGraph(sorted(verts, key=sp.akey), sorted(edges, key=sp.edge_key))


def scene(P, lo, hi):
    """The pillar band of chunks [lo, hi) after surface paths and loop rationing.

    It builds a margin of MARGIN chunks on each side, so the result is the same
    for every window that holds [lo, hi).
    """
    band = base_band(P, lo - MARGIN, hi + MARGIN)
    base_pluses = set(sp.crossings(band))
    tails = tail_blocks(P, band.axis0, band.axis0 + band.length)
    pins = set()
    if P.surface or P.cut:
        k = path_key(P)
        for c in range(lo - MARGIN, hi + MARGIN):
            for path in chunk_paths(k, c):
                for b in path.blocks:
                    if band.inside(b):
                        band.set(b, sp.GROOVE if P.cut else sp.SURFACE)
                if not P.cut:
                    pins.update(path.ends)
    graph = split_long_edges(sp.groove_graph(band), lambda v: v[1] // CHUNK)
    fixed = pins | attachments(band, sp.groove_core(band)) | tails

    def removable(e):
        free = [b for b in e.chain if b not in fixed]
        return [b for b in free if b not in band.groove_nodes] or free

    removed, stats = sp.sample_segments(
        graph, lambda v: v[1] // CHUNK, range(lo - 2, hi + 2),
        lambda s: rng(P, s, "segment"), P.share, removable)
    for b in removed:
        band.set(b, sp.WALL)
    out = band.crop(lo * CHUNK, (hi - lo) * CHUNK)
    return Scene(out, [b for b in removed if out.inside(b)], graph, stats,
                 {p for p in base_pluses if out.inside(p)}, {t for t in tails if out.inside(t)})


def suffix(P):
    """The file name suffix of one parameter set, after the layout."""
    s = ""
    if P.over:
        s += "_overtails"
    if P.cut:
        s += "_cut"
    elif P.surface:
        s += "_surface"
    if P.share != 1.0:
        s += f"_loops{round(P.share * 100)}"
    if P.box is not None:
        s += "_box"
    if P.orphans:
        s += "_orphans"
    if P.seed != 1:
        s += f"_seed{P.seed}"
    if P.pillar != 0:
        s += f"_pillar{P.pillar}"
    return s


def file_name(P):
    """The image file name of one parameter set."""
    if P.tail is not None:
        head = "scales" + ("" if P.tail == "seeded" else f"_tail{P.tail}")
    else:
        head = f"tiles_{P.size}" + ("" if P.stagger == "none" else f"_{P.stagger}") + ("_encased" if P.encased else "")
    return head + suffix(P) + ".png"


def label(P):
    """The label text of one render."""
    mode = "cut" if P.cut else ("surface" if P.surface else "plain")
    if P.tail is not None:
        head = f"scales tail {'down' if tail_sign(P) > 0 else 'up'}" + (" over tails" if P.over else "")
    else:
        head = f"{P.size} stagger {P.stagger}" + (" encased" if P.encased else "")
    text = f"{head} seed {P.seed} {mode} loops {round(P.share * 100)}%"
    return text + (f" box at {P.box}" if P.box is not None else "")


def render(P, chunks, scale, out_dir=OUT_DIR):
    """Render the band of chunks [0, chunks) and its channel image. Returns the readable image path."""
    sc = scene(P, 0, chunks)
    name = file_name(P)
    os.makedirs(os.path.join(out_dir, "channels"), exist_ok=True)
    path = os.path.join(out_dir, name)
    sp.write_band(sc.band, path, scale, label(P), sc.removed)
    sp.write_channels(sc.band, os.path.join(out_dir, "channels", name))
    return path


def vertex_forest(graph, removed, lo, hi):
    """Cycles and component counts of the vertex graph inside chunks [lo, hi), before and after rationing."""
    cut = set(removed)
    inside = [e for e in graph.edges if lo <= e.u[1] // CHUNK < hi and lo <= e.v[1] // CHUNK < hi]
    verts = [v for v in graph.vertices if lo <= v[1] // CHUNK < hi]
    full, kept = sp.UnionFind(), sp.UnionFind()
    for v in verts:
        full.find(v)
        kept.find(v)
    cycles = 0
    for e in inside:
        full.union(e.u, e.v)
        if not any(b in cut for b in e.chain):
            if not kept.union(e.u, e.v):
                cycles += 1
    count = lambda uf: len({uf.find(v) for v in verts})
    return cycles, count(full), count(kept)


def enclosed_components(band):
    """The number of groove components that touch neither the first nor the last row of the band.

    Such a component cannot join the rest of the pillar outside the band.
    """
    blocks = sp.groove_blocks(band)
    links = sp.groove_links(band, blocks)
    edge_rows = (band.axis0, band.axis0 + band.length - 1)
    seen = set()
    n = 0
    for p in blocks:
        if p in seen:
            continue
        seen.add(p)
        stack = [p]
        touches = False
        while stack:
            q = stack.pop()
            touches = touches or q[1] in edge_rows
            for w in links[q]:
                if w not in seen:
                    seen.add(w)
                    stack.append(w)
        n += 0 if touches else 1
    return n


def orphans(band):
    """Every wall block with grooves on all four sides."""
    g = band.kind == sp.GROOVE
    up = np.zeros_like(g)
    down = np.zeros_like(g)
    up[:, 1:] = g[:, :-1]
    down[:, :-1] = g[:, 1:]
    full = (band.kind == sp.WALL) & np.roll(g, 1, axis=0) & np.roll(g, -1, axis=0) & up & down
    return [(int(r), int(a) + band.axis0) for r, a in zip(*np.nonzero(full))]


def check_scene(P, chunks, one_tree, connected=True):
    """Assert the rules on one parameter set.

    `one_tree` asks for one vertex tree at 0% without a box. `connected` asks that no groove
    component lies enclosed in the band without a box.
    """
    tree_hi = chunks if chunks % 2 else chunks - 1
    sc = scene(P, 0, chunks)
    b = sc.band
    assert not sp.two_wide(b), (P, "2-wide", sp.two_wide(b)[:4])
    exempt = sc.tails if P.over else set()
    pluses = [p for p in sp.crossings(b) if p not in sc.base_pluses and p not in exempt]
    assert not pluses, (P, "new plus crossing", pluses[:4])
    assert not sp.corner_runs(b), (P, "corner run", sp.corner_runs(b)[:4])
    assert sc.stats["unbreakable"] == 0, (P, "unbreakable", sc.stats)
    if P.cut and not P.orphans:
        assert not orphans(b), (P, "orphan wall block", orphans(b)[:4])
    if not (P.cut and P.over):
        for t in sc.tails:
            assert b.get(t) != sp.GROOVE, (P, "tail carved", t)
    for c in range(chunks):
        alone = scene(P, c, c + 1).band
        part = b.kind[:, c * CHUNK:(c + 1) * CHUNK]
        assert np.array_equal(alone.kind, part), (P, "chunk differs", c)
    if P.share == 0.0:
        cycles, full, kept = vertex_forest(sc.graph, sc.removed, 0, tree_hi)
        assert cycles == 0 and full == kept, (P, "vertex forest", cycles, full, kept)
        part = b.crop(0, tree_hi * CHUNK)
        rank = sp.cycle_rank(part)
        assert rank == 0, (P, "block cycles", rank)
        if one_tree and P.box is None:
            assert kept == 1, (P, "not one tree", kept)
    if connected and P.box is None:
        n = enclosed_components(b)
        assert n == 0, (P, "enclosed groove component", n)


def tile_layouts():
    """Every allowed layout of tiles as (size, stagger, encased)."""
    out = []
    for size in SIZES:
        for stagger in STAGGERS:
            for encased in (False, True):
                P = tile_params(size, stagger, encased)
                if refusal(P) is None:
                    out.append((size, stagger, encased))
    return out


def check(chunks, seeds, layouts=None):
    """Assert the rules on every allowed layout, mode, loop share and box option for the given seeds."""
    runs = 0
    for seed in seeds:
        for size, stagger, encased in (layouts or tile_layouts()):
            for surface, cut in ((False, False), (True, False), (True, True)):
                for share in (0.0, 0.5, 1.0):
                    for box in (None, DEFAULT_BOX):
                        P = tile_params(size, stagger, encased, seed, 0, surface, cut, share, box)
                        check_scene(P, chunks, not encased)
                        runs += 1
        print(f"seed {seed}: ok")
    print(f"{runs} scenes passed, each chunk also rendered alone")


def deliverables(chunks, scale, seed):
    """Render the image set of the README."""
    out = []
    for size, stagger, encased in tile_layouts():
        out.append(render(tile_params(size, stagger, encased, seed), chunks, scale))
    for size in SIZES:
        for surface, cut in ((True, False), (True, True)):
            out.append(render(tile_params(size, seed=seed, surface=surface, cut=cut), chunks, scale))
    for share in (0.0, 0.5):
        out.append(render(tile_params(seed=seed, share=share), chunks, scale))
    for size in SIZES:
        out.append(render(tile_params(size, seed=seed, box=DEFAULT_BOX), chunks, scale))
    return out


def share_value(text):
    v = int(text)
    if not 0 <= v <= 100 or v % 5:
        raise argparse.ArgumentTypeError("the loop share is a percent from 0 to 100 in steps of 5")
    return v / 100


def common_args(ap):
    """Add the options that tiles.py and scales.py share."""
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--pillar", type=int, default=0)
    ap.add_argument("--chunks", type=int, default=12)
    ap.add_argument("--surface", action="store_true", help="place surface paths")
    ap.add_argument("--cut", action="store_true", help="carve the surface paths as grooves")
    ap.add_argument("--loops", type=share_value, default=1.0, metavar="PERCENT",
                    help="loop share in percent, 0 to 100 in steps of 5")
    ap.add_argument("--no-loops", action="store_true", help="loops off: the loop share is 0")
    ap.add_argument("--box", type=int, nargs="?", const=DEFAULT_BOX, default=None, metavar="AXIS",
                    help="one junction box centered at this axis position")
    ap.add_argument("--chance", type=float, default=0.75, help="chance that a tile gets a surface path")
    ap.add_argument("--orphans", action="store_true",
                    help="let a cut path leave a wall block with grooves on all four sides")
    ap.add_argument("--scale", type=int, default=5)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--check-seeds", type=int, default=2)
    ap.add_argument("--all", action="store_true", help="render the image set of the README")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--size", choices=SIZES, default="small")
    ap.add_argument("--stagger", choices=STAGGERS, default="none")
    ap.add_argument("--encased", action="store_true", help="keep the corner strip solid")
    common_args(ap)
    args = ap.parse_args()
    if args.check:
        check(args.chunks, range(1, args.check_seeds + 1))
        return
    if args.all:
        for p in deliverables(args.chunks, args.scale, args.seed):
            print(p)
        return
    share = 0.0 if args.no_loops else args.loops
    P = tile_params(args.size, args.stagger, args.encased, args.seed, args.pillar, args.surface, args.cut,
                    share, args.box, args.chance, args.orphans)
    why = refusal(P)
    if why:
        ap.error(why)
    print(render(P, args.chunks, args.scale))


if __name__ == "__main__":
    main()
