#!/usr/bin/env python3
"""Junction model for the Aerolite maze prototype.

One column crosses one X shaft. The junction is a 15x15x15 box at the
crossing. The box exposes two patches, its +Z face and its -Z face. The
seed gives one patch to the shaft and the other to the column.

Shaft: a junction segment of two chunks covers the box with a margin of
one ruling on each side. Its graph is the rulings of both chunks minus
the box rulings, plus the shaft patch nodes, plus the crossings from the
patch border onto the nearest rulings. One tree spans all of it. The
segment keeps the global chunk alternation: the chunk with the odd index
gives it a virtual node on that side.

Column: the pieces below and above the box are trees. The column patch
samples one tree over its nodes plus one virtual node per piece.

After sampling, every corner node of degree one is dropped with its edge.

The cut model is kept for comparison. It samples plain segments through
the box, cuts them, and rejoins the stub components through the patch.
Its stats show how many components stay stranded.

Every scene takes the bias P and the rule of originshift.py. A patch
crossing and a patch edge along the shaft axis are axis edges. A patch edge
across the shaft axis is a ring edge. On a column the axis is Y.

Every scene also takes the start tree and the scaled steps of originshift.py.
A curve start runs over each block of rulings that axis edges join. The
patch nodes and the virtual nodes join through the union-find pass.

The chunk paint writes the carves of the junction scene chunk by chunk, like
the mod. A chunk is 16 blocks in X and Z. The surface rule skips a carve whose
surface block is outside the chunk. The each rule writes each block of a carve
that is inside the chunk. The exposed interior blocks are the maze path blocks
that a rule does not write.

The bezel option sets the thickness of the solid frame on each patch face. A
bezel of 1 is the rim of the earlier model, with a 7x7 node patch. A bezel of
3 leaves a 9x9 area with a 5x5 node patch. A crossing notches through the
bezel onto a border node. Without the corner crossings flag, the patch corner
nodes get no crossing.

Every scene takes the pattern type and the pattern strength of originshift.py.
A pattern needs Wilson's algorithm with the degree rule. The stats also print
the tree state of each pillar over all of its nodes.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-28_22-52_72657c6a-f5b7-4adf-9364-cab19a14fc12.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-31_06-01_7b2c6d29-0ea3-4452-a1db-06e54416a4d5.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md
"""

import argparse
import os
import random

from originshift import (
    AXIS,
    BFS_START,
    BLOCKS_AROUND,
    CLASS_RULE,
    DEFAULT_BIAS,
    DEFAULT_RULE,
    DEFAULT_STEPS,
    DEGREE_RULE,
    NODES_PER_CHUNK,
    PATTERNS,
    RANDOM_SEAM,
    RIBS_PATTERN,
    RING,
    RING_EDGE,
    SEAMS,
    STARTS,
    UNIFORM_PATTERN,
    Bias,
    Graph,
    bias_suffix,
    bias_value,
    check_pattern,
    check_scaled_steps,
    check_weighted_wilson,
    build_layout,
    build_segment,
    forest_state,
    is_corner,
    label_rows,
    label_scale,
    pattern_label,
    pattern_strength,
    pattern_suffix,
    prune_corner_leaves,
    pattern_panels,
    ring_offset,
    sample_edges,
    seg_rng,
    segment_kind,
    start_only_panels,
    start_panels,
    start_suffix,
    state_text,
    strength_value,
    with_pillar,
    write_png,
)

HALF = 7
CHUNK = 2 * NODES_PER_CHUNK
BEZELS = (1, 3)


class Patch:
    """Shape of one patch: the bezel thickness, the node grid and the crossing rows.

    The border node index is m. The patch nodes run from -m to m in both indices.
    """

    def __init__(self, bezel=1, corner_crossings=False):
        self.bezel = bezel
        self.corner_crossings = corner_crossings
        self.m = (HALF - bezel) // 2
        self.size = 2 * self.m + 1
        reach = self.m if corner_crossings else self.m - 1
        # A crossing lands on an interior ring node, at ring offset 2 * index.
        if 2 * reach >= HALF - 1:
            raise ValueError("a corner crossing lands on a shaft corner")
        self.crossings = range(-reach, reach + 1)

    def nodes(self):
        return range(-self.m, self.m + 1)

    def suffix(self):
        if self.bezel == 1:
            return ""
        return f"_bezel{self.bezel}" + ("_corners" if self.corner_crossings else "")


RIM = Patch()


def ring_index(first, second):
    """Inverse of ring_offset for node blocks, or -1."""
    for b in range(0, BLOCKS_AROUND, 2):
        if ring_offset(b) == (first, second):
            return b // 2
    return -1


def face_node(sign, k):
    """Ring index of the node at offset 2k on the +second or -second face."""
    return ring_index(2 * k, 6 * sign)


def odd_below(a):
    return a if a % 2 else a - 1


def odd_above(a):
    return a if a % 2 else a + 1


def ring_block_between(v1, v2):
    return 2 * min(v1, v2) + 1 if abs(v1 - v2) == 1 else BLOCKS_AROUND - 1


def patch_grid(g, axis_on_i, patch):
    """Add the patch nodes and their grid edges to g. Return index map.

    axis_on_i is True when the first patch index runs along the shaft axis.
    """
    first = len(g.adj)
    m, size = patch.m, patch.size
    for _ in range(size * size):
        g.add_node()
    index = {}
    for i in patch.nodes():
        for j in patch.nodes():
            index[(i, j)] = first + (i + m) * size + (j + m)
    for (i, j), n in index.items():
        if i + 1 <= m:
            g.add_edge(n, index[(i + 1, j)], AXIS if axis_on_i else RING_EDGE)
        if j + 1 <= m:
            g.add_edge(n, index[(i, j + 1)], RING_EDGE if axis_on_i else AXIS)
    g.patches.append((index, axis_on_i, m))
    return index


def tree_edges(edges, names):
    """Sampled edges as pairs of names. Nodes without a name are virtual."""
    return [(names.get(a, ("virtual", a)), names.get(b, ("virtual", b))) for a, b in edges]


def real_names(names):
    """The names of the maze nodes: every patch node and every ruling node that is not a corner."""
    return {n for n in names.values() if n[1] is not None and (n[0] == "patch" or not is_corner(n[1]))}


def plain_nodes(segments):
    """The non-corner ruling nodes of the plain shaft segments."""
    return {(CHUNK * c + 2 * u + 1, v) for c in segments for u in range(NODES_PER_CHUNK) for v in range(RING) if not is_corner(v)}


def pillar_state(scene):
    """forest_state of the scene pillar over all of its maze nodes."""
    return forest_state(scene.nodes, getattr(scene, "shaft_edges", None) or scene.edges)


class Scene:
    """One junction: a junction segment on the shaft, pieces and a patch on the column."""

    def __init__(self, seed, cx, cy, sampler, steps_per_node, bias, chunks_each_side=3, piece_rulings=12, cz=0, patch=RIM):
        self.seed, self.cx, self.cy, self.cz = seed, cx, cy, cz
        self.patch = patch
        self.sampler, self.spn = sampler, steps_per_node
        self.bias = with_pillar(bias, f"{seed}:connector")
        column_bias = with_pillar(bias, f"{seed}:column")
        bias = self.bias
        rng = random.Random(f"{seed}:junction:{cx}:{cy}")
        self.shaft_sign = rng.choice((1, -1))
        self.column_sign = -self.shaft_sign
        self.x_last = odd_below(cx - HALF - 1)
        self.x_first = odd_above(cx + HALF + 1)
        self.k = self.x_last // CHUNK
        self.k_end = self.x_first // CHUNK
        self.y_last = odd_below(cy - HALF - 1)
        self.y_first = odd_above(cy + HALF + 1)
        # The scene starts and ends on tree chunks, so no crossing leaves it.
        self.c_lo = (self.k - chunks_each_side) // 2 * 2
        self.c_hi = (self.k_end + chunks_each_side + 1) // 2 * 2
        self.segments = {}
        for c in range(self.c_lo, self.c_hi + 1):
            if self.k <= c <= self.k_end:
                continue
            self.segments[c] = build_segment(segment_kind("connector", c), NODES_PER_CHUNK, seg_rng(seed, "connector", c), sampler, steps_per_node, bias)
        self.shaft_edges = self._plain_shaft_edges()
        self.shaft_edges += self._junction_segment(seg_rng(seed, "junction", self.k))
        self.below = build_segment("tree", piece_rulings, seg_rng(seed, "piece", 0), sampler, steps_per_node, column_bias)
        self.above = build_segment("tree", piece_rulings, seg_rng(seed, "piece", 1), sampler, steps_per_node, column_bias)
        self.piece_rulings = piece_rulings
        self.column_edges = self._column_patch(rng, column_bias)

    def _plain_shaft_edges(self):
        edges = []
        for c, seg in self.segments.items():
            base = CHUNK * c
            for (u1, v1), (u2, v2) in seg.edges:
                edges.append(((base + 2 * u1 + 1, v1), (base + 2 * u2 + 1, v2)))
            for v in seg.left_cross:
                edges.append(((base - 1, v), (base + 1, v)))
            for v in seg.right_cross:
                edges.append(((base + CHUNK - 1, v), (base + CHUNK + 1, v)))
        return edges

    def junction_rulings(self):
        start = CHUNK * self.k + 1
        end = CHUNK * (self.k_end + 1)
        return [x for x in range(start, end, 2) if abs(x - self.cx) > HALF]

    def _junction_segment(self, rng):
        """Sample the junction segment. Return its tree edges in world names."""
        g = Graph()
        names = {}
        index = {}
        rulings = self.junction_rulings()
        for x in rulings:
            for v in range(RING):
                n = g.add_node()
                index[(x, v)] = n
                names[n] = (x, v)
        g.rulings = [[index[(x, v)] for v in range(RING)] for x in rulings]
        for x in rulings:
            for v in range(RING):
                g.add_edge(index[(x, v)], index[(x, (v + 1) % RING)], RING_EDGE)
                if (x + 2, v) in index and not is_corner(v):
                    g.add_edge(index[(x, v)], index[(x + 2, v)], AXIS)
        m = self.patch.m
        patch = patch_grid(g, True, self.patch)
        for (i, j), n in patch.items():
            names[n] = ("patch", i, j)
        low, high = [], []
        for j in self.patch.crossings:
            v = face_node(self.shaft_sign, j)
            g.add_edge(patch[(-m, j)], index[(self.x_last, v)], AXIS)
            g.add_edge(patch[(m, j)], index[(self.x_first, v)], AXIS)
            low.append((patch[(-m, j)], index[(self.x_last, v)]))
            high.append((patch[(m, j)], index[(self.x_first, v)]))
        g.groups += [low, high]
        x_start, x_end = rulings[0], rulings[-1]
        if self.k % 2 == 1:
            left = g.add_node()
            g.left = left
            names[left] = (x_start - 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(left, index[(x_start, v)], AXIS)
        if self.k_end % 2 == 1:
            right = g.add_node()
            g.right = right
            names[right] = (x_end + 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(right, index[(x_end, v)], AXIS)
        sampled = sample_edges(g, rng, self.sampler, self.spn, self.bias)
        edges = []
        for a, b in tree_edges(sampled, names):
            if a[1] is None:
                a = (a[0], b[1])
            if b[1] is None:
                b = (b[0], a[1])
            edges.append((a, b))
        return prune_corner_leaves(edges, lambda n: n[0] != "patch" and is_corner(n[1]))

    def _column_patch(self, rng, bias):
        """Sample the column patch. Return its tree edges in world names."""
        g = Graph()
        m = self.patch.m
        patch = patch_grid(g, False, self.patch)
        names = {n: ("patch", i, j) for (i, j), n in patch.items()}
        below = g.add_node()
        above = g.add_node()
        for i in self.patch.crossings:
            g.add_edge(below, patch[(i, -m)], AXIS)
            g.add_edge(above, patch[(i, m)], AXIS)
        g.groups += [[(patch[(i, -m)], below) for i in self.patch.crossings], [(patch[(i, m)], above) for i in self.patch.crossings]]
        sampled = sample_edges(g, rng, self.sampler, self.spn, bias)
        edges = []
        for a, b in tree_edges(sampled, names):
            if a[0] == "virtual":
                a, b = b, a
            if b[0] == "virtual":
                y = self.y_last if b[1] == below else self.y_first
                v = face_node(self.column_sign, a[1])
                b = (y, v)
            edges.append((a, b))
        return edges

    def carves(self, reach):
        """All (surface block, block beneath) pairs to carve."""
        out = []
        cx, cy, cz = self.cx, self.cy, self.cz
        rulings = set(self.junction_rulings())
        for c in self.segments:
            for u in range(NODES_PER_CHUNK):
                rulings.add(CHUNK * c + 2 * u + 1)
        present = {n for e in self.shaft_edges for n in e}
        for x in rulings:
            if abs(x - cx) <= reach:
                for v in range(RING):
                    if not is_corner(v) or (x, v) in present:
                        out.append(shaft_carve(self, x, 2 * v))
        for a, b in self.shaft_edges:
            out += shaft_edge_carves(self, a, b, self.shaft_sign, reach)
        for sign in (self.shaft_sign, self.column_sign):
            for i in self.patch.nodes():
                for j in self.patch.nodes():
                    out.append(patch_carve(self, cx + 2 * i, cy + 2 * j, sign))
        for seg, y0, step in ((self.below, self.y_last, -1), (self.above, self.y_first, 1)):
            present = {n for e in seg.edges for n in e}
            for u in range(seg.length):
                for v in range(RING):
                    if not is_corner(v) or (u, v) in present:
                        out.append(column_carve(self, y0 + step * 2 * u, 2 * v))
            for (u1, v1), (u2, v2) in seg.edges:
                if u1 == u2:
                    out.append(column_carve(self, y0 + step * 2 * u1, ring_block_between(v1, v2)))
                else:
                    out.append(column_carve(self, y0 + step * (2 * min(u1, u2) + 1), 2 * v1))
        for a, b in self.column_edges:
            out += column_edge_carves(self, a, b, self.column_sign)
        return [c for c in out if abs(c[0][0] - cx) <= reach and abs(c[0][1] - cy) <= reach]


def clamp5(a):
    return max(-5, min(5, a))


def shaft_carve(scene, x, b):
    dy, dz = ring_offset(b)
    return ((x, scene.cy + dy, scene.cz + dz), (x, scene.cy + clamp5(dy), scene.cz + clamp5(dz)))


def column_carve(scene, y, b):
    dx, dz = ring_offset(b)
    return ((scene.cx + dx, y, scene.cz + dz), (scene.cx + clamp5(dx), y, scene.cz + clamp5(dz)))


def patch_carve(scene, x, y, sign):
    zf, zb = scene.cz + sign * HALF, scene.cz + sign * (HALF - 1)
    return ((x, y, zf), (x, y, zb))


def notch(center, side, patch):
    """The face offsets of a crossing through the bezel, from the box edge inward."""
    return [center + side * (HALF - d) for d in range(patch.bezel)]


def shaft_edge_carves(scene, a, b, sign, reach):
    """Carve list for one tree edge of the shaft, patch nodes included."""
    cx, cy, cz = scene.cx, scene.cy, scene.cz
    if a[0] == "patch" and b[0] == "patch":
        return [patch_carve(scene, cx + a[1] + b[1], cy + a[2] + b[2], sign)]
    if a[0] == "patch" or b[0] == "patch":
        p, n = (a, b) if a[0] == "patch" else (b, a)
        x, v = n
        y = cy + 2 * p[2]
        out = [patch_carve(scene, cx + 2 * p[1], y, sign)]
        if x < cx:
            out += [((xx, y, cz + sign * 6), (xx, y, cz + sign * 5)) for xx in range(x + 1, cx - HALF)]
            out += [patch_carve(scene, xx, y, sign) for xx in notch(cx, -1, scene.patch)]
        else:
            out += [((xx, y, cz + sign * 6), (xx, y, cz + sign * 5)) for xx in range(cx + HALF + 1, x)]
            out += [patch_carve(scene, xx, y, sign) for xx in notch(cx, 1, scene.patch)]
        return out
    (x1, v1), (x2, v2) = a, b
    if x1 == x2:
        return [shaft_carve(scene, x1, ring_block_between(v1, v2))]
    return [shaft_carve(scene, min(x1, x2) + 1, 2 * v1)]


def column_edge_carves(scene, a, b, sign):
    cx, cy, cz = scene.cx, scene.cy, scene.cz
    if a[0] == "patch" and b[0] == "patch":
        return [patch_carve(scene, cx + a[1] + b[1], cy + a[2] + b[2], sign)]
    p, n = (a, b) if a[0] == "patch" else (b, a)
    y, v = n
    x = cx + 2 * p[1]
    out = [patch_carve(scene, x, cy + 2 * p[2], sign)]
    if y < cy:
        out += [((x, yy, cz + sign * 6), (x, yy, cz + sign * 5)) for yy in range(y + 1, cy - HALF)]
        out += [patch_carve(scene, x, yy, sign) for yy in notch(cy, -1, scene.patch)]
    else:
        out += [((x, yy, cz + sign * 6), (x, yy, cz + sign * 5)) for yy in range(cy + HALF + 1, y)]
        out += [patch_carve(scene, x, yy, sign) for yy in notch(cy, 1, scene.patch)]
    return out


# --- cut model, kept for comparison -------------------------------------------


class UnionFind:
    def __init__(self):
        self.parent = {}

    def find(self, a):
        self.parent.setdefault(a, a)
        while self.parent[a] != a:
            self.parent[a] = self.parent[self.parent[a]]
            a = self.parent[a]
        return a

    def union(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[ra] = rb


class CutScene:
    """Plain segments through the box, cut at the box, rejoined through the patch."""

    def __init__(self, seed, cx, cy, sampler, steps_per_node, bias, chunks_each_side=4, patch=RIM):
        self.cx = cx
        self.patch = patch
        rng = random.Random(f"{seed}:junction:{cx}:{cy}")
        self.shaft_sign = rng.choice((1, -1))
        self.c_lo = (cx - HALF) // CHUNK - chunks_each_side
        self.c_hi = (cx + HALF) // CHUNK + chunks_each_side
        segs = build_layout("connector", seed, self.c_hi - self.c_lo + 1, NODES_PER_CHUNK, sampler, steps_per_node, bias, self.c_lo)
        self.segments = dict(zip(range(self.c_lo, self.c_hi + 1), segs))
        self.x_last = odd_below(cx - HALF - 1)
        self.x_first = odd_above(cx + HALF + 1)
        self.shaft_edges = Scene._plain_shaft_edges(self)

    def in_stub(self, side, x):
        return x <= self.x_last if side == "left" else x >= self.x_first

    def components(self, side, window):
        edge_x = self.x_last if side == "left" else self.x_first
        chunk = edge_x // CHUNK
        chunks = set(self.segments) if window is None else {chunk + d for d in range(-(window // 2), window // 2 + 1)}
        uf = UnionFind()
        for a, b in self.shaft_edges:
            if not (self.in_stub(side, a[0]) and self.in_stub(side, b[0])):
                continue
            if a[0] // CHUNK not in chunks or b[0] // CHUNK not in chunks:
                continue
            uf.union(a, b)
        key = {v: uf.find((edge_x, v)) for v in range(RING)}
        sizes = {}
        far = set()
        for node in list(uf.parent):
            root = uf.find(node)
            sizes[root] = sizes.get(root, 0) + 1
            if node[0] // CHUNK in (self.c_lo, self.c_hi):
                far.add(root)
        return key, sizes, far

    @staticmethod
    def partition(key):
        groups = {}
        for v, root in key.items():
            groups.setdefault(root, set()).add(v)
        return frozenset(frozenset(g) for g in groups.values())

    def report(self):
        out = {}
        for side in ("left", "right"):
            key, sizes, far = self.components(side, None)
            key3, _, _ = self.components(side, 3)
            roots = set(key.values())
            touching = {key[face_node(self.shaft_sign, j)] for j in self.patch.crossings}
            isolated = roots - touching
            out[side] = {
                "components": len(roots),
                "rejoined": len(touching),
                "isolated": len(isolated),
                "isolated_far": len(isolated & far),
                "isolated_nodes": sum(sizes[r] for r in isolated),
                "window3_agrees": self.partition(key) == self.partition(key3),
            }
        return out


def cut_stats(seed, count, sampler, spn, bias, patch):
    print("junction  cx   cy  side   comps  rejoined  isolated  isolated_far  isolated_nodes  window3")
    totals = {"components": 0, "rejoined": 0, "isolated": 0, "isolated_far": 0, "isolated_nodes": 0, "window3": 0}
    rows = 0
    for k in range(count):
        rng = random.Random(f"{seed}:place:{k}")
        cx = rng.randrange(100, 400)
        cy = rng.randrange(40, 200)
        scene = CutScene(seed, cx, cy, sampler, spn, bias, patch=patch)
        for side, r in scene.report().items():
            print(f"{k:>8} {cx:>4} {cy:>4}  {side:<6} {r['components']:>5} {r['rejoined']:>9} {r['isolated']:>9} {r['isolated_far']:>13} {r['isolated_nodes']:>15}  {r['window3_agrees']}")
            for key in ("components", "rejoined", "isolated", "isolated_far", "isolated_nodes"):
                totals[key] += r[key]
            totals["window3"] += r["window3_agrees"]
            rows += 1
    print()
    print(f"mean components per stub: {totals['components']/rows:.2f}")
    print(f"mean rejoined per stub:   {totals['rejoined']/rows:.2f}")
    print(f"mean isolated per stub:   {totals['isolated']/rows:.2f}")
    print(f"isolated that reach the window edge: {totals['isolated_far']} of {totals['isolated']}")
    print(f"mean isolated nodes per stub: {totals['isolated_nodes']/rows:.1f}")
    print(f"three-segment window agrees: {totals['window3']} of {rows}")


# --- cluster model ---------------------------------------------------------------


def junction_chunks(c):
    """First and last chunk of the junction segment of one box centered at c."""
    return odd_below(c - HALF - 1) // CHUNK, odd_above(c + HALF + 1) // CHUNK


class ClusterScene:
    """Boxes on one shaft whose junction segments share a chunk. One junction segment spans them all."""

    def __init__(self, seed, centers, sampler, steps_per_node, bias, chunks_each_side=3, patch=RIM):
        self.centers = sorted(centers)
        self.patch = patch
        bias = with_pillar(bias, f"{seed}:connector")
        rng = random.Random(f"{seed}:cluster:{self.centers}")
        self.signs = [rng.choice((1, -1)) for _ in self.centers]
        self.k = min(junction_chunks(c)[0] for c in self.centers)
        self.k_end = max(junction_chunks(c)[1] for c in self.centers)
        self.c_lo = (self.k - chunks_each_side) // 2 * 2
        self.c_hi = (self.k_end + chunks_each_side + 1) // 2 * 2
        self.segments = {}
        for c in range(self.c_lo, self.c_hi + 1):
            if self.k <= c <= self.k_end:
                continue
            self.segments[c] = build_segment(segment_kind("connector", c), NODES_PER_CHUNK, seg_rng(seed, "connector", c), sampler, steps_per_node, bias)
        self.nodes = plain_nodes(self.segments)
        self.shaft_edges = Scene._plain_shaft_edges(self)
        self.shaft_edges += self._junction_segment(seg_rng(seed, "junction", self.k), sampler, steps_per_node, bias)

    def rulings(self):
        start = CHUNK * self.k + 1
        end = CHUNK * (self.k_end + 1)
        return [x for x in range(start, end, 2) if all(abs(x - c) > HALF for c in self.centers)]

    def _junction_segment(self, rng, sampler, steps_per_node, bias):
        g = Graph()
        names = {}
        index = {}
        rulings = self.rulings()
        for x in rulings:
            for v in range(RING):
                n = g.add_node()
                index[(x, v)] = n
                names[n] = (x, v)
        g.rulings = [[index[(x, v)] for v in range(RING)] for x in rulings]
        for x in rulings:
            for v in range(RING):
                g.add_edge(index[(x, v)], index[(x, (v + 1) % RING)], RING_EDGE)
                if (x + 2, v) in index and not is_corner(v):
                    g.add_edge(index[(x, v)], index[(x + 2, v)], AXIS)
        m = self.patch.m
        for b, (c, sign) in enumerate(zip(self.centers, self.signs)):
            patch = patch_grid(g, True, self.patch)
            for (i, j), n in patch.items():
                names[n] = ("patch", b, i, j)
            x_last, x_first = odd_below(c - HALF - 1), odd_above(c + HALF + 1)
            low, high = [], []
            for j in self.patch.crossings:
                v = face_node(sign, j)
                g.add_edge(patch[(-m, j)], index[(x_last, v)], AXIS)
                g.add_edge(patch[(m, j)], index[(x_first, v)], AXIS)
                low.append((patch[(-m, j)], index[(x_last, v)]))
                high.append((patch[(m, j)], index[(x_first, v)]))
            g.groups += [low, high]
        x_start, x_end = rulings[0], rulings[-1]
        if self.k % 2 == 1:
            left = g.add_node()
            g.left = left
            names[left] = (x_start - 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(left, index[(x_start, v)], AXIS)
        if self.k_end % 2 == 1:
            right = g.add_node()
            g.right = right
            names[right] = (x_end + 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(right, index[(x_end, v)], AXIS)
        self.nodes |= real_names(names)
        sampled = sample_edges(g, rng, sampler, steps_per_node, bias)
        edges = []
        for a, b in tree_edges(sampled, names):
            if a[1] is None:
                a = (a[0], b[1])
            if b[1] is None:
                b = (b[0], a[1])
            edges.append((a, b))
        return prune_corner_leaves(edges, lambda n: n[0] != "patch" and is_corner(n[1]))


class PatternTally:
    """Tree states of the pillars of one pattern stats run."""

    def __init__(self, bias):
        self.bias = bias
        self.active = bias.pattern != UNIFORM_PATTERN
        self.count = self.trees = self.components = self.loops = 0

    def quiet(self):
        """At strength 1 the pattern edges paint as they are, so a scene that is not one tree is expected."""
        return self.active and pattern_strength(self.bias) >= 1.0

    def add(self, scene):
        if not self.active:
            return
        _, _, components, loops = pillar_state(scene)
        self.count += 1
        self.trees += components == 1 and loops == 0
        self.components += components
        self.loops += loops

    def report(self, what):
        if not self.active:
            return
        n = self.count
        print(f"pattern {pattern_label(self.bias)} strength {self.bias.strength:g}: {what} that are one tree with every maze node: {self.trees} of {n}, mean components {self.components / n:.2f}, mean loops {self.loops / n:.2f}")


def cluster_stats(seed, count, sampler, spn, bias, patch):
    """Place two boxes at a random distance of 31 to 60 blocks and check that the shaft stays one tree."""
    merged = 0
    trees = 0
    tally = PatternTally(bias)
    for k in range(count):
        rng = random.Random(f"{seed}:cluster-place:{k}")
        c1 = rng.randrange(100, 400)
        c2 = c1 + rng.randrange(31, 61)
        if junction_chunks(c1)[1] >= junction_chunks(c2)[0]:
            merged += 1
            scene = ClusterScene(seed, [c1, c2], sampler, spn, bias, patch=patch)
        else:
            scene = ClusterScene(seed, [c1], sampler, spn, bias, patch=patch)
        result = check_tree(scene)
        tally.add(scene)
        if result.endswith(" 1 component(s)"):
            trees += 1
        elif not tally.quiet():
            print(f"{k:>4} {c1:>4} {c2:>4}  {result}")
    print(f"merged clusters: {merged} of {count}")
    print(f"scenes that are one tree: {trees} of {count}")
    tally.report("scenes")


# --- segmented column -----------------------------------------------------------


class ColumnScene:
    """One full-height column cut into 16-block sections, like a horizontal shaft.

    Tree sections and connector sections alternate. A virtual node only faces a
    section inside the build height. The column junction segment holds the column
    patch, with crossings from the patch border onto the nearest rulings.
    """

    def __init__(self, seed, centers, signs, y_min, y_max, sampler, steps_per_node, bias, patch=RIM):
        self.centers, self.signs = centers, signs
        self.patch = patch
        self.y_min, self.y_max = y_min, y_max
        self.s_lo, self.s_hi = y_min // CHUNK, (y_max - 1) // CHUNK
        self.sampler, self.spn, self.bias = sampler, steps_per_node, with_pillar(bias, f"{seed}:column")
        self.nodes = set()
        ranges = sorted(junction_chunks(c) for c in centers)
        clusters = []
        for lo, hi in ranges:
            if clusters and lo <= clusters[-1][1]:
                clusters[-1][1] = max(clusters[-1][1], hi)
            else:
                clusters.append([lo, hi])
        self.edges = []
        self.rulings = []
        # Kept and total curve edges of a curve start.
        self.curve = [0, 0]
        covered = set()
        for lo, hi in clusters:
            lo, hi = max(lo, self.s_lo), min(hi, self.s_hi)
            covered.update(range(lo, hi + 1))
            boxes = [(c, g) for c, g in zip(centers, signs) if lo <= junction_chunks(c)[0] <= hi or lo <= junction_chunks(c)[1] <= hi]
            self._segment(lo, hi, boxes, seg_rng(seed, "column-junction", lo))
        for c in range(self.s_lo, self.s_hi + 1):
            if c not in covered:
                self._segment(c, c, [], seg_rng(seed, "column", c))

    def _segment(self, lo, hi, boxes, rng):
        g = Graph()
        names = {}
        index = {}
        rulings = [y for y in range(CHUNK * lo + 1, CHUNK * (hi + 1), 2) if all(abs(y - c) > HALF for c, _ in boxes)]
        self.rulings += rulings
        for y in rulings:
            for v in range(RING):
                n = g.add_node()
                index[(y, v)] = n
                names[n] = (y, v)
        g.rulings = [[index[(y, v)] for v in range(RING)] for y in rulings]
        for y in rulings:
            for v in range(RING):
                g.add_edge(index[(y, v)], index[(y, (v + 1) % RING)], RING_EDGE)
                if (y + 2, v) in index and not is_corner(v):
                    g.add_edge(index[(y, v)], index[(y + 2, v)], AXIS)
        m = self.patch.m
        for c, sign in boxes:
            patch = patch_grid(g, False, self.patch)
            for (i, j), n in patch.items():
                names[n] = ("patch", c, i, j)
            low, high = [], []
            for i in self.patch.crossings:
                v = face_node(sign, i)
                if (odd_below(c - HALF - 1), v) in index:
                    g.add_edge(patch[(i, -m)], index[(odd_below(c - HALF - 1), v)], AXIS)
                    low.append((patch[(i, -m)], index[(odd_below(c - HALF - 1), v)]))
                if (odd_above(c + HALF + 1), v) in index:
                    g.add_edge(patch[(i, m)], index[(odd_above(c + HALF + 1), v)], AXIS)
                    high.append((patch[(i, m)], index[(odd_above(c + HALF + 1), v)]))
            g.groups += [low, high]
        if lo % 2 == 1 and lo - 1 >= self.s_lo:
            left = g.add_node()
            g.left = left
            names[left] = (rulings[0] - 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(left, index[(rulings[0], v)], AXIS)
        if hi % 2 == 1 and hi + 1 <= self.s_hi:
            right = g.add_node()
            g.right = right
            names[right] = (rulings[-1] + 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(right, index[(rulings[-1], v)], AXIS)
        self.nodes |= real_names(names)
        trace = {}
        sampled = sample_edges(g, rng, self.sampler, self.spn, self.bias, trace)
        if "curve" in trace:
            self.curve[0] += trace["curve"][0]
            self.curve[1] += trace["curve"][1]
        edges = []
        for a, b in tree_edges(sampled, names):
            if a[1] is None:
                a = (a[0], b[1])
            if b[1] is None:
                b = (b[0], a[1])
            edges.append((a, b))
        self.edges += prune_corner_leaves(edges, lambda n: n[0] != "patch" and is_corner(n[1]))

    def band(self):
        """Path grid of the column surface, indexed [ring block][y - y_min]. Box rows are None."""
        height = self.y_max - self.y_min
        grid = [[False] * height for _ in range(BLOCKS_AROUND)]
        present = {n for e in self.edges for n in e}
        for y in self.rulings:
            for v in range(RING):
                if not is_corner(v) or (y, v) in present:
                    grid[2 * v][y - self.y_min] = True
        for a, b in self.edges:
            if a[0] == "patch" or b[0] == "patch":
                continue
            (y1, v1), (y2, v2) = a, b
            if y1 == y2:
                grid[ring_block_between(v1, v2)][y1 - self.y_min] = True
            else:
                grid[2 * v1][min(y1, y2) + 1 - self.y_min] = True
        for c, _ in zip(self.centers, self.signs):
            for y in range(c - HALF, c + HALF + 1):
                for b in range(BLOCKS_AROUND):
                    grid[b][y - self.y_min] = None
        return grid


def column_band_rows(scene, scale):
    """Pixel rows of the unwrapped column surface, bottom row at the bottom. Junction boxes have a blue tint."""
    grid = scene.band()
    rows = []
    for y in range(len(grid[0]) - 1, -1, -1):
        row = []
        for b in range(BLOCKS_AROUND):
            cell = grid[b][y]
            corner = b % (BLOCKS_AROUND // 4) == 0
            if cell is None:
                c = (120, 126, 150)
            elif cell:
                c = (84, 84, 94) if corner else (58, 58, 66)
            else:
                c = (176, 176, 176) if corner else (150, 150, 150)
            row.extend(c * scale)
        for _ in range(scale):
            rows.append(row)
    return rows


def render_column_band(scene, path, scale):
    rows = column_band_rows(scene, scale)
    write_png(path, BLOCKS_AROUND * scale, len(rows), rows)


COLUMN_GAP = 16
GAP_COLOR = (255, 255, 255)


def column_scene(seed, sampler, steps_per_node, bias, patch=RIM):
    """The column of the column band: two junctions on the full Overworld height."""
    return ColumnScene(seed, [-56, 56], [1, 1], -64, 320, sampler, steps_per_node, bias, patch)


def side_by_side(path, panels):
    """Put panels of equal height side by side with a white gap."""
    gap = bytes(GAP_COLOR * COLUMN_GAP)
    rows = [gap.join(bytes(panel[y]) for panel in panels) for y in range(len(panels[0]))]
    write_png(path, len(rows[0]) // 3, len(rows), rows)


def render_column_compare(path, seed, steps_per_node, p, scale, patch):
    """Put the column band of the class rule and the degree rule side by side, each under its label strip."""
    panels = []
    for label, rule in ((f"class {p:g}", CLASS_RULE), (f"degree {p:g}", DEGREE_RULE)):
        scene = column_scene(seed, "originshift", steps_per_node, Bias(p, rule), patch)
        print(f"{rule} column tree:", check_tree(scene))
        band = column_band_rows(scene, scale)
        panels.append(label_rows(label, len(band[0]) // 3) + band)
    side_by_side(path, panels)


def column_band_name(sampler, bias, steps_per_node, patch):
    """Wilson's algorithm adds _wilson, so its band keeps its own file."""
    tag = "_wilson" if sampler == "wilson" else ""
    return f"column_band{tag}{bias_suffix(sampler, bias)}{start_suffix(sampler, bias, steps_per_node)}{pattern_suffix(bias)}{patch.suffix()}.png"


def render_column_starts(out_dir, name, seed, panels, scale, band_scale, patch):
    """Put the column band of each panel side by side, each under its label strip. Write each band at band_scale too."""
    width = BLOCKS_AROUND * scale
    glyphs = label_scale([label for label, _, _, _ in panels], width)
    images = []
    for label, sampler, bias, spn in panels:
        scene = column_scene(seed, sampler, spn, bias, patch)
        band_name = column_band_name(sampler, bias, spn, patch)
        report_column(label, scene)
        render_column_band(scene, os.path.join(out_dir, band_name), band_scale)
        print("wrote", band_name)
        images.append(label_rows(label, width, glyphs) + column_band_rows(scene, scale))
    side_by_side(os.path.join(out_dir, name), images)
    print("wrote", name)


def render_column_patterns(out_dir, seed, p, scale, band_scale, patch, pattern, seam):
    """Put the column band of each strength of one pattern side by side, each under its label strip. Write each band too."""
    panels = [(label, "wilson", bias, DEFAULT_STEPS) for label, bias in pattern_panels(p, pattern, seam)]
    seam_part = f"_{seam}" if pattern == RIBS_PATTERN and seam != RANDOM_SEAM else ""
    name = f"compare_pattern_column_{pattern}{seam_part}_bias{p:g}{patch.suffix()}.png"
    render_column_starts(out_dir, name, seed, panels, scale, band_scale, patch)


def report_column(label, scene):
    """Print the tree check and, for a curve start, the share of curve edges that the start keeps."""
    line = f"{label}: {check_tree(scene)}"
    kept, total = scene.curve
    if total:
        line += f", curve edges kept {kept} of {total} ({kept / total:.3f})"
    print(line)
    if scene.bias.pattern != UNIFORM_PATTERN:
        print(f"{label} with every maze node: {state_text(pillar_state(scene))}")


def column_stats(seed, count, sampler, spn, bias, patch):
    """Place two junctions on a full-height column and check that the column stays one tree."""
    trees = 0
    tally = PatternTally(bias)
    for k in range(count):
        rng = random.Random(f"{seed}:column-place:{k}")
        low = rng.randrange(-64 + 24, 300 - 40)
        high = low + rng.randrange(17, 120)
        high = min(high, 320 - 24)
        scene = ColumnScene(seed + k, [low, high], [rng.choice((1, -1)), rng.choice((1, -1))], -64, 320, sampler, spn, bias, patch)
        result = check_tree(scene)
        tally.add(scene)
        if result.endswith(" 1 component(s)"):
            trees += 1
        elif not tally.quiet():
            print(f"{k:>4} {low:>4} {high:>4}  {result}")
    print(f"columns that are one tree: {trees} of {count}")
    tally.report("columns")


# --- checks and rendering -------------------------------------------------------


def check_tree(scene):
    """Confirm the shaft edges form one tree over the scene and report counts."""
    uf = UnionFind()
    nodes = set()
    for a, b in getattr(scene, "shaft_edges", None) or scene.edges:
        nodes.add(a)
        nodes.add(b)
        if uf.find(a) == uf.find(b):
            return "loop found"
        uf.union(a, b)
    roots = {uf.find(n) for n in nodes}
    return f"{len(nodes)} nodes, {len(getattr(scene, 'shaft_edges', None) or scene.edges)} edges, {len(roots)} component(s)"


AIR, STONE, BRICK, COBBLE, BOX, EXPOSED = 0, 1, 2, 3, 4, 5
COLORS = {STONE: (128, 128, 128), BRICK: (160, 152, 142), COBBLE: (92, 96, 102), BOX: (120, 126, 134), EXPOSED: (210, 40, 40)}


def fill_voxels(scene, reach):
    """The shaft, the column and the box of the scene, with no carve."""
    blocks = {}
    cx, cy, cz = scene.cx, scene.cy, scene.cz
    for x in range(cx - reach, cx + reach + 1):
        for dy in range(-6, 7):
            for dz in range(-6, 7):
                blocks[(x, cy + dy, cz + dz)] = BRICK if max(abs(dy), abs(dz)) == 6 else STONE
    for y in range(cy - reach, cy + reach + 1):
        for dx in range(-6, 7):
            for dz in range(-6, 7):
                blocks[(cx + dx, y, cz + dz)] = BRICK if max(abs(dx), abs(dz)) == 6 else STONE
    for dx in range(-HALF, HALF + 1):
        for dy in range(-HALF, HALF + 1):
            for dz in range(-HALF, HALF + 1):
                blocks[(cx + dx, cy + dy, cz + dz)] = BOX
    return blocks


def build_voxels(scene, reach):
    """The scene with every carve written, with no chunk edge."""
    blocks = fill_voxels(scene, reach)
    for surface, beneath in scene.carves(reach):
        blocks[surface] = AIR
        blocks[beneath] = COBBLE
    return blocks


# --- chunk paint ---------------------------------------------------------------

SURFACE_RULE = "surface"
EACH_RULE = "each"
CHUNK_RULES = (SURFACE_RULE, EACH_RULE)


def chunk_of(pos):
    """The chunk of a block, as its X and Z chunk coordinates."""
    return pos[0] // CHUNK, pos[2] // CHUNK


def paint_chunk(blocks, chunk, carves, rule):
    """Write the carves into the blocks of one chunk, like one chunk paint of the mod.

    The surface rule skips a carve whose surface block is outside the chunk.
    The each rule writes each block of a carve that is inside the chunk.
    """
    for surface, beneath in carves:
        if rule == SURFACE_RULE and chunk_of(surface) != chunk:
            continue
        if chunk_of(surface) == chunk:
            blocks[surface] = AIR
        if chunk_of(beneath) == chunk:
            blocks[beneath] = COBBLE


def build_chunk_voxels(scene, reach, rule):
    """The scene painted chunk by chunk under one rule."""
    blocks = fill_voxels(scene, reach)
    carves = scene.carves(reach)
    for chunk in sorted({chunk_of(p) for p in blocks}):
        paint_chunk(blocks, chunk, carves, rule)
    return blocks


def exposed_interior(blocks, reference):
    """The maze path blocks of the reference that the blocks do not hold."""
    return sorted(p for p, m in reference.items() if m == COBBLE and blocks.get(p) != COBBLE)


def chunk_stats(seed, cx, cy, sampler, spn, bias, reach, patch):
    """Print the exposed interior blocks of each rule for every shaft center Z inside one chunk."""
    print("cz  " + "  ".join(f"{rule:>8}" for rule in CHUNK_RULES))
    for cz in range(CHUNK):
        scene = Scene(seed, cx, cy, sampler, spn, bias, cz=cz, patch=patch)
        reference = build_voxels(scene, reach)
        counts = [len(exposed_interior(build_chunk_voxels(scene, reach, rule), reference)) for rule in CHUNK_RULES]
        print(f"{cz:>2}  " + "  ".join(f"{n:>8}" for n in counts))


def render_chunk_paint(out_dir, scene, reach, scale, suffix):
    """Render the chunk paint of each rule from both sides. The exposed interior blocks are red."""
    reference = build_voxels(scene, reach)
    for rule in CHUNK_RULES:
        blocks = build_chunk_voxels(scene, reach, rule)
        exposed = exposed_interior(blocks, reference)
        for p in exposed:
            blocks[p] = EXPOSED
        print(f"{rule} rule: {len(exposed)} exposed interior blocks")
        for flip, view in ((False, "front"), (True, "back")):
            name = f"chunk_paint_{rule}_{view}_cz{scene.cz}{suffix}.png"
            render_iso(blocks, os.path.join(out_dir, name), scale, flip)
            print("wrote", name)


def render_iso(blocks, path, scale, flip):
    """Isometric render with the +X, +Y and +Z faces visible. flip turns the scene by 180 degrees."""
    def tf(p):
        x, y, z = p
        return (-x, y, -z) if flip else (x, y, z)

    blocks = {tf(p): m for p, m in blocks.items() if m != AIR}
    k = 0.8660254

    def proj(x, y, z):
        return (x - z) * k * scale, (x + z) * 0.5 * scale - y * scale

    xs, ys = [], []
    for x, y, z in blocks:
        for dx, dy, dz in ((0, 0, 0), (1, 1, 1), (1, 0, 0), (0, 0, 1)):
            px, py = proj(x + dx, y + dy, z + dz)
            xs.append(px)
            ys.append(py)
    ox, oy = -min(xs) + 2, -min(ys) + 2
    width = int(max(xs) - min(xs)) + 5
    height = int(max(ys) - min(ys)) + 5
    rows = [bytearray((245, 245, 245) * width) for _ in range(height)]

    def fill(quad, color):
        pts = [(px + ox, py + oy) for px, py in quad]
        y_lo = int(min(p[1] for p in pts))
        y_hi = int(max(p[1] for p in pts)) + 1
        for yy in range(max(0, y_lo), min(height, y_hi + 1)):
            cuts = []
            for a in range(4):
                (x1, y1), (x2, y2) = pts[a], pts[(a + 1) % 4]
                if y1 == y2:
                    continue
                if min(y1, y2) <= yy + 0.5 < max(y1, y2):
                    cuts.append(x1 + (yy + 0.5 - y1) * (x2 - x1) / (y2 - y1))
            if len(cuts) < 2:
                continue
            x_lo = max(0, int(min(cuts) + 0.5))
            x_hi = min(width, int(max(cuts) + 0.5))
            row = rows[yy]
            for xx in range(x_lo, x_hi):
                row[3 * xx:3 * xx + 3] = color

    faces = (
        ((0, 1, 0), 1.0, ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1))),
        ((0, 0, 1), 0.84, ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))),
        ((1, 0, 0), 0.66, ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1))),
    )
    for x, y, z in sorted(blocks, key=lambda p: p[0] + p[1] + p[2]):
        base = COLORS[blocks[(x, y, z)]]
        for (nx, ny, nz), shade, corners in faces:
            if (x + nx, y + ny, z + nz) in blocks:
                continue
            color = bytes(int(c * shade) for c in base)
            fill([proj(x + a, y + b, z + c) for a, b, c in corners], color)
    write_png(path, width, height, rows)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--cx", type=int, default=203)
    ap.add_argument("--cy", type=int, default=70)
    ap.add_argument("--cz", type=int, default=0, help="Z of the shaft center; the chunk edges sit at multiples of 16")
    ap.add_argument("--sampler", choices=("originshift", "wilson"), default="originshift")
    ap.add_argument("--steps-per-node", type=int, default=DEFAULT_STEPS)
    ap.add_argument("--bias", type=bias_value, default=DEFAULT_BIAS, help="axis chance of the class rule or axis edge weight of the degree rule")
    ap.add_argument("--rule", choices=(CLASS_RULE, DEGREE_RULE), default=DEFAULT_RULE, help="the bias rule of the walker step")
    ap.add_argument("--start", choices=STARTS, default=BFS_START, help="the start tree of Origin Shift")
    ap.add_argument("--scaled-steps", action="store_true", help="divide the Origin Shift steps per node by min(P, 1 - P)")
    ap.add_argument("--reach", type=int, default=22, help="blocks of shaft and column on each side of the junction")
    ap.add_argument("--scale", type=int, default=9, help="pixels per block edge")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "images"))
    ap.add_argument("--cut-stats", action="store_true", help="measure the stranded components of the cut model and exit")
    ap.add_argument("--cluster-stats", action="store_true", help="check that two close boxes in one junction segment keep the shaft one tree and exit")
    ap.add_argument("--column-stats", action="store_true", help="check that a segmented column with two junctions is one tree and exit")
    ap.add_argument("--column-band", action="store_true", help="render the unwrapped surface of one segmented column and exit")
    ap.add_argument("--chunk-paint", action="store_true", help="paint the junction scene chunk by chunk under each rule, render the exposed interior blocks and exit")
    ap.add_argument("--chunk-stats", action="store_true", help="count the exposed interior blocks of each rule for every shaft center Z inside one chunk and exit")
    ap.add_argument("--count", type=int, default=40)
    ap.add_argument("--compare", action="store_true", help="render the column band of the class rule and the degree rule side by side and exit")
    ap.add_argument("--compare-starts", action="store_true", help="with --column-band, render the column band of each start tree side by side and exit")
    ap.add_argument("--bezel", type=int, choices=BEZELS, default=1, help="thickness of the solid frame on each patch face")
    ap.add_argument("--corner-crossings", action="store_true", help="give the patch corner nodes a crossing; needs --bezel 3")
    ap.add_argument("--pattern", choices=PATTERNS, default=UNIFORM_PATTERN, help="the pattern type of Wilson's algorithm")
    ap.add_argument("--strength", type=strength_value, default=0.0, help="the pattern strength, from 0 to 1 in steps of 0.05")
    ap.add_argument("--seam", choices=SEAMS, default=RANDOM_SEAM, help="the root of the ribs pattern")
    ap.add_argument("--compare-patterns", action="store_true", help="with --column-band, render the column band of the pattern at four strengths side by side and exit")
    args = ap.parse_args()
    bias = Bias(args.bias, args.rule, args.start, args.scaled_steps, args.pattern, args.strength, args.seam)
    if args.compare_patterns:
        if not (args.column_band and args.rule == DEGREE_RULE) or args.pattern == UNIFORM_PATTERN:
            ap.error("--compare-patterns needs --column-band, --rule degree and a --pattern other than uniform")
        if args.corner_crossings and args.bezel == 1:
            ap.error("--corner-crossings needs --bezel 3")
        check_pattern(ap, bias, "wilson")
        check_weighted_wilson(ap, True, args.bias)
        os.makedirs(args.out, exist_ok=True)
        render_column_patterns(args.out, args.seed, args.bias, 5, 4, Patch(args.bezel, args.corner_crossings), args.pattern, args.seam)
        return
    check_pattern(ap, bias, args.sampler)
    if args.pattern != UNIFORM_PATTERN and (args.compare or args.compare_starts):
        ap.error("--compare and --compare-starts take no --pattern")
    if args.corner_crossings and args.bezel == 1:
        ap.error("--corner-crossings needs --bezel 3")
    patch = Patch(args.bezel, args.corner_crossings)
    if args.compare_starts and not (args.column_band and args.rule == DEGREE_RULE):
        ap.error("--compare-starts needs --column-band and --rule degree")
    check_weighted_wilson(ap, args.compare_starts or (args.rule == DEGREE_RULE and args.sampler == "wilson"), args.bias)
    check_scaled_steps(ap, args.scaled_steps or args.compare_starts, args.bias)
    if args.cut_stats:
        cut_stats(args.seed, args.count, args.sampler, args.steps_per_node, bias, patch)
        return
    if args.chunk_stats:
        chunk_stats(args.seed, args.cx, args.cy, args.sampler, args.steps_per_node, bias, args.reach, patch)
        return
    if args.cluster_stats:
        cluster_stats(args.seed, args.count, args.sampler, args.steps_per_node, bias, patch)
        return
    if args.column_stats:
        column_stats(args.seed, args.count, args.sampler, args.steps_per_node, bias, patch)
        return
    if args.compare:
        os.makedirs(args.out, exist_ok=True)
        name = f"compare_column_bias{args.bias:g}{patch.suffix()}.png"
        render_column_compare(os.path.join(args.out, name), args.seed, args.steps_per_node, args.bias, 4, patch)
        print("wrote", name)
        return
    if args.compare_starts:
        os.makedirs(args.out, exist_ok=True)
        render_column_starts(args.out, f"compare_starts_column_bias{args.bias:g}{patch.suffix()}.png", args.seed, start_panels(args.bias), 5, 4, patch)
        render_column_starts(args.out, f"compare_starts_only_bias{args.bias:g}{patch.suffix()}.png", args.seed, start_only_panels(args.bias), 5, 4, patch)
        return
    suffix = bias_suffix(args.sampler, bias) + start_suffix(args.sampler, bias, args.steps_per_node) + pattern_suffix(bias) + patch.suffix()
    if args.column_band:
        os.makedirs(args.out, exist_ok=True)
        scene = column_scene(args.seed, args.sampler, args.steps_per_node, bias, patch)
        report_column("column tree", scene)
        name = column_band_name(args.sampler, bias, args.steps_per_node, patch)
        render_column_band(scene, os.path.join(args.out, name), 4)
        print("wrote", name)
        return
    os.makedirs(args.out, exist_ok=True)
    scene = Scene(args.seed, args.cx, args.cy, args.sampler, args.steps_per_node, bias, cz=args.cz, patch=patch)
    if args.chunk_paint:
        render_chunk_paint(args.out, scene, args.reach, args.scale, suffix)
        return
    print("shaft tree:", check_tree(scene))
    blocks = build_voxels(scene, args.reach)
    front = "shaft" if scene.shaft_sign == 1 else "column"
    back = "column" if front == "shaft" else "shaft"
    for flip, role in ((False, front), (True, back)):
        name = f"junction_{'back' if flip else 'front'}_{role}_patch{suffix}.png"
        render_iso(blocks, os.path.join(args.out, name), args.scale, flip)
        print("wrote", name)


if __name__ == "__main__":
    main()
