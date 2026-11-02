#!/usr/bin/env python3
"""Surface path toolkit for the patterns of the pillar outer layer.

The toolkit works on a band: the unwrapped outer layer of one pillar. A band
is a grid of ring positions by axis positions. The ring wraps. The axis does
not wrap. Each block has one kind: wall, groove, surface path or box.

- A groove is a maze path below the wall layer.
- A surface path is a block on the wall layer that becomes the surface path block.
- A box block belongs to a junction box. It is not part of the pattern.

A pattern program (bricks.py, and later tiles.py, scales.py and chaos.py) fills
the grooves and the node sets. This module then gives the groove graph, the
loop share rationing per segment, the surface path enumeration, the checks
and the renders. Nothing here knows the geometry of a brick or a tile.

A position is a tuple (ring, axis) with the global axis position.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md
"""

from collections import namedtuple

import numpy as np
from PIL import Image, ImageDraw, ImageFont

WALL = 0
GROOVE = 1
SURFACE = 2
BOX = 3

RING_DIRS = ((1, 0), (-1, 0))
AXIS_DIRS = ((0, 1), (0, -1))
DIRS = RING_DIRS + AXIS_DIRS

Edge = namedtuple("Edge", "u v chain")
GrooveGraph = namedtuple("GrooveGraph", "vertices edges")
SurfacePath = namedtuple("SurfacePath", "blocks ends turns")


def akey(p):
    """Sort key of a position: axis first, then ring."""
    return (p[1], p[0])


class Band:
    """The unwrapped outer layer of one pillar over an axis range, with a block kind and node sets.

    `kind[ring, axis - axis0]` holds the block kind. `groove_nodes` and
    `face_nodes` hold positions. A position on a `corners` ring position is a
    corner block: it never connects along the axis.
    """

    def __init__(self, ring, axis0, length, corners):
        self.ring = ring
        self.axis0 = axis0
        self.length = length
        self.corners = frozenset(corners)
        self.kind = np.full((ring, length), WALL, dtype=np.int8)
        self.groove_nodes = set()
        self.face_nodes = set()

    def inside(self, p):
        """True when the axis position of p lies in the band."""
        return self.axis0 <= p[1] < self.axis0 + self.length

    def get(self, p):
        """The kind of the block at p. The ring position wraps."""
        return int(self.kind[p[0] % self.ring, p[1] - self.axis0])

    def set(self, p, kind):
        """Set the kind of the block at p. The ring position wraps."""
        self.kind[p[0] % self.ring, p[1] - self.axis0] = kind

    def step(self, p, d):
        """The neighbor of p in direction d, wrapped on the ring, or None outside the axis range."""
        q = ((p[0] + d[0]) % self.ring, p[1] + d[1])
        return q if self.inside(q) else None

    def is_corner(self, p):
        """True when p sits on a corner ring position."""
        return p[0] % self.ring in self.corners

    def crop(self, axis0, length):
        """A copy of the band over [axis0, axis0 + length), with the node sets cut to that range."""
        out = Band(self.ring, axis0, length, self.corners)
        lo = axis0 - self.axis0
        out.kind = self.kind[:, lo:lo + length].copy()
        out.groove_nodes = {p for p in self.groove_nodes if out.inside(p)}
        out.face_nodes = {p for p in self.face_nodes if out.inside(p)}
        return out


def is_path(kind):
    """True for the kinds that count as path: groove and surface path."""
    return kind == GROOVE or kind == SURFACE


def neighbors(band, p):
    """The 4-neighbors of p inside the band, with the ring wrapped."""
    out = []
    for d in DIRS:
        q = band.step(p, d)
        if q is not None:
            out.append(q)
    return out


def linked(band, p, q):
    """True when the neighbor blocks p and q connect: an axis step on a corner never connects."""
    return p[1] == q[1] or not band.is_corner(p)


def path_mask(band):
    """A boolean array of the band, True on groove and surface path blocks."""
    return (band.kind == GROOVE) | (band.kind == SURFACE)


def two_wide(band):
    """Every 2x2 square of path blocks, as the position of its low corner. A valid band gives none."""
    m = path_mask(band)
    nxt = np.roll(m, -1, axis=0)
    sq = m[:, :-1] & nxt[:, :-1] & m[:, 1:] & nxt[:, 1:]
    return [(int(r), int(a) + band.axis0) for r, a in zip(*np.nonzero(sq))]


def crossings(band):
    """Every path block with path neighbors on all four sides. A valid band gives none."""
    m = path_mask(band)
    up = np.zeros_like(m)
    down = np.zeros_like(m)
    up[:, 1:] = m[:, :-1]
    down[:, :-1] = m[:, 1:]
    full = m & np.roll(m, 1, axis=0) & np.roll(m, -1, axis=0) & up & down
    return [(int(r), int(a) + band.axis0) for r, a in zip(*np.nonzero(full))]


def corner_runs(band):
    """Every pair of path blocks that touch along the axis on a corner. A valid band gives none."""
    m = path_mask(band)
    out = []
    for r in sorted(band.corners):
        hit = m[r, :-1] & m[r, 1:]
        out.extend((r, int(a) + band.axis0) for a in np.nonzero(hit)[0])
    return out


def groove_blocks(band):
    """The set of groove block positions."""
    return {(int(r), int(a) + band.axis0) for r, a in zip(*np.nonzero(band.kind == GROOVE))}


def groove_links(band, blocks):
    """Map each block of the set to its linked neighbors inside the set."""
    return {p: [q for q in neighbors(band, p) if q in blocks and linked(band, p, q)] for p in blocks}


def groove_core(band):
    """The 2-core of the groove blocks: the blocks left after dead ends are pruned again and again.

    Only the 2-core holds cycles. The pruned blocks hang as trees and stay.
    """
    blocks = groove_blocks(band)
    links = groove_links(band, blocks)
    degree = {p: len(links[p]) for p in blocks}
    stack = [p for p in blocks if degree[p] <= 1]
    core = set(blocks)
    while stack:
        p = stack.pop()
        if p not in core:
            continue
        core.discard(p)
        for q in links[p]:
            if q in core:
                degree[q] -= 1
                if degree[q] == 1:
                    stack.append(q)
    return core


def _canonical(u, v, chain):
    ku, kv = akey(u), akey(v)
    if ku > kv:
        return v, u, tuple(reversed(chain))
    if ku == kv:
        fwd, back = tuple(chain), tuple(reversed(chain))
        if [akey(b) for b in back] < [akey(b) for b in fwd]:
            return u, v, back
    return u, v, tuple(chain)


def edge_key(e):
    """Sort key of an edge: its end vertices, then its chain."""
    return (akey(e.u), akey(e.v), tuple(akey(b) for b in e.chain))


def groove_graph(band):
    """The groove graph of the 2-core: vertices are junction blocks, edges are the block chains between them.

    A vertex has 3 or more linked core neighbors. A core cycle without a junction gets its
    lowest block as vertex. An edge chain can be empty. Vertices and edges come sorted.
    """
    core = groove_core(band)
    links = groove_links(band, core)
    verts = {p for p in core if len(links[p]) >= 3}
    seen = set()
    for p in sorted(core, key=akey):
        if p in seen:
            continue
        comp = {p}
        stack = [p]
        while stack:
            q = stack.pop()
            for w in links[q]:
                if w not in comp:
                    comp.add(w)
                    stack.append(w)
        seen |= comp
        if not comp & verts:
            verts.add(min(comp, key=akey))
    edges = {}
    for v in verts:
        for n in links[v]:
            chain = []
            prev, cur = v, n
            while cur not in verts:
                chain.append(cur)
                prev, cur = cur, next(q for q in links[cur] if q != prev)
            e = Edge(*_canonical(v, cur, chain))
            edges[edge_key(e)] = e
    return GrooveGraph(sorted(verts, key=akey), [edges[k] for k in sorted(edges)])


def cycle_rank(band):
    """The number of independent cycles of the groove blocks: edges minus blocks plus components. 0 means a forest."""
    blocks = groove_blocks(band)
    links = groove_links(band, blocks)
    n_edges = sum(len(v) for v in links.values()) // 2
    comps = 0
    seen = set()
    for p in blocks:
        if p in seen:
            continue
        comps += 1
        seen.add(p)
        stack = [p]
        while stack:
            q = stack.pop()
            for w in links[q]:
                if w not in seen:
                    seen.add(w)
                    stack.append(w)
    return n_edges - len(blocks) + comps


class UnionFind:
    """A union-find over hashable items, created on first use."""

    def __init__(self):
        self.up = {}

    def find(self, x):
        """The representative of x."""
        self.up.setdefault(x, x)
        while self.up[x] != x:
            self.up[x] = self.up[self.up[x]]
            x = self.up[x]
        return x

    def union(self, a, b):
        """Join the sets of a and b. False when they already were one set."""
        ra, rb = self.find(a), self.find(b)
        if ra == rb:
            return False
        if rb < ra:
            ra, rb = rb, ra
        self.up[rb] = ra
        return True


def spanning_forest(nodes, pairs, rng):
    """A uniform spanning forest, one tree per component, sampled with Wilson's algorithm.

    `nodes` is a sorted list. `pairs` is a list of (u, v) edges, self-loops allowed.
    The result is the set of the indices of the tree edges.
    """
    adj = {n: [] for n in nodes}
    for i, (u, v) in enumerate(pairs):
        if u != v:
            adj[u].append((i, v))
            adj[v].append((i, u))
    in_tree = set()
    seen = set()
    for n in nodes:
        if n in seen:
            continue
        seen.add(n)
        stack = [n]
        while stack:
            q = stack.pop()
            for _, w in adj[q]:
                if w not in seen:
                    seen.add(w)
                    stack.append(w)
        in_tree.add(n)
    tree = set()
    for n in nodes:
        nxt = {}
        u = n
        while u not in in_tree:
            i, w = adj[u][rng.randrange(len(adj[u]))]
            nxt[u] = (i, w)
            u = w
        u = n
        while u not in in_tree:
            in_tree.add(u)
            i, w = nxt[u]
            tree.add(i)
            u = w
    return tree


def segment_owner(su, sv):
    """The segment that owns an edge between segments su and sv: the shared one, else the odd connector."""
    if su == sv:
        return su
    if abs(su - sv) != 1:
        raise ValueError(f"edge spans segments {su} and {sv}")
    return su if su % 2 else sv


def sample_segments(graph, segment_of, segments, rng_for, share, removable):
    """Ration the cycles of a groove graph segment by segment, so each segment is a pure function of its index.

    Even segments are tree segments. Odd segments are connectors. A connector sees each
    component of a neighbor tree segment as one virtual node. Each segment samples a spanning
    forest; of its other edges it keeps round(share * count) and cuts one `removable(edge)`
    block from each other one. An edge with no removable block always stays.
    Returns the removed blocks and the counts {"cycles", "kept", "unbreakable"}.
    """
    own = {}
    for i, e in enumerate(graph.edges):
        own.setdefault(segment_owner(segment_of(e.u), segment_of(e.v)), []).append(i)
    verts_of = {}
    for v in graph.vertices:
        verts_of.setdefault(segment_of(v), []).append(v)

    def components(t):
        uf = UnionFind()
        for v in verts_of.get(t, ()):
            uf.find(akey(v))
        for i in own.get(t, ()):
            e = graph.edges[i]
            uf.union(akey(e.u), akey(e.v))
        return uf

    removed = []
    stats = {"cycles": 0, "kept": 0, "unbreakable": 0}
    for s in segments:
        rng = rng_for(s)
        if s % 2:
            left, right = components(s - 1), components(s + 1)
        else:
            left = right = None

        def key(v):
            t = segment_of(v)
            if t == s:
                return (0,) + akey(v)
            if t == s - 1:
                return (1, 0) + left.find(akey(v))
            return (1, 1) + right.find(akey(v))

        idx = own.get(s, [])
        uf = UnionFind()
        for v in verts_of.get(s, ()):
            uf.find(key(v))
        options = {}
        free = []
        for i in idx:
            e = graph.edges[i]
            uf.find(key(e.u))
            uf.find(key(e.v))
            options[i] = removable(e)
            if options[i]:
                free.append(i)
            elif not uf.union(key(e.u), key(e.v)):
                stats["unbreakable"] += 1
        pairs = [(uf.find(key(graph.edges[i].u)), uf.find(key(graph.edges[i].v))) for i in free]
        nodes = sorted({uf.find(k) for k in list(uf.up)})
        tree = spanning_forest(nodes, pairs, rng)
        cotree = [free[j] for j in range(len(free)) if j not in tree]
        rng.shuffle(cotree)
        picks = [rng.choice(options[i]) for i in cotree]
        keep = int(share * len(cotree) + 0.5)
        stats["cycles"] += len(cotree)
        stats["kept"] += keep
        removed.extend(picks[keep:])
    return removed, stats


def lattice(band, region, nodes):
    """The straight links of each node inside one region.

    For each node and direction, the run of region blocks ends on another node (a link)
    or on a groove block outside the region (an exit). A corner node has no axis link.
    The result maps a node to tuples (direction, run blocks, target, is_exit).
    """
    links = {n: [] for n in nodes}
    for n in nodes:
        for d in DIRS:
            if d[1] != 0 and band.is_corner(n):
                continue
            run = []
            q = band.step(n, d)
            while q is not None:
                if q in nodes:
                    links[n].append((d, tuple(run), q, False))
                    break
                if q not in region:
                    if band.get(q) == GROOVE:
                        links[n].append((d, tuple(run), q, True))
                    break
                run.append(q)
                q = band.step(q, d)
    return links


def surface_candidates(band, region, nodes, max_turns=2):
    """Every surface path of one region: it leaves a groove, passes nodes, and ends on a groove or on a node.

    A path has at most `max_turns` corners. The blocks run from the start groove onward.
    `ends` holds the start groove, and the end groove when there is one. The list has no duplicate.
    """
    links = lattice(band, region, nodes)
    found = {}

    def record(blocks, ends, turns):
        k = (frozenset(blocks), frozenset(ends))
        if k not in found:
            found[k] = SurfacePath(tuple(blocks), tuple(ends), turns)

    def walk(u, din, blocks, visited, turns, g1):
        record(blocks, (g1,), turns)
        for d, run, t, is_exit in links[u]:
            if d == (-din[0], -din[1]):
                continue
            t2 = turns + (d != din)
            if t2 > max_turns:
                continue
            if is_exit:
                record(blocks + list(run), (g1, t), t2)
            elif t not in visited:
                walk(t, d, blocks + list(run) + [t], visited | {t}, t2, g1)

    for n in sorted(nodes, key=akey):
        for d, run, g, is_exit in links[n]:
            if is_exit:
                walk(n, (-d[0], -d[1]), list(reversed(run)) + [n], {n}, 0, g)
    return sorted(found.values(), key=lambda p: [akey(b) for b in p.blocks + p.ends])


def fits(band, path):
    """True when the path can go on the band without a 2-wide path, a plus crossing or a corner run.

    Every path block must be wall now. A path block may touch path blocks only at its
    own neighbors in the path and at its end grooves.
    """
    blocks = path.blocks
    own = set(blocks)
    if len(own) != len(blocks):
        return False
    if any(band.get(b) != WALL for b in blocks):
        return False
    seq = [path.ends[0]] + list(blocks) + list(path.ends[1:])
    last = len(blocks)
    for i in range(1, last + 1):
        allowed = {seq[i - 1]}
        if i + 1 < len(seq):
            allowed.add(seq[i + 1])
        for q in neighbors(band, seq[i]):
            if (q in own or is_path(band.get(q))) and q not in allowed:
                return False
    for a, b in zip(seq, seq[1:]):
        if not linked(band, a, b):
            return False

    def on(q):
        return q in own or is_path(band.get(q))

    for g in path.ends:
        if sum(1 for q in neighbors(band, g) if on(q)) >= 4:
            return False
    for b in blocks:
        for dr in (0, -1):
            for da in (0, -1):
                p0 = (b[0] + dr, b[1] + da)
                square = [(p0[0] + x, p0[1] + y) for x in (0, 1) for y in (0, 1)]
                if all(band.inside(q) and on((q[0] % band.ring, q[1])) for q in square):
                    return False
    return True


def pick_path(band, candidates, rng, dead_end_share=0.5):
    """Pick one fitting candidate, or None: a groove-to-node path with chance `dead_end_share`, else groove-to-groove.

    The draw falls back to the other class when one class has no fitting path.
    """
    ok = [p for p in candidates if fits(band, p)]
    through = [p for p in ok if len(p.ends) == 2]
    dead = [p for p in ok if len(p.ends) == 1]
    want_dead = rng.random() < dead_end_share
    pool = (dead or through) if want_dead else (through or dead)
    if not pool:
        return None
    return pool[rng.randrange(len(pool))]


def place(band, path, cut):
    """Write the path blocks: grooves when `cut`, else surface path blocks."""
    for b in path.blocks:
        band.set(b, GROOVE if cut else SURFACE)


# Channel values of the reference images in prototypes/reference/.
RED = {GROOVE: 0, SURFACE: 128, WALL: 255, BOX: 255}


def channel_image(band):
    """The reference color-channel image, one pixel per block, ring across and axis down.

    Red: 0 groove, 128 surface path, 255 wall. Green: 255 node, 128 node on a corner, 0 none.
    Blue: 0 corner column, 255 face interior, 128 box. Column 0 repeats at the right edge.
    """
    w = band.ring + 1
    img = np.zeros((band.length, w, 3), dtype=np.uint8)
    nodes = band.groove_nodes | band.face_nodes
    for x in range(w):
        r = x % band.ring
        corner = r in band.corners
        for y in range(band.length):
            p = (r, y + band.axis0)
            k = band.get(p)
            g = 0
            if p in nodes:
                g = 128 if corner else 255
            b = 128 if k == BOX else (0 if corner else 255)
            img[y, x] = (RED[k], g, b)
    return img


def write_channels(band, path):
    """Write the reference color-channel image of the band to a PNG file."""
    Image.fromarray(channel_image(band)).save(path)


COLORS = {
    WALL: ((168, 158, 146), (190, 182, 170)),
    GROOVE: ((44, 44, 52), (70, 70, 82)),
    SURFACE: ((212, 146, 66), (230, 170, 96)),
    BOX: ((116, 142, 194), (138, 162, 212)),
}
MARK = ((186, 120, 112), (204, 140, 132))
CHUNK_MARKS = ((210, 120, 40), (40, 120, 210))


def _shade(c, amount):
    return tuple(max(0, min(255, v + amount)) for v in c)


def band_image(band, scale=5, label=None, marks=(), chunk=16):
    """A readable render: walls raised, grooves dark, surface paths orange, box blocks blue.

    Ring across, axis down, column 0 repeated at the right edge. A raised block gets a light
    rim on its top and left groove edges and a dark rim on its bottom and right groove edges.
    `marks` holds wall blocks drawn in a red tint. A strip on the left alternates color per chunk.
    """
    w = band.ring + 1
    strip = 2 * scale
    rim = max(1, scale // 4)
    img = np.zeros((band.length * scale, strip + w * scale, 3), dtype=np.uint8)
    marks = set(marks)
    for y in range(band.length):
        a = y + band.axis0
        img[y * scale:(y + 1) * scale, :strip] = CHUNK_MARKS[(a // chunk) % 2]
        for x in range(w):
            p = (x % band.ring, a)
            k = band.get(p)
            corner = p[0] in band.corners
            base = (MARK if p in marks else COLORS[k])[1 if corner else 0]
            x0, y0 = strip + x * scale, y * scale
            img[y0:y0 + scale, x0:x0 + scale] = base
            if k == GROOVE:
                continue
            up = band.step(p, (0, -1))
            down = band.step(p, (0, 1))
            left = band.step(p, (-1, 0))
            right = band.step(p, (1, 0))
            if up is not None and band.get(up) == GROOVE:
                img[y0:y0 + rim, x0:x0 + scale] = _shade(base, 45)
            if left is not None and band.get(left) == GROOVE:
                img[y0:y0 + scale, x0:x0 + rim] = _shade(base, 45)
            if down is not None and band.get(down) == GROOVE:
                img[y0 + scale - rim:y0 + scale, x0:x0 + scale] = _shade(base, -60)
            if right is not None and band.get(right) == GROOVE:
                img[y0:y0 + scale, x0 + scale - rim:x0 + scale] = _shade(base, -60)
    out = Image.fromarray(img)
    if label:
        font = ImageFont.load_default()
        pad = 6
        box = ImageDraw.Draw(out).textbbox((0, 0), label, font=font)
        head = Image.new("RGB", (out.width, box[3] + 2 * pad), (236, 236, 236))
        ImageDraw.Draw(head).text((pad, pad), label, fill=(24, 24, 28), font=font)
        both = Image.new("RGB", (out.width, head.height + out.height))
        both.paste(head, (0, 0))
        both.paste(out, (0, head.height))
        out = both
    return out


def write_band(band, path, scale=5, label=None, marks=(), chunk=16):
    """Write the readable render of the band to a PNG file."""
    band_image(band, scale, label, marks, chunk).save(path)
