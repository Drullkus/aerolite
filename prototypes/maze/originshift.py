#!/usr/bin/env python3
"""Origin Shift maze prototype for the Aerolite shaft surface.

The shaft surface is a cylinder of 48 blocks around and infinite length.
A ruling is one ring of nodes at an odd block along the axis. A chunk holds
8 rulings. Around the ring, nodes sit on even blocks, so the four corners
are nodes. Each face has 7 nodes from corner to corner. A corner node has
no edge along the axis. A corner node carves only when it passes a path
from one face to the other. A corner leaf and its edge stay wall, so a
two-block margin runs along every corner. Every other block is a maze wall.

The program cuts the cylinder into segments and samples one spanning
tree per segment. Three layouts join the segments:

- oneroot: each segment is a tree with one crossing to the next segment.
- forest: each segment is a forest. Each component has one forward crossing.
- connector: tree segments and connector segments alternate. A connector
  touches both neighbors through two virtual nodes.

The bias P sets the Origin Shift step. The walker picks the axis class with
chance P and the ring class with chance 1 - P. Then it picks a uniform
neighbor in that class. An empty class falls back to the other class.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-28_22-52_44feb929-a98e-4826-8aa2-5d56973d077a.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-28_22-52_72657c6a-f5b7-4adf-9364-cab19a14fc12.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md
"""

import argparse
import os
import random
import struct
import zlib

RING = 24
BLOCKS_AROUND = 2 * RING
FACE_NODES = 6
NODES_PER_CHUNK = 8
DEFAULT_BIAS = 0.5
CLOSEUP_CHUNKS = 6
CLOSEUP_SCALE = 10

# An axis edge joins two rulings. An edge to a virtual node is an axis edge.
AXIS = "axis"
# A ring edge joins two neighbor nodes of one ruling.
RING_EDGE = "ring"


def is_corner(v):
    return v % FACE_NODES == 0


def ring_offset(block):
    """Map a ring block index to a (first, second) offset on the 13x13 ring."""
    block %= BLOCKS_AROUND
    if block < 12:
        return -6 + block, 6
    if block < 24:
        return 6, 6 - (block - 12)
    if block < 36:
        return 6 - (block - 24), -6
    return -6, -6 + (block - 36)


class Graph:
    """Undirected graph with adjacency lists that keep insertion sequence.

    adj holds every neighbor. axis and ring hold the neighbors of one edge class.
    """

    def __init__(self):
        self.adj = []
        self.axis = []
        self.ring = []

    def add_node(self):
        self.adj.append([])
        self.axis.append([])
        self.ring.append([])
        return len(self.adj) - 1

    def add_edge(self, a, b, kind):
        self.adj[a].append(b)
        self.adj[b].append(a)
        side = self.axis if kind == AXIS else self.ring
        side[a].append(b)
        side[b].append(a)


def segment_graph(length, with_left, with_right):
    """Build the node grid of one segment with optional virtual end nodes."""
    g = Graph()
    for _ in range(length * RING):
        g.add_node()
    for u in range(length):
        for v in range(RING):
            node = u * RING + v
            g.add_edge(node, u * RING + (v + 1) % RING, RING_EDGE)
            if u + 1 < length and not is_corner(v):
                g.add_edge(node, (u + 1) * RING + v, AXIS)
    left = right = -1
    if with_left:
        left = g.add_node()
        for v in range(RING):
            if not is_corner(v):
                g.add_edge(left, v, AXIS)
    if with_right:
        right = g.add_node()
        for v in range(RING):
            if not is_corner(v):
                g.add_edge(right, (length - 1) * RING + v, AXIS)
    return g, left, right


def bfs_tree(g, root):
    """Deterministic start tree for Origin Shift."""
    parent = [-2] * len(g.adj)
    parent[root] = -1
    queue = [root]
    for node in queue:
        for n in g.adj[node]:
            if parent[n] == -2:
                parent[n] = node
                queue.append(n)
    return parent


def biased_step(g, rng, node, bias):
    """Pick the axis class with chance bias, else the ring class, then a uniform neighbor."""
    first, other = (g.axis, g.ring) if rng.random() < bias else (g.ring, g.axis)
    return rng.choice(first[node] or other[node])


def origin_shift(g, rng, steps, bias):
    root = rng.randrange(len(g.adj))
    parent = bfs_tree(g, root)
    for _ in range(steps):
        nxt = biased_step(g, rng, root, bias)
        parent[root] = nxt
        root = nxt
        parent[root] = -1
    return parent


def wilson(g, rng):
    n = len(g.adj)
    parent = [-1] * n
    in_tree = [False] * n
    in_tree[rng.randrange(n)] = True
    for start in range(n):
        node = start
        while not in_tree[node]:
            parent[node] = rng.choice(g.adj[node])
            node = parent[node]
        # Overwriting parents erases the loops of the walk.
        node = start
        while not in_tree[node]:
            in_tree[node] = True
            node = parent[node]
    return parent


def prune_corner_leaves(edges, is_corner_node):
    """Drop every corner node of degree one together with its edge."""
    degree = {}
    for a, b in edges:
        degree[a] = degree.get(a, 0) + 1
        degree[b] = degree.get(b, 0) + 1
    return [(a, b) for a, b in edges
            if not (is_corner_node(a) and degree[a] == 1) and not (is_corner_node(b) and degree[b] == 1)]


def sample(g, rng, sampler, steps_per_node, bias):
    """Wilson's algorithm ignores the bias. It stays the unbiased reference."""
    if sampler == "wilson":
        return wilson(g, rng)
    return origin_shift(g, rng, steps_per_node * len(g.adj), bias)


def bias_value(text):
    """Parse a bias from 0 to 1 on the command line."""
    p = float(text)
    if not 0.0 <= p <= 1.0:
        raise argparse.ArgumentTypeError("the bias must be from 0 to 1")
    return p


def bias_suffix(sampler, bias):
    """File name suffix. The default bias and Wilson's algorithm get none."""
    if sampler == "wilson" or bias == DEFAULT_BIAS:
        return ""
    return f"_bias{bias:g}"


class Segment:
    """Tree edges of one segment in local node coordinates."""

    def __init__(self, length):
        self.length = length
        self.edges = []
        self.left_cross = []
        self.right_cross = []


def build_segment(kind, length, rng, sampler, steps_per_node, bias):
    with_left = kind == "connector"
    with_right = kind in ("connector", "forest")
    g, left, right = segment_graph(length, with_left, with_right)
    parent = sample(g, rng, sampler, steps_per_node, bias)
    seg = Segment(length)
    real = length * RING
    for node in range(len(g.adj)):
        p = parent[node]
        if p < 0:
            continue
        a, b = (node, p) if node < p else (p, node)
        if b < real:
            seg.edges.append((divmod(a, RING), divmod(b, RING)))
        elif b == left:
            seg.left_cross.append(a % RING)
        elif b == right:
            seg.right_cross.append(a % RING)
    if kind == "oneroot":
        seg.right_cross.append(rng.choice([v for v in range(RING) if not is_corner(v)]))
    seg.edges = prune_corner_leaves(seg.edges, lambda n: is_corner(n[1]))
    return seg


def seg_rng(seed, layout, index):
    return random.Random(f"{seed}:{layout}:{index}")


def segment_kind(layout, index):
    if layout == "connector":
        return "tree" if index % 2 == 0 else "connector"
    return layout


def build_layout(layout, seed, segments, length, sampler, steps_per_node, bias, first_index=0):
    out = []
    for k in range(first_index, first_index + segments):
        rng = seg_rng(seed, layout, k)
        out.append(build_segment(segment_kind(layout, k), length, rng, sampler, steps_per_node, bias))
    return out


def paint_band(segments):
    """Return a block grid, True for path, indexed [ring_block][along_block]."""
    total = sum(s.length for s in segments)
    width = 2 * total
    grid = [[False] * width for _ in range(BLOCKS_AROUND)]
    offset = 0
    for seg in segments:
        present = {n for e in seg.edges for n in e}
        for u in range(seg.length):
            for v in range(RING):
                if not is_corner(v) or (u, v) in present:
                    grid[2 * v][2 * (offset + u) + 1] = True
        for (u1, v1), (u2, v2) in seg.edges:
            if u1 == u2:
                lo = min(v1, v2)
                y = 2 * lo + 1 if abs(v1 - v2) == 1 else BLOCKS_AROUND - 1
                grid[y][2 * (offset + u1) + 1] = True
            else:
                grid[2 * v1][2 * (offset + min(u1, u2)) + 2] = True
        for v in seg.left_cross:
            grid[2 * v][2 * offset] = True
        for v in seg.right_cross:
            x = 2 * (offset + seg.length)
            if x < width:
                grid[2 * v][x] = True
        offset += seg.length
    return grid


WALL = (150, 150, 150)
PATH = (58, 58, 66)
WALL_CORNER = (176, 176, 176)
PATH_CORNER = (84, 84, 94)
MARK_A = (210, 120, 40)
MARK_B = (40, 120, 210)


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", header))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def render_band(grid, path, scale):
    width = len(grid[0])
    mark_h = 2
    rows = []
    mark = []
    for x in range(width):
        c = MARK_A if (x // (2 * NODES_PER_CHUNK)) % 2 == 0 else MARK_B
        mark.extend(c * scale)
    for _ in range(mark_h):
        rows.append(mark)
    for y in range(BLOCKS_AROUND):
        corner = y % (BLOCKS_AROUND // 4) == 0
        row = []
        for x in range(width):
            if grid[y][x]:
                c = PATH_CORNER if corner else PATH
            else:
                c = WALL_CORNER if corner else WALL
            row.extend(c * scale)
        for _ in range(scale):
            rows.append(row)
    write_png(path, width * scale, len(rows), rows)


def dead_end_fraction(seg):
    degree = {}
    for a, b in seg.edges:
        degree[a] = degree.get(a, 0) + 1
        degree[b] = degree.get(b, 0) + 1
    for v in seg.left_cross:
        degree[(0, v)] = degree.get((0, v), 0) + 1
    for v in seg.right_cross:
        degree[(seg.length - 1, v)] = degree.get((seg.length - 1, v), 0) + 1
    ends = sum(1 for d in degree.values() if d == 1)
    return ends / len(degree)


def corner_pass_fraction(seg):
    """Share of corner nodes that carry a path across the corner."""
    present = {n for e in seg.edges for n in e if is_corner(n[1])}
    return len(present) / (seg.length * RING // FACE_NODES)


def stats(seed, length, count, bias):
    print(f"bias {bias:g}. Wilson's algorithm ignores the bias.")
    print("layout      sampler      steps/node  dead ends  corner pass  left cross  right cross")
    runs = [("wilson", 0), ("originshift", 1), ("originshift", 4), ("originshift", 16), ("originshift", 64)]
    for kind in ("tree", "connector", "forest"):
        for sampler, spn in runs:
            ends = []
            passes = []
            lefts = []
            rights = []
            for k in range(count):
                rng = seg_rng(seed, f"stats-{kind}-{sampler}-{spn}", k)
                seg = build_segment(kind, length, rng, sampler, spn, bias)
                ends.append(dead_end_fraction(seg))
                passes.append(corner_pass_fraction(seg))
                lefts.append(len(seg.left_cross))
                rights.append(len(seg.right_cross))
            print(f"{kind:<11} {sampler:<12} {spn:>10}  {sum(ends)/count:>9.3f}  {sum(passes)/count:>11.3f}  {sum(lefts)/count:>10.2f}  {sum(rights)/count:>11.2f}")


def svg_graph_view(path, seed, length, sampler, steps_per_node, bias):
    """Draw three segments of the oneroot and connector layouts as node graphs."""
    cell = 13
    pad = 30
    seg_w = length * cell
    panel_w = 3 * seg_w + 2 * pad
    panel_h = RING * cell + 2 * pad
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{panel_w + 160}" height="{3 * panel_h}" font-family="sans-serif" font-size="12">')
    out.append('<rect width="100%" height="100%" fill="white"/>')

    def node_xy(seg_index, u, v, oy):
        return pad + seg_index * seg_w + u * cell + cell / 2, oy + pad + v * cell + cell / 2

    def draw_panel(title, segments, oy, contracted):
        out.append(f'<text x="{pad}" y="{oy + 18}" font-weight="bold">{title}</text>')
        for k in range(1, 3):
            x = pad + k * seg_w
            out.append(f'<line x1="{x}" y1="{oy + pad}" x2="{x}" y2="{oy + pad + RING * cell}" stroke="#999" stroke-dasharray="4 3"/>')
        for k, seg in enumerate(segments):
            if contracted and k != 1:
                continue
            for (u1, v1), (u2, v2) in seg.edges:
                if abs(v1 - v2) > 1:
                    continue
                x1, y1 = node_xy(k, u1, v1, oy)
                x2, y2 = node_xy(k, u2, v2, oy)
                out.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#333" stroke-width="2"/>')
            for v in seg.left_cross:
                x2, y2 = node_xy(k, 0, v, oy)
                x1 = x2 - cell if not contracted else pad + seg_w - 2 * cell
                y1 = y2 if not contracted else oy + pad + RING * cell / 2
                out.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#d2782a" stroke-width="2"/>')
            for v in seg.right_cross:
                x1, y1 = node_xy(k, seg.length - 1, v, oy)
                x2 = x1 + cell if not contracted else pad + 2 * seg_w + 2 * cell
                y2 = y1 if not contracted else oy + pad + RING * cell / 2
                if k == 2 and not contracted:
                    continue
                out.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#d2782a" stroke-width="2"/>')
            for u in range(seg.length):
                for v in range(RING):
                    x, y = node_xy(k, u, v, oy)
                    r = 4 if is_corner(v) else 2.5
                    out.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#333"/>')
        if contracted:
            cy = oy + pad + RING * cell / 2
            for cx, label in ((pad + seg_w - 2 * cell, "E(k)"), (pad + 2 * seg_w + 2 * cell, "E(k+1)")):
                out.append(f'<circle cx="{cx}" cy="{cy}" r="16" fill="#eee" stroke="#333" stroke-width="2"/>')
                out.append(f'<text x="{cx}" y="{cy + 4}" text-anchor="middle">{label}</text>')
            out.append(f'<text x="{pad + seg_w + seg_w / 2}" y="{oy + pad + RING * cell + 20}" text-anchor="middle">connector O(k)</text>')

    oneroot = build_layout("oneroot", seed, 3, length, sampler, steps_per_node, bias)
    connector = build_layout("connector", seed, 3, length, sampler, steps_per_node, bias)
    draw_panel("oneroot layout: tree, tree, tree. One crossing per boundary. Big dots are corner nodes.", oneroot, 0, False)
    draw_panel("connector layout: tree E(0), connector O(1), tree E(2). Many crossings per boundary.", connector, panel_h, False)
    draw_panel("contracted view of O(1): each tree neighbor is one virtual node.", connector, 2 * panel_h, True)
    lx = panel_w + 10
    out.append(f'<line x1="{lx}" y1="{pad}" x2="{lx + 30}" y2="{pad}" stroke="#333" stroke-width="2"/><text x="{lx + 36}" y="{pad + 4}">tree edge</text>')
    out.append(f'<line x1="{lx}" y1="{pad + 20}" x2="{lx + 30}" y2="{pad + 20}" stroke="#d2782a" stroke-width="2"/><text x="{lx + 36}" y="{pad + 24}">crossing</text>')
    out.append(f'<line x1="{lx}" y1="{pad + 40}" x2="{lx + 30}" y2="{pad + 40}" stroke="#999" stroke-dasharray="4 3"/><text x="{lx + 36}" y="{pad + 44}">segment edge</text>')
    out.append("</svg>")
    with open(path, "w") as f:
        f.write("\n".join(out))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--chunks", type=int, default=24, help="segments per band")
    ap.add_argument("--length", type=int, default=NODES_PER_CHUNK, help="rulings per segment")
    ap.add_argument("--sampler", choices=("originshift", "wilson"), default="originshift")
    ap.add_argument("--steps-per-node", type=int, default=16)
    ap.add_argument("--bias", type=bias_value, default=DEFAULT_BIAS, help="chance of the axis class at each Origin Shift step")
    ap.add_argument("--scale", type=int, default=4, help="pixels per block")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "images"))
    ap.add_argument("--stats", action="store_true", help="print sampler statistics and exit")
    args = ap.parse_args()
    if args.stats:
        stats(args.seed, args.length, 200, args.bias)
        return
    suffix = bias_suffix(args.sampler, args.bias)
    closeup = os.path.join(args.out, "closeup")
    os.makedirs(closeup, exist_ok=True)
    for layout in ("oneroot", "forest", "connector"):
        segs = build_layout(layout, args.seed, args.chunks, args.length, args.sampler, args.steps_per_node, args.bias)
        name = f"band_{layout}_{args.sampler}{suffix}.png"
        render_band(paint_band(segs), os.path.join(args.out, name), args.scale)
        print("wrote", name)
        render_band(paint_band(segs[:CLOSEUP_CHUNKS]), os.path.join(closeup, name), CLOSEUP_SCALE)
        print("wrote", os.path.join("closeup", name))
    name = f"graph_view{suffix}.svg"
    svg_graph_view(os.path.join(args.out, name), args.seed, args.length, args.sampler, args.steps_per_node, args.bias)
    print("wrote", name)


if __name__ == "__main__":
    main()
