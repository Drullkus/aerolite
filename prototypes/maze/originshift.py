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

The bias P and the rule set the Origin Shift step. Two rules exist:

- class: the walker picks the axis class with chance P and the ring class
  with chance 1 - P. Then it picks a uniform neighbor in that class. An
  empty class falls back to the other class.
- degree: every axis edge has weight P and every ring edge has weight
  1 - P. The walker picks one edge in proportion to its weight. When every
  edge at the node has weight 0, the pick is uniform.

Wilson's algorithm takes the weighted steps of the degree rule. It ignores
the bias of the class rule.

Origin Shift starts from one of four start trees:

- bfs: the breadth-first tree of the root.
- uniform: a uniform spanning tree from Wilson's algorithm with uniform steps.
- serpentine: one path that snakes along the axis, one ring position per run.
- hilbert: a generalized Hilbert curve over the ring-by-ruling rectangle.

A curve start keeps the curve edges that the graph has. A union-find pass
over the graph edges joins the pieces, then adds the virtual and patch nodes.
The scaled steps divide the steps per node by min(P, 1 - P).

Wilson's algorithm takes a pattern type and a pattern strength s. Each type
defines a set of pattern edges on the segment graph. The weight of an edge is
its pattern factor times its class factor. The pattern factor is 1 for a
pattern edge and 1 - s for any other edge. At s = 1 the sampler does not run,
and the pattern edges are the segment edges. The pattern types are uniform,
serpentine, ribs, lines and gilbert. Lines takes no strength and no bias. It
always paints its grooves, on every non-corner ring position or on every
second one. A serpentine connector turns into each neighbor at the gap of the
facing neighbor ruling. Gilbert at s = 1 joins its curve pieces into one tree.

A node carves only when it carries an edge.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-28_22-52_44feb929-a98e-4826-8aa2-5d56973d077a.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-28_22-52_72657c6a-f5b7-4adf-9364-cab19a14fc12.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md
Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md
"""

import argparse
import os
import random
import struct
import zlib
from collections import namedtuple

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

CLASS_RULE = "class"
DEGREE_RULE = "degree"
DEFAULT_RULE = CLASS_RULE

BFS_START = "bfs"
UNIFORM_START = "uniform"
SERPENTINE_START = "serpentine"
HILBERT_START = "hilbert"
STARTS = (BFS_START, UNIFORM_START, SERPENTINE_START, HILBERT_START)
DEFAULT_STEPS = 16

UNIFORM_PATTERN = "uniform"
SERPENTINE_PATTERN = "serpentine"
RIBS_PATTERN = "ribs"
LINES_PATTERN = "lines"
GILBERT_PATTERN = "gilbert"
PATTERNS = (UNIFORM_PATTERN, SERPENTINE_PATTERN, RIBS_PATTERN, LINES_PATTERN, GILBERT_PATTERN)

RANDOM_SEAM = "random"
SEEDED_SEAM = "seeded"
PILLAR_SEAM = "pillar"
SEAMS = (RANDOM_SEAM, SEEDED_SEAM, PILLAR_SEAM)

LINE_SPACINGS = (1, 2)
STRENGTH_STEPS = 20
COMPARE_STRENGTHS = (0.0, 0.5, 0.9, 1.0)

# p is the axis chance of the class rule or the axis edge weight of the degree rule.
# start and scaled apply to Origin Shift only. pattern, strength and seam apply to Wilson's algorithm only.
# pillar is the ring position of the pillar seam, or None. spacing is the line spacing of lines.
Bias = namedtuple(
    "Bias",
    ("p", "rule", "start", "scaled", "pattern", "strength", "seam", "pillar", "spacing"),
    defaults=(BFS_START, False, UNIFORM_PATTERN, 0.0, RANDOM_SEAM, None, 1),
)


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
    rulings holds the RING node ids of each ruling, in axis sequence.
    left and right are the virtual nodes before the first ruling and after the last ruling, or -1.
    patches holds (index, axis_on_i, m) of each patch grid.
    groups holds the crossing edges of each patch side as (patch node, other node) pairs.
    """

    def __init__(self):
        self.adj = []
        self.axis = []
        self.ring = []
        self.rulings = []
        self.left = -1
        self.right = -1
        self.patches = []
        self.groups = []

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
    g.rulings = [[u * RING + v for v in range(RING)] for u in range(length)]
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
    g.left, g.right = left, right
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


def class_step(g, rng, node, p):
    """Pick the axis class with chance p, else the ring class, then a uniform neighbor."""
    first, other = (g.axis, g.ring) if rng.random() < p else (g.ring, g.axis)
    return rng.choice(first[node] or other[node])


def degree_step(g, rng, node, p):
    """Pick one edge in proportion to its weight: p per axis edge, 1 - p per ring edge."""
    axis_weight = len(g.axis[node]) * p
    total = axis_weight + len(g.ring[node]) * (1.0 - p)
    if total <= 0.0:
        return rng.choice(g.adj[node])
    if rng.random() * total < axis_weight:
        return rng.choice(g.axis[node])
    return rng.choice(g.ring[node])


def biased_step(g, rng, node, bias):
    if bias.rule == DEGREE_RULE:
        return degree_step(g, rng, node, bias.p)
    return class_step(g, rng, node, bias.p)


def tree_edges_of(parent):
    """Undirected edge set of a parent array."""
    return {(min(n, p), max(n, p)) for n, p in enumerate(parent) if p >= 0}


def orient(n, edges, root):
    """Parent array of a spanning tree, pointing toward root."""
    adj = [[] for _ in range(n)]
    for a, b in edges:
        adj[a].append(b)
        adj[b].append(a)
    parent = [-2] * n
    parent[root] = -1
    queue = [root]
    for node in queue:
        for m in adj[node]:
            if parent[m] == -2:
                parent[m] = node
                queue.append(m)
    return parent


def ruling_runs(g):
    """Split the rulings of g into runs that axis edges join. Ring position 1 is never a corner."""
    runs = []
    for r, ruling in enumerate(g.rulings):
        if runs and ruling[1] in g.axis[g.rulings[r - 1][1]]:
            runs[-1].append(ruling)
        else:
            runs.append([ruling])
    return runs


def serpentine_cells(n):
    """Snake over RING x n cells (v, r). A corner column takes one cell on the turn ruling."""
    cells = []
    up = True
    r = 0
    for v in range(1, RING):
        if is_corner(v):
            cells.append((v, r))
            continue
        span = range(n) if up else range(n - 1, -1, -1)
        cells.extend((v, s) for s in span)
        r = span[-1]
        up = not up
    return cells


def _sgn(a):
    return (a > 0) - (a < 0)


def _half(a):
    """Half of a, rounded toward zero."""
    return a // 2 if a >= 0 else -(-a // 2)


def _gilbert(out, x, y, ax, ay, bx, by):
    """Gilbert recursion of Jakub Cerveny. (ax, ay) is the major axis, (bx, by) the minor axis."""
    w = abs(ax + ay)
    h = abs(bx + by)
    dax, day = _sgn(ax), _sgn(ay)
    dbx, dby = _sgn(bx), _sgn(by)
    if h == 1:
        for _ in range(w):
            out.append((x, y))
            x, y = x + dax, y + day
        return
    if w == 1:
        for _ in range(h):
            out.append((x, y))
            x, y = x + dbx, y + dby
        return
    ax2, ay2 = _half(ax), _half(ay)
    bx2, by2 = _half(bx), _half(by)
    w2 = abs(ax2 + ay2)
    h2 = abs(bx2 + by2)
    if 2 * w > 3 * h:
        if w2 % 2 and w > 2:
            ax2, ay2 = ax2 + dax, ay2 + day
        _gilbert(out, x, y, ax2, ay2, bx, by)
        _gilbert(out, x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by)
    else:
        if h2 % 2 and h > 2:
            bx2, by2 = bx2 + dbx, by2 + dby
        _gilbert(out, x, y, bx2, by2, ax2, ay2)
        _gilbert(out, x + bx2, y + by2, ax, ay, bx - bx2, by - by2)
        _gilbert(out, x + (ax - dax) + (bx2 - dbx), y + (ay - day) + (by2 - dby),
                 -bx2, -by2, -(ax - ax2), -(ay - ay2))


def gilbert_cells(n):
    """Generalized Hilbert curve over RING x n cells (v, r). The ring seam is at the corner column 0."""
    out = []
    if RING >= n:
        _gilbert(out, 0, 0, RING, 0, 0, n)
    else:
        _gilbert(out, 0, 0, 0, n, RING, 0)
    return out


def curve_start(g, root, curve, trace):
    """Keep the curve edges that g has, then join the rest by a union-find pass over the graph edges."""
    kept = []
    total = 0
    for run in ruling_runs(g):
        cells = curve(len(run))
        for (v1, r1), (v2, r2) in zip(cells, cells[1:]):
            a, b = run[r1][v1], run[r2][v2]
            total += 1
            if b in g.adj[a]:
                kept.append((a, b))
    if trace is not None:
        trace["curve"] = (len(kept), total)
    n = len(g.adj)
    on_grid = [False] * n
    for ruling in g.rulings:
        for node in ruling:
            on_grid[node] = True
    graph_edges = [(a, b) for a in range(n) for b in g.adj[a] if a < b]
    grid_edges = [e for e in graph_edges if on_grid[e[0]] and on_grid[e[1]]]
    other_edges = [e for e in graph_edges if not (on_grid[e[0]] and on_grid[e[1]])]
    uf = list(range(n))

    def find(a):
        while uf[a] != a:
            uf[a] = uf[uf[a]]
            a = uf[a]
        return a

    edges = []
    for a, b in kept + grid_edges + other_edges:
        ra, rb = find(a), find(b)
        if ra != rb:
            uf[ra] = rb
            edges.append((a, b))
    return orient(n, edges, root)


def start_tree(g, rng, root, start, trace=None):
    """Origin Shift start tree, rooted at root."""
    if start == UNIFORM_START:
        uniform = wilson(g, rng, Bias(DEFAULT_BIAS, CLASS_RULE))
        return orient(len(g.adj), tree_edges_of(uniform), root)
    if start == SERPENTINE_START:
        return curve_start(g, root, serpentine_cells, trace)
    if start == HILBERT_START:
        return curve_start(g, root, gilbert_cells, trace)
    return bfs_tree(g, root)


def origin_shift(g, rng, steps, bias, trace=None):
    root = rng.randrange(len(g.adj))
    parent = start_tree(g, rng, root, bias.start, trace)
    if trace is not None:
        trace["start"] = list(parent)
    for _ in range(steps):
        nxt = biased_step(g, rng, root, bias)
        parent[root] = nxt
        root = nxt
        parent[root] = -1
    return parent


def wilson(g, rng, bias):
    """The degree rule gives weighted steps. The class rule gives uniform steps."""
    n = len(g.adj)
    parent = [-1] * n
    in_tree = [False] * n
    in_tree[rng.randrange(n)] = True
    weighted = bias.rule == DEGREE_RULE
    for start in range(n):
        node = start
        while not in_tree[node]:
            if weighted:
                parent[node] = degree_step(g, rng, node, bias.p)
            else:
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


def scaled_steps(steps_per_node, p):
    """Steps per node divided by the weight of the disfavored edge class."""
    return round(steps_per_node / min(p, 1.0 - p))


def effective_steps(steps_per_node, bias):
    return scaled_steps(steps_per_node, bias.p) if bias.scaled else steps_per_node


def sample(g, rng, sampler, steps_per_node, bias, trace=None):
    """Wilson's algorithm ignores the bias of the class rule. The class rule has no edge weights.

    trace, when given, receives the start tree of Origin Shift.
    """
    if sampler == "wilson":
        return wilson(g, rng, bias)
    return origin_shift(g, rng, effective_steps(steps_per_node, bias) * len(g.adj), bias, trace)


def parent_edges(parent):
    """(node, parent) pairs of a parent array, in node sequence."""
    return [(n, p) for n, p in enumerate(parent) if p >= 0]


def edge_key(a, b):
    return (a, b) if a < b else (b, a)


def pattern_strength(bias):
    """Uniform has no pattern edge, so it acts at 0. Lines always paints its grooves, so it acts at 1."""
    if bias.pattern == UNIFORM_PATTERN:
        return 0.0
    if bias.pattern == LINES_PATTERN:
        return 1.0
    return bias.strength


def pattern_random(rng):
    """A random seeded from the state of the segment random. It takes no value from the segment random."""
    return random.Random(repr(rng.getstate()))


def with_pillar(bias, key):
    """Set the ring position of the pillar seam from the pillar random of key."""
    if bias.seam != PILLAR_SEAM or bias.pillar is not None:
        return bias
    return bias._replace(pillar=random.Random(f"{key}:pillar").randrange(RING))


def serpentine_turns(rng, count):
    """The (gap, bridge) of each of count rulings: the gap, then the bridge end when both gap ends are not corners."""
    turns = []
    for _ in range(count):
        gap = rng.randrange(RING)
        ends = [v for v in (gap, (gap + 1) % RING) if not is_corner(v)]
        turns.append((gap, ends[rng.randrange(2)] if len(ends) == 2 else ends[0]))
    return turns


def neighbor_bridge(rng, count):
    """The bridge of the last of count rulings of a neighbor segment. rng is the fresh segment random of that neighbor."""
    return serpentine_turns(pattern_random(rng), count)[-1][1]


def serpentine_edges(g, rng, left_bridge=None):
    """Ring paths with one gap per ruling, joined by one axis edge next to the gap.

    Draws: the turns of the rulings, then the left virtual edge when left_bridge is None,
    then the row ends of each patch, then one crossing per group.
    left_bridge is the bridge of the facing ruling of the neighbor before the segment.
    """
    edges = []
    rulings = g.rulings
    for r, (ruling, (gap, bridge)) in enumerate(zip(rulings, serpentine_turns(rng, len(rulings)))):
        for v in range(RING):
            if v != gap:
                edges.append(edge_key(ruling[v], ruling[(v + 1) % RING]))
        nxt = rulings[r + 1][bridge] if r + 1 < len(rulings) else g.right
        if nxt >= 0 and nxt in g.axis[ruling[bridge]]:
            edges.append(edge_key(ruling[bridge], nxt))
    if g.left >= 0:
        if left_bridge is None:
            positions = [v for v in range(RING) if not is_corner(v)]
            left_bridge = positions[rng.randrange(len(positions))]
        edges.append(edge_key(g.left, rulings[0][left_bridge]))
    for index, axis_on_i, m in g.patches:
        def node(a, r):
            return index[(a, r)] if axis_on_i else index[(r, a)]
        for a in range(-m, m + 1):
            for r in range(-m, m):
                edges.append(edge_key(node(a, r), node(a, r + 1)))
        for a in range(-m, m):
            end = (-m, m)[rng.randrange(2)]
            edges.append(edge_key(node(a, end), node(a + 1, end)))
    for group in g.groups:
        if group:
            edges.append(edge_key(*group[rng.randrange(len(group))]))
    return edges


def ribs_root(g, rng, bias):
    """The root of the ribs pattern. A graph without a ruling takes a random root."""
    if bias.seam == RANDOM_SEAM or not g.rulings:
        return rng.randrange(len(g.adj))
    if bias.seam == SEEDED_SEAM:
        return g.rulings[0][rng.randrange(RING)]
    return g.rulings[0][bias.pillar]


def groove_positions(spacing):
    """The ring positions of the grooves: every non-corner position, or every second one in ring sequence."""
    return [v for v in range(RING) if not is_corner(v)][::spacing]


def lines_edges(g, spacing):
    """The axis edges of the grooves. A patch line is a groove when a crossing of it lands on a groove node."""
    axis_edges = [(a, b) for a in range(len(g.adj)) for b in g.axis[a] if a < b]
    if spacing == 1:
        return axis_edges
    grooves = set(groove_positions(spacing))
    position = {node: v for ruling in g.rulings for v, node in enumerate(ruling)}
    patch_line = {}
    for k, (index, axis_on_i, _) in enumerate(g.patches):
        for (i, j), node in index.items():
            patch_line[node] = (k, j if axis_on_i else i)
    crossings = set()
    lines = set()
    for group in g.groups:
        for p, other in group:
            # A crossing onto a node without a ruling counts as a groove.
            if position.get(other) is None or position[other] in grooves:
                crossings.add(edge_key(p, other))
                lines.add(patch_line[p])
    edges = []
    for a, b in axis_edges:
        if a in patch_line and b in patch_line:
            keep = patch_line[a] in lines
        elif a in patch_line or b in patch_line:
            keep = (a, b) in crossings
        else:
            keep = position.get(a, position.get(b)) in grooves
        if keep:
            edges.append((a, b))
    return edges


def join_pieces(g, rng, edges):
    """Join the pieces of edges into one tree of the segment graph.

    A union-find pass runs over the graph edges that touch no virtual node and are no crossing,
    in a sequence that rng shuffles. Then each virtual node and each crossing group adds one edge.
    """
    n = len(g.adj)
    crossings = {edge_key(a, b) for group in g.groups for a, b in group}
    virtual = [x for x in (g.left, g.right) if x >= 0]
    uf = list(range(n))

    def find(a):
        while uf[a] != a:
            uf[a] = uf[uf[a]]
            a = uf[a]
        return a

    out = list(edges)
    for a, b in edges:
        uf[find(a)] = find(b)
    candidates = [(a, b) for a in range(n) for b in g.adj[a]
                  if a < b and a not in virtual and b not in virtual and (a, b) not in crossings]
    rng.shuffle(candidates)
    for a, b in candidates:
        ra, rb = find(a), find(b)
        if ra != rb:
            uf[ra] = rb
            out.append((a, b))
    for x in virtual:
        out.append(edge_key(x, g.adj[x][rng.randrange(len(g.adj[x]))]))
    for group in g.groups:
        if group:
            out.append(edge_key(*group[rng.randrange(len(group))]))
    return out


def pattern_edges(g, rng, bias, left_bridge=None):
    """The pattern edges of the bias pattern on g, as sorted node pairs."""
    if bias.pattern == SERPENTINE_PATTERN:
        return serpentine_edges(g, rng, left_bridge)
    if bias.pattern == RIBS_PATTERN:
        return [edge_key(n, p) for n, p in parent_edges(bfs_tree(g, ribs_root(g, rng, bias)))]
    if bias.pattern == LINES_PATTERN:
        return lines_edges(g, bias.spacing)
    if bias.pattern == GILBERT_PATTERN:
        edges = []
        for run in ruling_runs(g):
            cells = gilbert_cells(len(run))
            for (v1, r1), (v2, r2) in zip(cells, cells[1:]):
                a, b = run[r1][v1], run[r2][v2]
                if b in g.adj[a]:
                    edges.append(edge_key(a, b))
        return edges
    return []


def weighted_pick(row, total, rng):
    """Pick one (node, weight) entry of row in proportion to its weight."""
    x = rng.random() * total
    for other, weight in row:
        x -= weight
        if x < 0.0:
            return other
    return next(other for other, weight in reversed(row) if weight > 0.0)


def pattern_wilson(g, rng, bias, pattern):
    """Wilson's algorithm whose walk steps in proportion to the pattern factor times the class factor."""
    n = len(g.adj)
    keep = 1.0 - bias.strength
    table = []
    for node in range(n):
        row = []
        for side, weight in ((g.axis, bias.p), (g.ring, 1.0 - bias.p)):
            for other in side[node]:
                row.append((other, weight if edge_key(node, other) in pattern else weight * keep))
        table.append((row, sum(w for _, w in row)))
    parent = [-1] * n
    in_tree = [False] * n
    in_tree[rng.randrange(n)] = True
    for start in range(n):
        node = start
        while not in_tree[node]:
            row, total = table[node]
            parent[node] = weighted_pick(row, total, rng)
            node = parent[node]
        # Overwriting parents erases the loops of the walk.
        node = start
        while not in_tree[node]:
            in_tree[node] = True
            node = parent[node]
    return parent


def sample_edges(g, rng, sampler, steps_per_node, bias, trace=None, left_bridge=None):
    """Segment edges as node pairs. At strength 0 the pairs of sample. At strength 1 the pattern edges.

    left_bridge is the serpentine bridge of the facing ruling of the neighbor before the segment, or None.
    """
    s = pattern_strength(bias)
    if s == 0.0:
        return parent_edges(sample(g, rng, sampler, steps_per_node, bias, trace))
    if sampler != "wilson" or bias.rule != DEGREE_RULE:
        raise ValueError("a pattern needs Wilson's algorithm with the degree rule")
    prng = pattern_random(rng)
    edges = pattern_edges(g, prng, bias, left_bridge)
    if s >= 1.0:
        if bias.pattern == GILBERT_PATTERN:
            return join_pieces(g, prng, edges)
        return edges
    return parent_edges(pattern_wilson(g, rng, bias, set(edges)))


def residue(start, final):
    """Share of the final tree edges that are also start tree edges."""
    final_edges = tree_edges_of(final)
    return len(final_edges & tree_edges_of(start)) / len(final_edges)


def steps_value(text):
    """Parse a comma-separated list of steps per node."""
    return tuple(int(t) for t in text.split(","))


def bias_value(text):

    """Parse a bias from 0 to 1 on the command line."""
    p = float(text)
    if not 0.0 <= p <= 1.0:
        raise argparse.ArgumentTypeError("the bias must be from 0 to 1")
    return p


def check_weighted_wilson(ap, uses_weighted_wilson, p):
    """At p = 0 or 1 the weighted walk cannot reach every node, so the loop-erased walk never ends."""
    if uses_weighted_wilson and not 0.0 < p < 1.0:
        ap.error("Wilson's algorithm with the degree rule needs a bias strictly between 0 and 1")


def strength_value(text):
    """Parse a pattern strength from 0 to 1 in steps of 0.05."""
    s = float(text)
    k = round(s * STRENGTH_STEPS)
    if not 0.0 <= s <= 1.0 or abs(k - s * STRENGTH_STEPS) > 1e-9:
        raise argparse.ArgumentTypeError("the strength must be from 0 to 1 in steps of 0.05")
    return k / STRENGTH_STEPS


def check_pattern(ap, bias, sampler):
    """A pattern weighs the walk of Wilson's algorithm with the degree rule. Only ribs has a seam."""
    if bias.seam != RANDOM_SEAM and bias.pattern != RIBS_PATTERN:
        ap.error("--seam needs --pattern ribs")
    if bias.spacing != 1 and bias.pattern != LINES_PATTERN:
        ap.error("--line-spacing needs --pattern lines")
    if bias.pattern != UNIFORM_PATTERN and (sampler != "wilson" or bias.rule != DEGREE_RULE):
        ap.error("--pattern needs --sampler wilson and --rule degree")


def pattern_suffix(bias):
    """File name suffix of the pattern. The uniform pattern gets none."""
    if bias.pattern == UNIFORM_PATTERN:
        return ""
    if bias.pattern == LINES_PATTERN:
        return "_lines" + (f"_spacing{bias.spacing}" if bias.spacing != 1 else "")
    seam = f"_{bias.seam}" if bias.pattern == RIBS_PATTERN and bias.seam != RANDOM_SEAM else ""
    return f"_{bias.pattern}{seam}_s{bias.strength:g}"


def pattern_label(bias):
    if bias.pattern == LINES_PATTERN:
        return f"lines spacing {bias.spacing}"
    seam = f" {bias.seam}" if bias.pattern == RIBS_PATTERN and bias.seam != RANDOM_SEAM else ""
    return f"{bias.pattern}{seam}"


def pattern_text(bias):
    """The pattern and its strength in words. Lines has no strength."""
    if bias.pattern == LINES_PATTERN:
        return pattern_label(bias)
    return f"{pattern_label(bias)} strength {bias.strength:g}"


def check_scaled_steps(ap, scaled, p):
    """At p = 0 or 1 the scaled step count is infinite."""
    if scaled and not 0.0 < p < 1.0:
        ap.error("--scaled-steps needs a bias strictly between 0 and 1")


def start_suffix(sampler, bias, steps_per_node):
    """File name suffix of the start tree, the scaled steps and the step count. Wilson's algorithm gets none."""
    if sampler == "wilson":
        return ""
    out = "" if bias.start == BFS_START else "_" + bias.start
    if bias.scaled:
        out += "_scaled"
    if steps_per_node == 0:
        out += "_start0"
    elif steps_per_node != DEFAULT_STEPS:
        out += f"_steps{steps_per_node}"
    return out


def bias_suffix(sampler, bias):
    """File name suffix. The class rule gets none at the default bias or with Wilson's algorithm."""
    if bias.rule == CLASS_RULE:
        if sampler == "wilson" or bias.p == DEFAULT_BIAS:
            return ""
        return f"_bias{bias.p:g}"
    base = "" if bias.p == DEFAULT_BIAS else f"_bias{bias.p:g}"
    return base + "_" + DEGREE_RULE


class Segment:
    """Tree edges of one segment in local node coordinates."""

    def __init__(self, length):
        self.length = length
        self.edges = []
        self.left_cross = []
        self.right_cross = []
        # start and residue are None for Wilson's algorithm. curve is None unless a curve start.
        self.start = None
        self.residue = None
        self.curve = None


def build_segment(kind, length, rng, sampler, steps_per_node, bias, left_bridge=None):
    with_left = kind == "connector"
    with_right = kind in ("connector", "forest")
    g, left, right = segment_graph(length, with_left, with_right)
    trace = {}
    edges = sample_edges(g, rng, sampler, steps_per_node, bias, trace, left_bridge)
    seg = Segment(length)
    if "start" in trace:
        seg.start = trace["start"]
        final = {edge_key(a, b) for a, b in edges}
        seg.residue = len(final & tree_edges_of(trace["start"])) / len(final)
    seg.curve = trace.get("curve")
    real = length * RING
    for node, p in edges:
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


def turns_at_neighbor(bias):
    """A serpentine connector turns into its neighbor next to the gap of the facing neighbor ruling."""
    return bias.pattern == SERPENTINE_PATTERN and pattern_strength(bias) > 0.0


def build_layout(layout, seed, segments, length, sampler, steps_per_node, bias, first_index=0):
    bias = with_pillar(bias, f"{seed}:{layout}")
    out = []
    for k in range(first_index, first_index + segments):
        rng = seg_rng(seed, layout, k)
        kind = segment_kind(layout, k)
        left_bridge = None
        if kind == "connector" and turns_at_neighbor(bias):
            left_bridge = neighbor_bridge(seg_rng(seed, layout, k - 1), length)
        out.append(build_segment(kind, length, rng, sampler, steps_per_node, bias, left_bridge))
    return out


def paint_band(segments):
    """Return a block grid, True for path, indexed [ring_block][along_block]."""
    total = sum(s.length for s in segments)
    width = 2 * total
    grid = [[False] * width for _ in range(BLOCKS_AROUND)]
    offset = 0
    for seg in segments:
        present = {n for e in seg.edges for n in e}
        present |= {(0, v) for v in seg.left_cross} | {(seg.length - 1, v) for v in seg.right_cross}
        for u in range(seg.length):
            for v in range(RING):
                if (u, v) in present:
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


def band_rows(grid, scale):
    """Pixel rows of one band: the chunk strip, then the surface."""
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
    return rows


def render_band(grid, path, scale):
    rows = band_rows(grid, scale)
    write_png(path, len(rows[0]) // 3, len(rows), rows)


# A 5x8 bitmap font. Row 6 is the baseline. Row 7 holds the descender.
GLYPHS = """
a .....|.....|.###.|....#|.####|#...#|.####|.....
b #....|#....|####.|#...#|#...#|#...#|####.|.....
c .....|.....|.####|#....|#....|#....|.####|.....
d ....#|....#|.####|#...#|#...#|#...#|.####|.....
e .....|.....|.###.|#...#|#####|#....|.###.|.....
f ..##.|.#...|####.|.#...|.#...|.#...|.#...|.....
g .....|.....|.####|#...#|#...#|.####|....#|.###.
h #....|#....|#.##.|##..#|#...#|#...#|#...#|.....
i ..#..|.....|.##..|..#..|..#..|..#..|.###.|.....
l .##..|..#..|..#..|..#..|..#..|..#..|.###.|.....
m .....|.....|##.#.|#.#.#|#.#.#|#.#.#|#.#.#|.....
n .....|.....|#.##.|##..#|#...#|#...#|#...#|.....
o .....|.....|.###.|#...#|#...#|#...#|.###.|.....
p .....|.....|####.|#...#|#...#|####.|#....|#....
r .....|.....|#.##.|##..#|#....|#....|#....|.....
s .....|.....|.####|#....|.###.|....#|####.|.....
t .#...|.#...|####.|.#...|.#...|.#..#|..##.|.....
u .....|.....|#...#|#...#|#...#|#..##|.##.#|.....
w .....|.....|#...#|#...#|#.#.#|#.#.#|.#.#.|.....
0 .###.|#...#|#..##|#.#.#|##..#|#...#|.###.|.....
1 ..#..|.##..|..#..|..#..|..#..|..#..|.###.|.....
2 .###.|#...#|....#|...#.|..#..|.#...|#####|.....
3 ####.|....#|....#|.###.|....#|....#|####.|.....
4 ...#.|..##.|.#.#.|#..#.|#####|...#.|...#.|.....
5 #####|#....|####.|....#|....#|#...#|.###.|.....
6 ..##.|.#...|#....|####.|#...#|#...#|.###.|.....
7 #####|....#|...#.|..#..|.#...|.#...|.#...|.....
8 .###.|#...#|#...#|.###.|#...#|#...#|.###.|.....
9 .###.|#...#|#...#|.####|....#|...#.|.##..|.....
. .....|.....|.....|.....|.....|.##..|.##..|.....
"""
FONT = {" ": ["....."] * 8}
for _line in GLYPHS.strip().splitlines():
    FONT[_line[0]] = _line[2:].split("|")
GLYPH_W = 5
GLYPH_H = 8
LABEL_BG = (236, 236, 236)
LABEL_INK = (24, 24, 28)
LABEL_PAD = 6


def label_rows(text, width, max_scale=3):
    """Pixel rows of a label strip of the given width, with the text at the left."""
    advance = GLYPH_W + 1
    text_w = len(text) * advance - 1
    scale = max(1, min(max_scale, (width - 2 * LABEL_PAD) // text_w))
    height = GLYPH_H * scale + 2 * LABEL_PAD
    rows = [bytearray(LABEL_BG * width) for _ in range(height)]
    for k, ch in enumerate(text):
        glyph = FONT[ch]
        for gy in range(GLYPH_H):
            for gx in range(GLYPH_W):
                if glyph[gy][gx] != "#":
                    continue
                x0 = LABEL_PAD + (k * advance + gx) * scale
                for yy in range(LABEL_PAD + gy * scale, LABEL_PAD + (gy + 1) * scale):
                    for xx in range(x0, min(width, x0 + scale)):
                        rows[yy][3 * xx:3 * xx + 3] = bytes(LABEL_INK)
    return rows


def label_scale(texts, width, max_scale=3):
    """The largest glyph scale up to max_scale at which every text fits the width."""
    advance = GLYPH_W + 1
    return max(1, min([max_scale] + [(width - 2 * LABEL_PAD) // (len(t) * advance - 1) for t in texts]))


def start_panels(p):
    """Label, sampler, bias and steps per node of each panel of the start comparison."""
    panels = [(f"{start} {DEFAULT_STEPS}", "originshift", Bias(p, DEGREE_RULE, start), DEFAULT_STEPS) for start in STARTS]
    panels.append(("bfs scaled", "originshift", Bias(p, DEGREE_RULE, BFS_START, True), DEFAULT_STEPS))
    panels.append(("wilson", "wilson", Bias(p, DEGREE_RULE), DEFAULT_STEPS))
    return panels


def start_only_panels(p):
    """The four start trees with no Origin Shift step."""
    return [(f"{start} start", "originshift", Bias(p, DEGREE_RULE, start), 0) for start in STARTS]


def render_compare_starts(out_dir, seed, chunks, length, p, scale):
    """Stack the connector band of each start panel, each under its label strip. Write each band too."""
    rows = []
    panels = start_panels(p)
    for label, sampler, bias, spn in panels:
        segs = build_layout("connector", seed, chunks, length, sampler, spn, bias)
        grid = paint_band(segs)
        name = f"band_connector_{sampler}{bias_suffix(sampler, bias)}{start_suffix(sampler, bias, spn)}.png"
        render_band(grid, os.path.join(out_dir, name), scale)
        print("wrote", name)
        band = band_rows(grid, scale)
        width = len(band[0]) // 3
        rows += label_rows(label, width, label_scale([t for t, _, _, _ in panels], width))
        rows += band
    name = f"compare_starts_bias{p:g}.png"
    write_png(os.path.join(out_dir, name), len(rows[0]) // 3, len(rows), rows)
    print("wrote", name)


def compare_panels(p):
    """Label and connector band of each of the four samplers at one bias."""
    class_bias = Bias(p, CLASS_RULE)
    degree_bias = Bias(p, DEGREE_RULE)
    return (
        (f"class {p:g}", "originshift", class_bias),
        (f"degree {p:g}", "originshift", degree_bias),
        (f"wilson degree {p:g}", "wilson", degree_bias),
        ("wilson uniform", "wilson", class_bias),
    )


def render_compare(path, seed, chunks, length, steps_per_node, p, scale):
    """Stack the connector band of each sampler, each under its label strip."""
    rows = []
    for label, sampler, bias in compare_panels(p):

        segs = build_layout("connector", seed, chunks, length, sampler, steps_per_node, bias)
        band = band_rows(paint_band(segs), scale)
        rows += label_rows(label, len(band[0]) // 3)
        rows += band
    write_png(path, len(rows[0]) // 3, len(rows), rows)


def forest_state(nodes, edges):
    """Nodes, edges, components and independent loops of a graph."""
    uf = {n: n for n in nodes}

    def find(a):
        while uf[a] != a:
            uf[a] = uf[uf[a]]
            a = uf[a]
        return a

    for a, b in edges:
        uf.setdefault(a, a)
        uf.setdefault(b, b)
        ra, rb = find(a), find(b)
        if ra != rb:
            uf[ra] = rb
    components = len({find(n) for n in uf})
    return len(uf), len(edges), components, len(edges) - len(uf) + components


def walls_by_design(bias):
    """Lines with a spacing above 1 leaves whole node columns as wall."""
    return bias.pattern == LINES_PATTERN and bias.spacing != 1


def band_state(segments, every_node=True):
    """forest_state of a connector band from segment 0 up to its last tree segment.

    Its nodes are the nodes that carry an edge, plus every non-corner node when every_node is True.
    A tree segment has no crossing, so no crossing leaves that part of the band.
    """
    if len(segments) % 2 == 0:
        segments = segments[:-1]
    nodes = set()
    edges = []
    offset = 0
    for seg in segments:
        for u in range(seg.length):
            for v in range(RING):
                if every_node and not is_corner(v):
                    nodes.add((offset + u, v))
        for (u1, v1), (u2, v2) in seg.edges:
            edges.append(((offset + u1, v1), (offset + u2, v2)))
        for v in seg.left_cross:
            edges.append(((offset - 1, v), (offset, v)))
        for v in seg.right_cross:
            edges.append(((offset + seg.length - 1, v), (offset + seg.length, v)))
        offset += seg.length
    return forest_state(nodes, edges)


def state_text(state):
    nodes, edges, components, loops = state
    tree = "one tree" if components == 1 and loops == 0 else "not one tree"
    return f"{nodes} nodes, {edges} edges, {components} component(s), {loops} loop(s): {tree}"


def pattern_panels(p, pattern, seam):
    """Label and bias of each strength of the pattern comparison. Lines has a panel per line spacing."""
    if pattern == LINES_PATTERN:
        return [(pattern_label(b), b) for b in (Bias(p, DEGREE_RULE, pattern=pattern, spacing=k) for k in LINE_SPACINGS)]
    panels = []
    for s in COMPARE_STRENGTHS:
        bias = Bias(p, DEGREE_RULE, pattern=pattern, strength=s, seam=seam)
        panels.append((f"{pattern_label(bias)} {s:g}", bias))
    return panels


def render_compare_patterns(out_dir, seed, chunks, length, p, scale, pattern, seam):
    """Stack the connector band of each strength of one pattern, each under its label strip. Write each band too."""
    rows = []
    panels = pattern_panels(p, pattern, seam)
    for label, bias in panels:
        segs = build_layout("connector", seed, chunks, length, "wilson", DEFAULT_STEPS, bias)
        print(f"{label}: connector band {state_text(band_state(segs, not walls_by_design(bias)))}")
        grid = paint_band(segs)
        name = f"band_connector_wilson{bias_suffix('wilson', bias)}{pattern_suffix(bias)}.png"
        render_band(grid, os.path.join(out_dir, name), scale)
        print("wrote", name)
        band = band_rows(grid, scale)
        width = len(band[0]) // 3
        rows += label_rows(label, width, label_scale([t for t, _ in panels], width))
        rows += band
    seam_part = f"_{seam}" if pattern == RIBS_PATTERN and seam != RANDOM_SEAM else ""
    name = f"compare_pattern_{pattern}{seam_part}_bias{p:g}.png"
    write_png(os.path.join(out_dir, name), len(rows[0]) // 3, len(rows), rows)
    print("wrote", name)


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


def wilson_residue(seed, kind, length, count, bias):
    """Mean overlap of two independent Wilson trees of one segment graph. A fully mixed sampler reaches it."""
    g, _, _ = segment_graph(length, kind == "connector", kind in ("connector", "forest"))
    total = 0.0
    for k in range(count):
        rng = seg_rng(seed, f"stats-{kind}-baseline", k)
        total += residue(wilson(g, rng, bias), wilson(g, rng, bias))
    return total / count


def stats(seed, length, count, bias, steps_list):
    if bias.rule == DEGREE_RULE:
        print(f"bias {bias.p:g}, rule degree. Wilson's algorithm takes the same weighted steps.")
    else:
        print(f"bias {bias.p:g}, rule class. Wilson's algorithm ignores the bias.")
    scaled = ", scaled steps" if bias.scaled else ""
    print(f"start {bias.start}{scaled}.")
    print("residue: share of the final tree edges that are also start tree edges.")
    print("base: overlap of two independent Wilson trees. The Wilson row shows it as its residue.")
    print("mixed: overlap of an independent Wilson tree with the same start tree. A fully mixed Origin Shift reaches it.")
    print("layout      sampler      steps/node  dead ends  corner pass  left cross  right cross  residue   -base   mixed  -mixed")
    runs = [("wilson", 0)] + [("originshift", spn) for spn in steps_list]
    curves = {}
    for kind in ("tree", "connector", "forest"):
        baseline = wilson_residue(seed, kind, length, count, bias)
        g, _, _ = segment_graph(length, kind == "connector", kind in ("connector", "forest"))
        for sampler, spn in runs:
            ends = []
            passes = []
            lefts = []
            rights = []
            residues = []
            mixed = []
            for k in range(count):
                rng = seg_rng(seed, f"stats-{kind}-{sampler}-{spn}", k)
                seg = build_segment(kind, length, rng, sampler, spn, bias)
                ends.append(dead_end_fraction(seg))
                passes.append(corner_pass_fraction(seg))
                lefts.append(len(seg.left_cross))
                rights.append(len(seg.right_cross))
                residues.append(baseline if seg.residue is None else seg.residue)
                if seg.start is None:
                    mixed.append(baseline)
                else:
                    mixed.append(residue(seg.start, wilson(g, seg_rng(seed, f"stats-{kind}-mixed-{spn}", k), bias)))
                if seg.curve is not None:
                    kept, total = curves.get(kind, (0, 0))
                    curves[kind] = (kept + seg.curve[0], total + seg.curve[1])
            shown = effective_steps(spn, bias) if sampler == "originshift" else spn
            mean_residue = sum(residues) / count
            mean_mixed = sum(mixed) / count
            print(f"{kind:<11} {sampler:<12} {shown:>10}  {sum(ends)/count:>9.3f}  {sum(passes)/count:>11.3f}  {sum(lefts)/count:>10.2f}  {sum(rights)/count:>11.2f}  {mean_residue:>7.3f}  {mean_residue - baseline:>+6.3f}  {mean_mixed:>6.3f}  {mean_residue - mean_mixed:>+6.3f}")
    for kind, (kept, total) in curves.items():
        print(f"{kind}: the start keeps {kept} of {total} curve edges ({kept / total:.3f}).")


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
    ap.add_argument("--steps-per-node", type=int, default=DEFAULT_STEPS)
    ap.add_argument("--bias", type=bias_value, default=DEFAULT_BIAS, help="axis chance of the class rule or axis edge weight of the degree rule")
    ap.add_argument("--rule", choices=(CLASS_RULE, DEGREE_RULE), default=DEFAULT_RULE, help="the bias rule of the walker step")
    ap.add_argument("--start", choices=STARTS, default=BFS_START, help="the start tree of Origin Shift")
    ap.add_argument("--scaled-steps", action="store_true", help="divide the Origin Shift steps per node by min(P, 1 - P)")
    ap.add_argument("--scale", type=int, default=4, help="pixels per block")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "images"))
    ap.add_argument("--stats", action="store_true", help="print sampler statistics and exit")
    ap.add_argument("--stats-steps", type=steps_value, default=(1, 4, 16, 64), help="comma-separated Origin Shift steps per node for --stats")
    ap.add_argument("--stats-count", type=int, default=200, help="segments per row for --stats")
    ap.add_argument("--compare", action="store_true", help="render the connector band of the four samplers at one bias and exit")
    ap.add_argument("--compare-starts", action="store_true", help="render the connector band of each start tree at one bias and exit")
    ap.add_argument("--pattern", choices=PATTERNS, default=UNIFORM_PATTERN, help="the pattern type of Wilson's algorithm")
    ap.add_argument("--strength", type=strength_value, default=0.0, help="the pattern strength, from 0 to 1 in steps of 0.05")
    ap.add_argument("--seam", choices=SEAMS, default=RANDOM_SEAM, help="the root of the ribs pattern")
    ap.add_argument("--compare-patterns", action="store_true", help="render the connector band of the pattern at four strengths, or of lines at each spacing, and exit")
    ap.add_argument("--line-spacing", type=int, choices=LINE_SPACINGS, default=1, help="node columns per groove of lines")
    args = ap.parse_args()
    bias = Bias(args.bias, args.rule, args.start, args.scaled_steps, args.pattern, args.strength, args.seam, None, args.line_spacing)
    if args.compare_patterns:
        if args.pattern == UNIFORM_PATTERN or args.rule != DEGREE_RULE:
            ap.error("--compare-patterns needs --rule degree and a --pattern other than uniform")
        check_pattern(ap, bias, "wilson")
        check_weighted_wilson(ap, True, args.bias)
        os.makedirs(args.out, exist_ok=True)
        render_compare_patterns(args.out, args.seed, args.chunks, args.length, args.bias, args.scale, args.pattern, args.seam)
        return
    check_pattern(ap, bias, args.sampler)
    if args.pattern != UNIFORM_PATTERN and (args.stats or args.compare or args.compare_starts):
        ap.error("--stats, --compare and --compare-starts take no --pattern")
    if args.compare_starts and args.rule != DEGREE_RULE:
        ap.error("--compare-starts needs --rule degree")
    check_weighted_wilson(ap, args.compare or args.compare_starts or (args.rule == DEGREE_RULE and (args.stats or args.sampler == "wilson")), args.bias)
    check_scaled_steps(ap, args.scaled_steps or args.compare_starts, args.bias)
    if args.stats:

        stats(args.seed, args.length, args.stats_count, bias, args.stats_steps)
        return
    if args.compare:
        os.makedirs(args.out, exist_ok=True)
        name = f"compare_bias{args.bias:g}.png"
        render_compare(os.path.join(args.out, name), args.seed, args.chunks, args.length, args.steps_per_node, args.bias, args.scale)
        print("wrote", name)
        return
    if args.compare_starts:
        os.makedirs(args.out, exist_ok=True)
        render_compare_starts(args.out, args.seed, args.chunks, args.length, args.bias, args.scale)
        return
    suffix = bias_suffix(args.sampler, bias) + start_suffix(args.sampler, bias, args.steps_per_node) + pattern_suffix(bias)
    closeup = os.path.join(args.out, "closeup")
    os.makedirs(closeup, exist_ok=True)
    for layout in ("oneroot", "forest", "connector"):
        segs = build_layout(layout, args.seed, args.chunks, args.length, args.sampler, args.steps_per_node, bias)
        name = f"band_{layout}_{args.sampler}{suffix}.png"
        if args.pattern != UNIFORM_PATTERN and layout == "connector":
            print(f"{layout} band: {state_text(band_state(segs, not walls_by_design(bias)))}")
        render_band(paint_band(segs), os.path.join(args.out, name), args.scale)
        print("wrote", name)
        render_band(paint_band(segs[:CLOSEUP_CHUNKS]), os.path.join(closeup, name), CLOSEUP_SCALE)
        print("wrote", os.path.join("closeup", name))
    name = f"graph_view{suffix}.svg"
    svg_graph_view(os.path.join(args.out, name), args.seed, args.length, args.sampler, args.steps_per_node, bias)
    print("wrote", name)


if __name__ == "__main__":
    main()
