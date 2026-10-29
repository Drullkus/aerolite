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

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-28_22-52_72657c6a-f5b7-4adf-9364-cab19a14fc12.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md
"""

import argparse
import os
import random

from originshift import (
    BLOCKS_AROUND,
    NODES_PER_CHUNK,
    RING,
    Graph,
    build_layout,
    build_segment,
    is_corner,
    prune_corner_leaves,
    ring_offset,
    sample,
    seg_rng,
    segment_kind,
    write_png,
)

HALF = 7
PATCH = 7
CHUNK = 2 * NODES_PER_CHUNK


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


def patch_grid(g):
    """Add the 7x7 patch nodes and their grid edges to g. Return index map."""
    first = len(g.adj)
    for _ in range(PATCH * PATCH):
        g.add_node()
    index = {}
    for i in range(-3, 4):
        for j in range(-3, 4):
            index[(i, j)] = first + (i + 3) * PATCH + (j + 3)
    for (i, j), n in index.items():
        if i + 1 <= 3:
            g.add_edge(n, index[(i + 1, j)])
        if j + 1 <= 3:
            g.add_edge(n, index[(i, j + 1)])
    return index


def tree_edges(g, parent, names):
    """Tree edges as pairs of names. Nodes without a name are virtual."""
    edges = []
    for node in range(len(g.adj)):
        p = parent[node]
        if p < 0:
            continue
        edges.append((names.get(node, ("virtual", node)), names.get(p, ("virtual", p))))
    return edges


class Scene:
    """One junction: a junction segment on the shaft, pieces and a patch on the column."""

    def __init__(self, seed, cx, cy, sampler, steps_per_node, chunks_each_side=3, piece_rulings=12):
        self.seed, self.cx, self.cy, self.cz = seed, cx, cy, 0
        self.sampler, self.spn = sampler, steps_per_node
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
            self.segments[c] = build_segment(segment_kind("connector", c), NODES_PER_CHUNK, seg_rng(seed, "connector", c), sampler, steps_per_node)
        self.shaft_edges = self._plain_shaft_edges()
        self.shaft_edges += self._junction_segment(seg_rng(seed, "junction", self.k))
        self.below = build_segment("tree", piece_rulings, seg_rng(seed, "piece", 0), sampler, steps_per_node)
        self.above = build_segment("tree", piece_rulings, seg_rng(seed, "piece", 1), sampler, steps_per_node)
        self.piece_rulings = piece_rulings
        self.column_edges = self._column_patch(rng)

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
        for x in rulings:
            for v in range(RING):
                g.add_edge(index[(x, v)], index[(x, (v + 1) % RING)])
                if (x + 2, v) in index and not is_corner(v):
                    g.add_edge(index[(x, v)], index[(x + 2, v)])
        patch = patch_grid(g)
        for (i, j), n in patch.items():
            names[n] = ("patch", i, j)
        for j in range(-2, 3):
            v = face_node(self.shaft_sign, j)
            g.add_edge(patch[(-3, j)], index[(self.x_last, v)])
            g.add_edge(patch[(3, j)], index[(self.x_first, v)])
        x_start, x_end = rulings[0], rulings[-1]
        if self.k % 2 == 1:
            left = g.add_node()
            names[left] = (x_start - 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(left, index[(x_start, v)])
        if self.k_end % 2 == 1:
            right = g.add_node()
            names[right] = (x_end + 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(right, index[(x_end, v)])
        parent = sample(g, rng, self.sampler, self.spn)
        edges = []
        for a, b in tree_edges(g, parent, names):
            if a[1] is None:
                a = (a[0], b[1])
            if b[1] is None:
                b = (b[0], a[1])
            edges.append((a, b))
        return prune_corner_leaves(edges, lambda n: n[0] != "patch" and is_corner(n[1]))

    def _column_patch(self, rng):
        """Sample the column patch. Return its tree edges in world names."""
        g = Graph()
        patch = patch_grid(g)
        names = {n: ("patch", i, j) for (i, j), n in patch.items()}
        below = g.add_node()
        above = g.add_node()
        for i in range(-2, 3):
            g.add_edge(below, patch[(i, -3)])
            g.add_edge(above, patch[(i, 3)])
        parent = sample(g, rng, self.sampler, self.spn)
        edges = []
        for a, b in tree_edges(g, parent, names):
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
            for i in range(-3, 4):
                for j in range(-3, 4):
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
            out.append(patch_carve(scene, cx - HALF, y, sign))
        else:
            out += [((xx, y, cz + sign * 6), (xx, y, cz + sign * 5)) for xx in range(cx + HALF + 1, x)]
            out.append(patch_carve(scene, cx + HALF, y, sign))
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
        out.append(patch_carve(scene, x, cy - HALF, sign))
    else:
        out += [((x, yy, cz + sign * 6), (x, yy, cz + sign * 5)) for yy in range(cy + HALF + 1, y)]
        out.append(patch_carve(scene, x, cy + HALF, sign))
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

    def __init__(self, seed, cx, cy, sampler, steps_per_node, chunks_each_side=4):
        self.cx = cx
        rng = random.Random(f"{seed}:junction:{cx}:{cy}")
        self.shaft_sign = rng.choice((1, -1))
        self.c_lo = (cx - HALF) // CHUNK - chunks_each_side
        self.c_hi = (cx + HALF) // CHUNK + chunks_each_side
        segs = build_layout("connector", seed, self.c_hi - self.c_lo + 1, NODES_PER_CHUNK, sampler, steps_per_node, self.c_lo)
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
            touching = {key[face_node(self.shaft_sign, j)] for j in range(-2, 3)}
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


def cut_stats(seed, count, sampler, spn):
    print("junction  cx   cy  side   comps  rejoined  isolated  isolated_far  isolated_nodes  window3")
    totals = {"components": 0, "rejoined": 0, "isolated": 0, "isolated_far": 0, "isolated_nodes": 0, "window3": 0}
    rows = 0
    for k in range(count):
        rng = random.Random(f"{seed}:place:{k}")
        cx = rng.randrange(100, 400)
        cy = rng.randrange(40, 200)
        scene = CutScene(seed, cx, cy, sampler, spn)
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

    def __init__(self, seed, centers, sampler, steps_per_node, chunks_each_side=3):
        self.centers = sorted(centers)
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
            self.segments[c] = build_segment(segment_kind("connector", c), NODES_PER_CHUNK, seg_rng(seed, "connector", c), sampler, steps_per_node)
        self.shaft_edges = Scene._plain_shaft_edges(self)
        self.shaft_edges += self._junction_segment(seg_rng(seed, "junction", self.k), sampler, steps_per_node)

    def rulings(self):
        start = CHUNK * self.k + 1
        end = CHUNK * (self.k_end + 1)
        return [x for x in range(start, end, 2) if all(abs(x - c) > HALF for c in self.centers)]

    def _junction_segment(self, rng, sampler, steps_per_node):
        g = Graph()
        names = {}
        index = {}
        rulings = self.rulings()
        for x in rulings:
            for v in range(RING):
                n = g.add_node()
                index[(x, v)] = n
                names[n] = (x, v)
        for x in rulings:
            for v in range(RING):
                g.add_edge(index[(x, v)], index[(x, (v + 1) % RING)])
                if (x + 2, v) in index and not is_corner(v):
                    g.add_edge(index[(x, v)], index[(x + 2, v)])
        for b, (c, sign) in enumerate(zip(self.centers, self.signs)):
            patch = patch_grid(g)
            for (i, j), n in patch.items():
                names[n] = ("patch", b, i, j)
            x_last, x_first = odd_below(c - HALF - 1), odd_above(c + HALF + 1)
            for j in range(-2, 3):
                v = face_node(sign, j)
                g.add_edge(patch[(-3, j)], index[(x_last, v)])
                g.add_edge(patch[(3, j)], index[(x_first, v)])
        x_start, x_end = rulings[0], rulings[-1]
        if self.k % 2 == 1:
            left = g.add_node()
            names[left] = (x_start - 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(left, index[(x_start, v)])
        if self.k_end % 2 == 1:
            right = g.add_node()
            names[right] = (x_end + 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(right, index[(x_end, v)])
        parent = sample(g, rng, sampler, steps_per_node)
        edges = []
        for a, b in tree_edges(g, parent, names):
            if a[1] is None:
                a = (a[0], b[1])
            if b[1] is None:
                b = (b[0], a[1])
            edges.append((a, b))
        return prune_corner_leaves(edges, lambda n: n[0] != "patch" and is_corner(n[1]))


def cluster_stats(seed, count, sampler, spn):
    """Place two boxes at a random distance of 31 to 60 blocks and check that the shaft stays one tree."""
    merged = 0
    trees = 0
    for k in range(count):
        rng = random.Random(f"{seed}:cluster-place:{k}")
        c1 = rng.randrange(100, 400)
        c2 = c1 + rng.randrange(31, 61)
        if junction_chunks(c1)[1] >= junction_chunks(c2)[0]:
            merged += 1
            scene = ClusterScene(seed, [c1, c2], sampler, spn)
        else:
            scene = ClusterScene(seed, [c1], sampler, spn)
        result = check_tree(scene)
        if result.endswith(" 1 component(s)"):
            trees += 1
        else:
            print(f"{k:>4} {c1:>4} {c2:>4}  {result}")
    print(f"merged clusters: {merged} of {count}")
    print(f"scenes that are one tree: {trees} of {count}")


# --- segmented column -----------------------------------------------------------


class ColumnScene:
    """One full-height column cut into 16-block sections, like a horizontal shaft.

    Tree sections and connector sections alternate. A virtual node only faces a
    section inside the build height. The column junction segment holds the column
    patch, with crossings from the patch border onto the nearest rulings.
    """

    def __init__(self, seed, centers, signs, y_min, y_max, sampler, steps_per_node):
        self.centers, self.signs = centers, signs
        self.y_min, self.y_max = y_min, y_max
        self.s_lo, self.s_hi = y_min // CHUNK, (y_max - 1) // CHUNK
        self.sampler, self.spn = sampler, steps_per_node
        ranges = sorted(junction_chunks(c) for c in centers)
        clusters = []
        for lo, hi in ranges:
            if clusters and lo <= clusters[-1][1]:
                clusters[-1][1] = max(clusters[-1][1], hi)
            else:
                clusters.append([lo, hi])
        self.edges = []
        self.rulings = []
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
        for y in rulings:
            for v in range(RING):
                g.add_edge(index[(y, v)], index[(y, (v + 1) % RING)])
                if (y + 2, v) in index and not is_corner(v):
                    g.add_edge(index[(y, v)], index[(y + 2, v)])
        for c, sign in boxes:
            patch = patch_grid(g)
            for (i, j), n in patch.items():
                names[n] = ("patch", c, i, j)
            for i in range(-2, 3):
                v = face_node(sign, i)
                if (odd_below(c - HALF - 1), v) in index:
                    g.add_edge(patch[(i, -3)], index[(odd_below(c - HALF - 1), v)])
                if (odd_above(c + HALF + 1), v) in index:
                    g.add_edge(patch[(i, 3)], index[(odd_above(c + HALF + 1), v)])
        if lo % 2 == 1 and lo - 1 >= self.s_lo:
            left = g.add_node()
            names[left] = (rulings[0] - 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(left, index[(rulings[0], v)])
        if hi % 2 == 1 and hi + 1 <= self.s_hi:
            right = g.add_node()
            names[right] = (rulings[-1] + 2, None)
            for v in range(RING):
                if not is_corner(v):
                    g.add_edge(right, index[(rulings[-1], v)])
        parent = sample(g, rng, self.sampler, self.spn)
        edges = []
        for a, b in tree_edges(g, parent, names):
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


def render_column_band(scene, path, scale):
    """The unwrapped column surface, bottom row at the bottom. Junction boxes have a blue tint."""
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
    write_png(path, BLOCKS_AROUND * scale, len(rows), rows)


def column_stats(seed, count, sampler, spn):
    """Place two junctions on a full-height column and check that the column stays one tree."""
    trees = 0
    for k in range(count):
        rng = random.Random(f"{seed}:column-place:{k}")
        low = rng.randrange(-64 + 24, 300 - 40)
        high = low + rng.randrange(17, 120)
        high = min(high, 320 - 24)
        scene = ColumnScene(seed + k, [low, high], [rng.choice((1, -1)), rng.choice((1, -1))], -64, 320, sampler, spn)
        result = check_tree(scene)
        if result.endswith(" 1 component(s)"):
            trees += 1
        else:
            print(f"{k:>4} {low:>4} {high:>4}  {result}")
    print(f"columns that are one tree: {trees} of {count}")


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


AIR, STONE, BRICK, COBBLE, BOX = 0, 1, 2, 3, 4
COLORS = {STONE: (128, 128, 128), BRICK: (160, 152, 142), COBBLE: (92, 96, 102), BOX: (120, 126, 134)}


def build_voxels(scene, reach):
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
    for surface, beneath in scene.carves(reach):
        blocks[surface] = AIR
        blocks[beneath] = COBBLE
    return blocks


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
    ap.add_argument("--sampler", choices=("originshift", "wilson"), default="originshift")
    ap.add_argument("--steps-per-node", type=int, default=16)
    ap.add_argument("--reach", type=int, default=22, help="blocks of shaft and column on each side of the junction")
    ap.add_argument("--scale", type=int, default=9, help="pixels per block edge")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "images"))
    ap.add_argument("--cut-stats", action="store_true", help="measure the stranded components of the cut model and exit")
    ap.add_argument("--cluster-stats", action="store_true", help="check that two close boxes in one junction segment keep the shaft one tree and exit")
    ap.add_argument("--column-stats", action="store_true", help="check that a segmented column with two junctions is one tree and exit")
    ap.add_argument("--column-band", action="store_true", help="render the unwrapped surface of one segmented column and exit")
    ap.add_argument("--count", type=int, default=40)
    args = ap.parse_args()
    if args.cut_stats:
        cut_stats(args.seed, args.count, args.sampler, args.steps_per_node)
        return
    if args.cluster_stats:
        cluster_stats(args.seed, args.count, args.sampler, args.steps_per_node)
        return
    if args.column_stats:
        column_stats(args.seed, args.count, args.sampler, args.steps_per_node)
        return
    if args.column_band:
        os.makedirs(args.out, exist_ok=True)
        scene = ColumnScene(args.seed, [-56, 56], [1, 1], -64, 320, args.sampler, args.steps_per_node)
        print("column tree:", check_tree(scene))
        render_column_band(scene, os.path.join(args.out, "column_band.png"), 4)
        print("wrote column_band.png")
        return
    os.makedirs(args.out, exist_ok=True)
    scene = Scene(args.seed, args.cx, args.cy, args.sampler, args.steps_per_node)
    print("shaft tree:", check_tree(scene))
    blocks = build_voxels(scene, args.reach)
    front = "shaft" if scene.shaft_sign == 1 else "column"
    back = "column" if front == "shaft" else "shaft"
    for flip, role in ((False, front), (True, back)):
        name = f"junction_{'back' if flip else 'front'}_{role}_patch.png"
        render_iso(blocks, os.path.join(args.out, name), args.scale, flip)
        print("wrote", name)


if __name__ == "__main__":
    main()
