#!/usr/bin/env python3
"""Bricks prototype for the pillar outer layer.

The outer layer is 48 blocks around and infinite along the pillar axis. A chunk
is 16 blocks along the axis. Bed joints are grooves around the ring. Head joints
are grooves along the axis inside one course. Three sizes exist:

- small: courses 3 tall, bed joints every 4 rows, bricks 5 wide. Neighbor faces
  swap their course phase, so a corner brick is 8 wide.
- jumbled: three courses per chunk, 4, 4 and 5 tall in a random sequence. Each
  face has head joints at random even columns.
- large: courses 7 tall, bed joints every 8 rows, bricks 11 wide.

surfacepaths.py gives the groove graph, the loop share rationing, the surface
paths, the checks and the renders.

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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import surfacepaths as sp  # noqa: E402

CHUNK = 16
FACE = 12
RING = 4 * FACE
CORNERS = tuple(range(0, RING, FACE))
SIZES = ("small", "jumbled", "large")
BOX_HALF = 7
PATCH_HALF = 4
PATCH_FACES = (1, 3)
DEFAULT_BOX = 89
MARGIN = 4
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "images")

Params = namedtuple("Params", "size seed pillar surface cut share box chance")


def rng(P, c, stream):
    """The random of one stream of chunk c. It mixes the seed, the pillar, the size and the chunk."""
    return random.Random(f"{P.seed}:{P.pillar}:{P.size}:{c}:{stream}")


def pillar_flip(P):
    """0 or 1 from the pillar random. It picks which face pair of a small pillar starts on phase 0."""
    return random.Random(f"{P.seed}:{P.pillar}:flip").randrange(2)


def courses(P, c):
    """The courses of chunk c as (start offset, height). A bed joint follows each course."""
    if P.size == "small":
        return [(0, 3), (4, 3), (8, 3), (12, 3)]
    if P.size == "large":
        return [(0, 7), (8, 7)]
    heights = [4, 4, 5]
    rng(P, c, "heights").shuffle(heights)
    out = []
    s = 0
    for h in heights:
        out.append((s, h))
        s += h + 1
    return out


def node_rows(P, height):
    """The node rows of a course, as offsets from its start."""
    if P.size == "small":
        return (1,)
    if P.size == "large":
        return (1, 3, 5)
    return (1, 2) if height == 4 else (1, 3)


def small_joints(P, k):
    """The head joints of small course k: column 6 on phase 0 faces, columns 3 and 9 on phase 1 faces."""
    out = []
    for f in range(4):
        cols = (6,) if (k + f + pillar_flip(P)) % 2 == 0 else (3, 9)
        out.extend(FACE * f + x for x in cols)
    return tuple(out)


def draw_jumbled(r, avoid):
    """Head joints of one jumbled course: two even columns per face at least 4 apart, none in `avoid`.

    A face falls back to one joint when no pair is free.
    """
    out = []
    for f in range(4):
        free = [x for x in (2, 4, 6, 8, 10) if FACE * f + x not in avoid]
        pairs = [(a, b) for a in free for b in free if b - a >= 4]
        if pairs:
            a, b = pairs[r.randrange(len(pairs))]
            out += [FACE * f + a, FACE * f + b]
        else:
            out.append(FACE * f + free[r.randrange(len(free))])
    return tuple(out)


@lru_cache(maxsize=None)
def jumbled_last(P, c):
    """The head joints of the last jumbled course of chunk c. Its stream needs no other chunk."""
    return draw_jumbled(rng(P, c, "joints-last"), set())


@lru_cache(maxsize=None)
def course_joints(P, c):
    """The head joint ring positions of each course of chunk c."""
    n = len(courses(P, c))
    if P.size == "small":
        return tuple(small_joints(P, 4 * c + i) for i in range(n))
    if P.size == "large":
        return tuple(tuple(FACE * f + (2 if (2 * c + i) % 2 == 0 else 10) for f in range(4)) for i in range(n))
    out = [None] * n
    out[-1] = jumbled_last(P, c)
    prev = jumbled_last(P, c - 1)
    r = rng(P, c, "joints")
    for i in range(n - 1):
        avoid = set(prev) | (set(out[-1]) if i == n - 2 else set())
        out[i] = draw_jumbled(r, avoid)
        prev = out[i]
    return tuple(out)


def bricks(joints):
    """The bricks of one course as lists of unwrapped ring positions between neighbor joints."""
    js = sorted(joints)
    out = []
    for i, j in enumerate(js):
        nxt = js[(i + 1) % len(js)] + (RING if i + 1 == len(js) else 0)
        out.append(list(range(j + 1, nxt)))
    return out


def face_positions(P, brick):
    """The ring positions of the face nodes of one brick."""
    if P.size == "small":
        return list(range(brick[0] + 1, brick[-1], 2))
    return [x for x in brick if x % 2 == 0]


def bed_node(P, r):
    """True when ring position r is a groove node of a bed joint."""
    if P.size == "small":
        return r % FACE in (0, 3, 6, 9)
    return r % 2 == 0


def is_box(P, p):
    """True when p is a box block. The patch inside the bezel of each exposed face is not."""
    if P.box is None or abs(p[1] - P.box) > BOX_HALF:
        return False
    f, col = divmod(p[0] % RING, FACE)
    inner = abs(col - FACE // 2) <= PATCH_HALF and abs(p[1] - P.box) <= PATCH_HALF
    return not (f in PATCH_FACES and inner)


@lru_cache(maxsize=None)
def base_band(P, lo, hi):
    """The bed joints, head joints, node sets and box of chunks [lo, hi), with no surface path."""
    band = sp.Band(RING, lo * CHUNK, (hi - lo) * CHUNK, CORNERS)
    for c in range(lo, hi):
        for (s, h), js in zip(courses(P, c), course_joints(P, c)):
            top = c * CHUNK + s
            bed = top + h
            for r in range(RING):
                band.set((r, bed), sp.GROOVE)
                if bed_node(P, r):
                    band.groove_nodes.add((r, bed))
            for j in js:
                for a in range(top, bed):
                    band.set((j, a), sp.GROOVE)
                for o in node_rows(P, h):
                    band.groove_nodes.add((j, top + o))
            for b in bricks(js):
                for x in face_positions(P, b):
                    for o in node_rows(P, h):
                        band.face_nodes.add((x % RING, top + o))
    if P.box is not None:
        for r in range(RING):
            for a in range(P.box - BOX_HALF, P.box + BOX_HALF + 1):
                p = (r, a)
                if band.inside(p) and is_box(P, p):
                    band.set(p, sp.BOX)
                    band.groove_nodes.discard(p)
                    band.face_nodes.discard(p)
    return band


def path_key(P):
    """The parameters that the surface paths depend on."""
    return P._replace(share=None, surface=True)


def course_regions(band, P, c, i):
    """The brick regions of course i of chunk c, each a set of positions without box blocks."""
    s, h = courses(P, c)[i]
    top = c * CHUNK + s
    out = []
    for b in bricks(course_joints(P, c)[i]):
        region = {(x % RING, a) for x in b for a in range(top, top + h)}
        out.append({p for p in region if band.get(p) != sp.BOX})
    return out


def choose_course(band, P, c, i, r):
    """Pick and place the surface paths of course i of chunk c in brick sequence. Returns them."""
    out = []
    for region in course_regions(band, P, c, i):
        if r.random() >= P.chance:
            continue
        nodes = {p for p in band.face_nodes if p in region}
        path = sp.pick_path(band, sp.surface_candidates(band, region, nodes), r)
        if path is not None:
            sp.place(band, path, P.cut)
            out.append(path)
    return out


def neighborhood(P, c):
    """A copy of the base band of chunks c-1 to c+1."""
    src = base_band(P, c - 1, c + 2)
    band = src.crop(src.axis0, src.length)
    return band


@lru_cache(maxsize=None)
def last_course_paths(P, c):
    """The surface paths of the last course of chunk c. They come first, so no other chunk paths matter."""
    band = neighborhood(P, c)
    n = len(courses(P, c))
    return tuple(choose_course(band, P, c, n - 1, rng(P, c, "paths-last")))


@lru_cache(maxsize=None)
def chunk_paths(P, c):
    """The surface paths of chunk c: the last course first, then the others from the top.

    The first course fits around the last-course paths of chunk c-1.
    """
    band = neighborhood(P, c)
    for path in last_course_paths(P, c - 1) + last_course_paths(P, c):
        sp.place(band, path, P.cut)
    r = rng(P, c, "paths")
    out = list(last_course_paths(P, c))
    for i in range(len(courses(P, c)) - 1):
        out += choose_course(band, P, c, i, r)
    return tuple(out)


Scene = namedtuple("Scene", "band removed graph stats")


def scene(P, lo, hi):
    """The pillar band of chunks [lo, hi) after surface paths and loop rationing.

    It builds a margin of MARGIN chunks on each side, so the result is the same
    for every window that holds [lo, hi).
    """
    band = base_band(P, lo - MARGIN, hi + MARGIN)
    band = band.crop(band.axis0, band.length)
    pins = set()
    if P.surface or P.cut:
        k = path_key(P)
        for c in range(lo - MARGIN, hi + MARGIN):
            for path in chunk_paths(k, c):
                sp.place(band, path, P.cut)
                if not P.cut:
                    pins.update(path.ends)
    graph = sp.groove_graph(band)

    def removable(e):
        free = [b for b in e.chain if b not in pins]
        return [b for b in free if b not in band.groove_nodes] or free

    removed, stats = sp.sample_segments(
        graph, lambda v: v[1] // CHUNK, range(lo - 2, hi + 2),
        lambda s: rng(P, s, "segment"), P.share, removable)
    for b in removed:
        band.set(b, sp.WALL)
    out = band.crop(lo * CHUNK, (hi - lo) * CHUNK)
    return Scene(out, [b for b in removed if out.inside(b)], graph, stats)


def suffix(P):
    """The file name suffix of one parameter set."""
    s = P.size
    if P.cut:
        s += "_cut"
    elif P.surface:
        s += "_surface"
    if P.share != 1.0:
        s += f"_loops{round(P.share * 100)}"
    if P.box is not None:
        s += "_box"
    if P.seed != 1:
        s += f"_seed{P.seed}"
    if P.pillar != 0:
        s += f"_pillar{P.pillar}"
    return s


def label(P):
    """The label text of one render."""
    mode = "cut" if P.cut else ("surface" if P.surface else "plain")
    text = f"{P.size} seed {P.seed} {mode} loops {round(P.share * 100)}%"
    return text + (f" box at {P.box}" if P.box is not None else "")


def render(P, chunks, scale, out_dir=OUT_DIR):
    """Render the band of chunks [0, chunks) and its channel image. Returns the readable image path."""
    sc = scene(P, 0, chunks)
    name = f"bricks_{suffix(P)}.png"
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


def check(chunks, seeds, sizes):
    """Assert the rules on every size, mode, loop share and box option for the given seeds."""
    tree_hi = chunks if chunks % 2 else chunks - 1
    runs = 0
    for seed in seeds:
        for size in sizes:
            for surface, cut in ((False, False), (True, False), (True, True)):
                for share in (0.0, 0.5, 1.0):
                    for box in (None, DEFAULT_BOX):
                        P = Params(size, seed, 0, surface, cut, share, box, 0.75)
                        sc = scene(P, 0, chunks)
                        b = sc.band
                        assert not sp.two_wide(b), (P, "2-wide", sp.two_wide(b)[:4])
                        assert not sp.crossings(b), (P, "plus", sp.crossings(b)[:4])
                        assert not sp.corner_runs(b), (P, "corner run", sp.corner_runs(b)[:4])
                        assert sc.stats["unbreakable"] == 0, (P, "unbreakable", sc.stats)
                        for c in range(chunks):
                            alone = scene(P, c, c + 1).band
                            part = b.kind[:, c * CHUNK:(c + 1) * CHUNK]
                            assert np.array_equal(alone.kind, part), (P, "chunk differs", c)
                        if share == 0.0:
                            cycles, full, kept = vertex_forest(sc.graph, sc.removed, 0, tree_hi)
                            assert cycles == 0 and full == kept, (P, "vertex forest", cycles, full, kept)
                            rank = sp.cycle_rank(b.crop(0, tree_hi * CHUNK))
                            assert rank == 0, (P, "block cycles", rank)
                            if box is None:
                                assert kept == 1, (P, "not one tree", kept)
                        runs += 1
        print(f"seed {seed}: ok")
    print(f"{runs} scenes passed, each chunk also rendered alone")


def deliverables(chunks, scale, seed):
    """Render the image set of the README."""
    base = Params("small", seed, 0, False, False, 1.0, None, 0.75)
    out = []
    for size in SIZES:
        for surface, cut in ((False, False), (True, False), (True, True)):
            out.append(render(base._replace(size=size, surface=surface, cut=cut), chunks, scale))
    for share in (0.0, 0.5):
        out.append(render(base._replace(share=share), chunks, scale))
    for size in ("small", "large"):
        out.append(render(base._replace(size=size, box=DEFAULT_BOX), chunks, scale))
        out.append(render(base._replace(size=size, box=DEFAULT_BOX, surface=True), chunks, scale))
    return out


def share_value(text):
    v = int(text)
    if not 0 <= v <= 100 or v % 5:
        raise argparse.ArgumentTypeError("the loop share is a percent from 0 to 100 in steps of 5")
    return v / 100


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--size", choices=SIZES, default="small")
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
    ap.add_argument("--chance", type=float, default=0.75, help="chance that a brick gets a surface path")
    ap.add_argument("--scale", type=int, default=5)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--check-seeds", type=int, default=3)
    ap.add_argument("--all", action="store_true", help="render the image set of the README")
    args = ap.parse_args()
    os.makedirs(os.path.join(OUT_DIR, "channels"), exist_ok=True)
    if args.check:
        check(args.chunks, range(1, args.check_seeds + 1), SIZES)
        return
    if args.all:
        for p in deliverables(args.chunks, args.scale, args.seed):
            print(p)
        return
    share = 0.0 if args.no_loops else args.loops
    P = Params(args.size, args.seed, args.pillar, args.surface or args.cut, args.cut, share, args.box, args.chance)
    print(render(P, args.chunks, args.scale))


if __name__ == "__main__":
    main()
