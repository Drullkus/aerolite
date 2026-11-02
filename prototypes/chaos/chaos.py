#!/usr/bin/env python3
"""Chaos prototype for the outer layer of a pillar, an I-beam or a bridge plate.

A jittered 2D point set on the unwrapped band cuts the outer layer into stones,
like cobblestone. Two cell types exist:

- voronoi: each block belongs to the nearest point on the band. The groove is a
  one-block border on one side of each stone border.
- delaunay: a small groove ring sits at the center of each stone, and a straight
  one-block groove joins the rings of every two stones that share a border.

surfacepaths.py gives the groove graph, the loop share rationing, the surface
path enumeration, the checks and the renders. This file adds the point set, the
stone labels, the plus crossing repair, the dual lines and the surface path rules
of chaos.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md
"""

import argparse
import math
import os
import random
import sys
import zlib
from collections import Counter, namedtuple
from concurrent.futures import ProcessPoolExecutor
from functools import lru_cache

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "patterns"))
import surfacepaths as sp  # noqa: E402

CHUNK = 16
RINGS = {48: "pillar", 64: "I-beam", 28: "bridge plate"}
PILLAR_RING = 48
FACE = 12
PILLAR_CORNERS = tuple(range(0, PILLAR_RING, FACE))
CELLS = ("voronoi", "delaunay")
SPACINGS = range(4, 13)
BOX_HALF = 7
PATCH_HALF = 4
PATCH_FACES = (1, 3)
DEFAULT_BOX = 89
DEFAULT_RADIUS = 1
EPS = 1e-9
OUT_DIR = os.path.join(HERE, "images")
DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

# The largest jitter per node spacing at which every stone stays 3 blocks across.
# `--min-spacing` measures it. Spacings 4 and 5 reach 3 at no jitter, so they have no cap.
JITTER_CAP = {6: 0.25, 7: 0.6, 8: 0.7, 9: 0.85, 10: 0.85}

Params = namedtuple("Params", "cells spacing jitter radius seed pillar surface cut share box chance face ring cap",
                    defaults=(2, PILLAR_RING, True))
Labels = namedtuple("Labels", "labels grooves vgrooves box hubs dual")
Base = namedtuple("Base", "band pins vgrooves hubs paths")
Scene = namedtuple("Scene", "band removed graph stats marks cut")


# ---------------------------------------------------------------- helpers


def wrap(dr, ring):
    """A ring difference wrapped to [-ring/2, ring/2)."""
    return (dr + ring // 2) % ring - ring // 2


def shift(a, dr, da, fill):
    """out[r, y] = a[r + dr, y + da] with the ring wrapped, or `fill` outside the axis range."""
    out = np.roll(a, -dr, axis=0)
    if da > 0:
        pad = np.full((a.shape[0], da), fill, dtype=a.dtype)
        out = np.concatenate([out[:, da:], pad], axis=1)
    elif da < 0:
        pad = np.full((a.shape[0], -da), fill, dtype=a.dtype)
        out = np.concatenate([pad, out[:, :da]], axis=1)
    return out


def components(mask, labels=None):
    """The component id of each mask block, 4-connected with the ring wrapped, -1 off the mask.

    With `labels`, two neighbor blocks join only when their labels are equal.
    """
    n = mask.size
    idx = np.arange(n).reshape(mask.shape)
    right = mask & np.roll(mask, -1, axis=0)
    down = mask[:, :-1] & mask[:, 1:]
    if labels is not None:
        right &= labels == np.roll(labels, -1, axis=0)
        down &= labels[:, :-1] == labels[:, 1:]
    a = np.concatenate([idx[right], idx[:, :-1][down]])
    b = np.concatenate([np.roll(idx, -1, axis=0)[right], idx[:, 1:][down]])
    parent = np.arange(n)
    while True:
        pa, pb = parent[a], parent[b]
        diff = pa != pb
        if not diff.any():
            break
        np.minimum.at(parent, np.maximum(pa[diff], pb[diff]), np.minimum(pa[diff], pb[diff]))
        while True:
            pp = parent[parent]
            if np.array_equal(pp, parent):
                break
            parent = pp
    return np.where(mask, parent.reshape(mask.shape), -1)


def squares(mask, k):
    """True at the low corner of each k x k square of mask blocks."""
    out = mask.copy()
    for dr in range(k):
        for da in range(k):
            if dr or da:
                out &= shift(mask, dr, da, False)
    return out


def groups(comp):
    """Map each component id to the arrays (ring, local axis) of its blocks."""
    flat = comp.ravel()
    perm = np.argsort(flat, kind="stable")
    ids = flat[perm]
    cut = np.flatnonzero(np.diff(ids)) + 1
    out = {}
    for part in np.split(perm, cut):
        cid = int(flat[part[0]])
        if cid >= 0:
            out[cid] = np.unravel_index(part, comp.shape)
    return out


# ---------------------------------------------------------------- point set


M64 = (1 << 64) - 1


def _mix(x):
    x = x ^ (x >> np.uint64(30))
    x = x * np.uint64(0xBF58476D1CE4E5B9)
    x = x ^ (x >> np.uint64(27))
    x = x * np.uint64(0x94D049BB133111EB)
    return x ^ (x >> np.uint64(31))


def cell_unit(seed, pillar, cells, salt):
    """A value in [0, 1) per grid cell. It depends only on the seed, the pillar, the cell and the salt."""
    start = (seed * 0x9E3779B97F4A7C15 + pillar * 0xC2B2AE3D27D4EB4F + (salt + 1) * 0xD1B54A32D192ED03) & M64
    with np.errstate(over="ignore"):
        h = np.full(cells.shape[:-1], start, dtype=np.uint64)
        for c in range(cells.shape[-1]):
            h = _mix(h ^ (cells[..., c].astype(np.int64).view(np.uint64) + np.uint64(0x632BE59BD9B4E019)))
    return (h >> np.uint64(11)).astype(np.float64) * (1.0 / (1 << 53))


def grid(ring, spacing):
    """(cells around the ring, ring cell width): the ring length over the spacing, rounded half up."""
    count = max(1, int(math.floor(ring / spacing + 0.5)))
    return count, ring / count


def jitter_of(P):
    """The jitter after the cap of its node spacing."""
    if P.cap:
        return min(P.jitter, JITTER_CAP.get(P.spacing, 1.0))
    return P.jitter


CODE_BASE = 1 << 12
CODE_OFF = 1 << 30


def nearest(P, axis0, length):
    """The code of the nearest point to each block center on the band. A tie takes the lowest code.

    The grid cell (i, j) spans ring cell width i to i + 1 around and spacing j to j + 1 along.
    Its point sits at the cell center moved by up to half a cell times the jitter.
    A point lies inside its own cell, so the nearest point is at most 2 cells away.
    """
    ring, s = P.ring, P.spacing
    count, width = grid(ring, s)
    jit = jitter_of(P)
    j_lo = int(math.floor(axis0 / s)) - 2
    j_hi = int(math.floor((axis0 + length) / s)) + 2
    I, J = np.meshgrid(np.arange(count), np.arange(j_lo, j_hi + 1), indexing="ij")
    cells = np.stack([I, J], axis=-1)
    pr = (I + 0.5 + jit * (cell_unit(P.seed, P.pillar, cells, 0) - 0.5)) * width
    pa = (J + 0.5 + jit * (cell_unit(P.seed, P.pillar, cells, 1) - 0.5)) * s
    br = (np.arange(ring) + 0.5)[:, None] * np.ones((1, length))
    ba = np.ones((ring, 1)) * (axis0 + np.arange(length) + 0.5)[None, :]
    bi = np.floor(br / width).astype(np.int64)
    bj = np.floor(ba / s).astype(np.int64)
    best_d = np.full((ring, length), np.inf)
    best = np.zeros((ring, length), dtype=np.int64)
    for di in range(-2, 3):
        for dj in range(-2, 3):
            ci = (bi + di) % count
            cj = bj + dj
            dr = (br - pr[ci, cj - j_lo] + ring / 2) % ring - ring / 2
            d = dr * dr + (ba - pa[ci, cj - j_lo]) ** 2
            code = (cj + CODE_OFF) * CODE_BASE + ci
            better = (d < best_d - EPS) | ((np.abs(d - best_d) <= EPS) & (code < best))
            best_d = np.where(better, d, best_d)
            best = np.where(better, code, best)
    return best


def box_masks(P, axis0, length):
    """The box mask of a window on the pillar ring. The 9x9 patch inside each bezel is not box."""
    box = np.zeros((P.ring, length), dtype=bool)
    if P.box is None:
        return box
    a = axis0 + np.arange(length)
    rows = np.abs(a - P.box) <= BOX_HALF
    r = np.arange(P.ring)
    cols = (np.abs(r % FACE - FACE // 2) <= PATCH_HALF) & np.isin(r // FACE, PATCH_FACES)
    patch = cols[:, None] & (np.abs(a - P.box) <= PATCH_HALF)[None, :]
    return rows[None, :] & ~patch


# ---------------------------------------------------------------- labels


T_PAIRS = {(0, 1): (2, 3), (2, 3): (0, 1), (0, 2): (1, 3), (1, 3): (0, 2)}
T_CORNERS = ((0, 0), (1, 0), (0, 1), (1, 1))


def round_corners(L, radius):
    """The corner radius: at each T junction, the stone with the flat side takes the corner triangles of the other two.

    A T junction is a block corner whose 4 blocks hold 3 labels, with the repeated
    label on two neighbor blocks. That label is the flat side. Each of the two
    other blocks is the corner of its stone. The corner triangle holds the blocks
    of that stone at i blocks around and j blocks along from the corner block,
    with i + j < radius. A block that two triangles claim takes the nearer
    junction, then the lower label.
    """
    ring, rows = L.shape
    win = [L, shift(L, 1, 0, -2), shift(L, 0, 1, -2), shift(L, 1, 1, -2)]
    claims = {}
    full = (win[0] >= 0) & (win[1] >= 0) & (win[2] >= 0) & (win[3] >= 0)
    for r, y in zip(*np.nonzero(full)):
        q = [int(w[r, y]) for w in win]
        if len(set(q)) != 3:
            continue
        flat = corners = None
        for (a, b), others in T_PAIRS.items():
            if q[a] == q[b]:
                flat, corners = q[a], others
        if flat is None:
            continue
        for k in corners:
            dx, dy = T_CORNERS[k]
            sx, sy = (-1 if dx == 0 else 1), (-1 if dy == 0 else 1)
            for i in range(radius):
                for j in range(radius - i):
                    p = ((int(r) + dx + sx * i) % ring, int(y) + dy + sy * j)
                    if 0 <= p[1] < rows and L[p] == q[k]:
                        claim = (i + j, flat)
                        if p not in claims or claim < claims[p]:
                            claims[p] = claim
    out = L.copy()
    for p, (_, lab) in claims.items():
        out[p] = lab
    return out


def thick(L):
    """True where a block lies in a 2x2 square of one label."""
    u = (L >= 0) & (L == shift(L, 1, 0, -2)) & (L == shift(L, 0, 1, -2)) & (L == shift(L, 1, 1, -2))
    return u | shift(u, -1, 0, False) | shift(u, 0, -1, False) | shift(u, -1, -1, False)


NB8 = tuple((dr, da) for dr in (-1, 0, 1) for da in (-1, 0, 1) if dr or da)


def majority_other(L):
    """For each block, the most frequent other label among its 8 neighbors, lowest on a tie, or -1."""
    nbs = [shift(L, dr, da, -2) for dr, da in NB8]
    best = np.full(L.shape, -1, dtype=np.int64)
    best_n = np.zeros(L.shape, dtype=np.int32)
    for c in nbs:
        n = np.zeros(L.shape, dtype=np.int32)
        for s in nbs:
            n += s == c
        ok = (c >= 0) & (c != L)
        better = ok & ((n > best_n) | ((n == best_n) & (c < best)))
        best = np.where(better, c, best)
        best_n = np.where(better, n, best_n)
    return best


def groove_mask(L):
    """The one-sided groove rule: a block is a groove when its 2x2 window down and along holds two labels.

    So a border between two columns is a groove in the left column, and a border
    between two rows is a groove in the upper row. Box blocks do not count.
    """
    w = [L, shift(L, 1, 0, -2), shift(L, 0, 1, -2), shift(L, 1, 1, -2)]
    g = np.zeros(L.shape, dtype=bool)
    for i in range(4):
        for j in range(i + 1, 4):
            g |= (w[i] >= 0) & (w[j] >= 0) & (w[i] != w[j])
    return g & (L >= 0)


def thin_pass(L, frozen):
    """Relabel every block outside a one-label 2x2 square to its majority neighbor label, until none is left.

    A block that stays thin under its new label also gives that label to the rest
    of the 2x2 square around it that holds the most of that label.
    """
    ring, rows = L.shape
    for _ in range(40):
        thin = ~thick(L) & (L >= 0) & ~frozen
        if not thin.any():
            return
        tgt = majority_other(L)
        upd = thin & (tgt >= 0)
        if not upd.any():
            return
        L[upd] = tgt[upd]
        still = upd & ~thick(L)
        for r, y in sorted(zip(*np.nonzero(still)), key=lambda p: (p[1], p[0])):
            t = L[r, y]
            best = None
            for dr in (-1, 0):
                for da in (-1, 0):
                    sq = [((r + dr + i) % ring, y + da + j) for i in (0, 1) for j in (0, 1)]
                    if not all(0 <= q[1] < rows and L[q] >= 0 and not frozen[q] for q in sq):
                        continue
                    n = sum(L[q] == t for q in sq)
                    if best is None or n > best[0]:
                        best = (n, sq)
            if best:
                for q in best[1]:
                    L[q] = t


def merge_tiny(L, G, ends, history, width):
    """Relabel each face without a square of `width` x `width` wall blocks, with the grooves of its own label around it.

    The face takes the most frequent label next to it that has no tiny face, else
    the most frequent higher label, so two tiny faces never swap labels. A face
    whose first block is in `history` comes back after an earlier merge, so its
    whole stone takes the new label. A face that touches the window ends stays.
    Returns True when a label changed.
    """
    ring, rows = L.shape
    wall = (L >= 0) & ~G
    comp = components(wall)
    good = set(np.unique(comp[squares(wall, width)]).tolist())
    skip = set(np.unique(comp[ends]).tolist())
    found = groups(comp)
    tiny = {f: (rs, ys) for f, (rs, ys) in found.items() if f not in good and f not in skip}
    tiny_labels = {int(L[rs[0], ys[0]]) for rs, ys in tiny.values()}
    stones = None
    updates = []
    for f, (rs, ys) in tiny.items():
        lab = L[rs[0], ys[0]]
        T = set(zip(rs.tolist(), ys.tolist()))
        first = min(T, key=lambda p: (p[1], p[0]))
        if first in history:
            if stones is None:
                stones = components(L >= 0, L)
            crs, cys = np.nonzero(stones == stones[first])
            if cys.min() >= 2 and cys.max() < rows - 2:
                T |= set(zip(crs.tolist(), cys.tolist()))
        history.add(first)
        for r, y in list(T):
            for dr, da in NB8:
                q = ((r + dr) % ring, y + da)
                if 0 <= q[1] < rows and G[q] and L[q] == lab:
                    T.add(q)
        count = Counter()
        for r, y in T:
            for dr, da in DIRS4:
                q = ((r + dr) % ring, y + da)
                if 0 <= q[1] < rows and q not in T and L[q] >= 0 and L[q] != lab:
                    count[int(L[q])] += 1
        calm = {k: n for k, n in count.items() if k not in tiny_labels}
        if not calm:
            calm = {k: n for k, n in count.items() if k > lab}
        if calm:
            updates.append((T, min(calm, key=lambda k: (-calm[k], k))))
    for T, tgt in updates:
        for p in T:
            L[p] = tgt
    return bool(updates)


def settle(L, width):
    """Make every block thick and every face at least `width` blocks across. Returns the groove mask."""
    rows = L.shape[1]
    frozen = np.zeros(L.shape, dtype=bool)
    frozen[:, :2] = True
    frozen[:, rows - 2:] = True
    history = set()
    for _ in range(40):
        thin_pass(L, frozen)
        G = groove_mask(L)
        if not merge_tiny(L, G, frozen, history, width):
            return G
    thin_pass(L, frozen)
    return groove_mask(L)


# ---------------------------------------------------------------- local rules


def local_ok(G, box, changed, hubs=()):
    """True when the blocks near `changed` hold no 2x2 groove, plus crossing or orphan wall,
    and each face next to a changed block holds a 2x2 wall square. A block in `hubs` may be a plus crossing.
    """
    ring, rows = G.shape

    def wall(r, y):
        if y < 0 or y >= rows:
            return True
        return not G[r % ring, y] and not box[r % ring, y]

    area = set()
    for r, y in changed:
        for dr in range(-2, 3):
            for da in range(-2, 3):
                if 0 <= y + da < rows:
                    area.add(((r + dr) % ring, y + da))
    for r, y in area:
        if G[r, y]:
            rr = (r + 1) % ring
            if y + 1 < rows and G[rr, y] and G[r, y + 1] and G[rr, y + 1]:
                return False
            if 0 < y < rows - 1 and G[rr, y] and G[r - 1, y] and G[r, y - 1] and G[r, y + 1] \
                    and (r, y) not in hubs:
                return False
        elif not box[r, y]:
            if not any(wall(r + dr, y + da) for dr, da in DIRS4):
                return False
    done = set()
    for r, y in changed:
        for dr, da in DIRS4:
            q = ((r + dr) % ring, y + da)
            if 0 <= q[1] < rows and q not in done and wall(*q):
                ok, seen = face_has_square(G, box, q)
                if not ok:
                    return False
                done |= seen
    return True


def face_has_square(G, box, start, limit=600):
    """Search the face of `start` for a 2x2 wall square. A face that reaches the window ends counts as good."""
    ring, rows = G.shape

    def wall(r, y):
        return 0 <= y < rows and not G[r % ring, y] and not box[r % ring, y]

    seen = {start}
    stack = [start]
    while stack:
        r, y = stack.pop()
        if y == 0 or y == rows - 1 or len(seen) > limit:
            return True, seen
        if wall(r + 1, y) and wall(r, y + 1) and wall(r + 1, y + 1):
            return True, seen
        for dr, da in DIRS4:
            q = ((r + dr) % ring, y + da)
            if q not in seen and wall(*q):
                seen.add(q)
                stack.append(q)
    return False, seen


FIRST_JOG = ((0, 1), (1, 0))


def plus_blocks(G):
    """Every groove block with groove neighbors on all four sides, as local (ring, axis)."""
    full = G & np.roll(G, 1, axis=0) & np.roll(G, -1, axis=0) & shift(G, 0, 1, False) & shift(G, 0, -1, False)
    return [(int(r), int(y)) for r, y in zip(*np.nonzero(full))]


def jog_pluses(P, G, box, axis0):
    """Split each plus crossing into two T junctions.

    One arm leaves its first block and detours by one block to the side, through
    two blocks next to the side arm. The crossings go in the sequence of a hash
    of their position, so no sweep across the band sets it. FIRST_JOG goes first:
    the lower arm detours to the side of the higher ring position. So on a regular
    grid each stone gives blocks to one detour only. The seeded random of the
    crossing position sets the sequence of the other seven variants. The first
    variant that passes the local rules stays. The crossings that wait for their
    own repair do not count against a variant. Returns the crossings that no
    variant fits, as local positions.
    """
    ring, rows = G.shape
    left = []
    pending = set(plus_blocks(G))
    seq_key = lambda p: (zlib.crc32(f"{P.seed}:{P.pillar}:{p[0]}:{p[1] + axis0}:plus".encode()), p[1], p[0])
    for r, y in sorted(plus_blocks(G), key=seq_key):
        pending.discard((r, y))
        if not all(G[(r + dr) % ring, y + da] for dr, da in DIRS4 + ((0, 0),)):
            continue
        variants = [(d, s) for d in DIRS4 for s in DIRS4 if d[0] * s[0] + d[1] * s[1] == 0 and (d, s) != FIRST_JOG]
        random.Random(f"{P.seed}:{P.pillar}:{r}:{y + axis0}:jog").shuffle(variants)
        variants.insert(0, FIRST_JOG)
        done = False
        for d, s in variants:
            a1 = ((r + d[0]) % ring, y + d[1])
            a2 = ((r + 2 * d[0]) % ring, y + 2 * d[1])
            b1 = ((r + s[0] + d[0]) % ring, y + s[1] + d[1])
            b2 = ((r + s[0] + 2 * d[0]) % ring, y + s[1] + 2 * d[1])
            if not all(0 <= p[1] < rows for p in (a2, b1, b2)):
                continue
            if not G[a2] or G[b1] or G[b2] or box[b1] or box[b2]:
                continue
            G[a1], G[b1], G[b2] = False, True, True
            if local_ok(G, box, [a1, b1, b2], pending):
                done = True
                break
            G[a1], G[b1], G[b2] = True, False, False
        if not done:
            left.append((r, y))
    return left


def merge_cell(L, p, rows):
    """Give the smallest stone at the 2x2 window of p the most frequent label along its border.

    A stone that touches the window ends stays. Returns True when a label changed.
    """
    ring = L.shape[0]
    comp = components(L >= 0, L)
    window = [((p[0] + i) % ring, p[1] + j) for i in (0, 1) for j in (0, 1)]
    ids = {int(comp[q]) for q in window if 0 <= q[1] < rows and comp[q] >= 0}
    found = groups(comp)
    best = None
    for cid in ids:
        rs, ys = found[cid]
        if ys.min() < 2 or ys.max() >= rows - 2:
            continue
        k = (len(rs), int(L[rs[0], ys[0]]))
        if best is None or k < best[0]:
            best = (k, cid)
    if best is None:
        return False
    rs, ys = found[best[1]]
    lab = best[0][1]
    count = Counter()
    for r, y in zip(rs.tolist(), ys.tolist()):
        for dr, da in DIRS4:
            q = ((r + dr) % ring, y + da)
            if 0 <= q[1] < rows and L[q] >= 0 and L[q] != lab:
                count[int(L[q])] += 1
    if not count:
        return False
    L[rs, ys] = min(count, key=lambda k: (-count[k], k))
    return True


# ---------------------------------------------------------------- delaunay


def line_steps(dr, da):
    """The 4-connected steps of the straight line from (0, 0) to (dr, da)."""
    nr, na = abs(dr), abs(da)
    sr, sa = (dr > 0) - (dr < 0), (da > 0) - (da < 0)
    out = []
    i = j = 0
    while i < nr or j < na:
        if j >= na or (i < nr and (2 * i + 1) * na < (2 * j + 1) * nr):
            out.append((sr, 0))
            i += 1
        else:
            out.append((0, sa))
            j += 1
    return out


def trim(G, blocks):
    """Cut each end of a line back to the last block that touches the grooves, so the line joins them in a T.

    A block touches when it is a groove or has a groove neighbor. A line that
    touches the grooves at every block keeps one block between its two ends.
    """
    ring, rows = G.shape

    def touches(p):
        if G[p]:
            return True
        return any(0 <= p[1] + d[1] < rows and G[(p[0] + d[0]) % ring, p[1] + d[1]] for d in DIRS4)

    flags = [touches(p) for p in blocks]
    s = 0
    while s + 1 < len(blocks) and flags[s] and flags[s + 1]:
        s += 1
    e = len(blocks) - 1
    while e - 1 > s and flags[e] and flags[e - 1]:
        e -= 1
    return blocks[s:e + 1]


def centroids(comp, ids):
    """The rounded surface center (ring, local axis) of each component, unwrapped around the ring."""
    ring = comp.shape[0]
    found = groups(comp)
    out = {}
    for cid in ids:
        rs, ys = found[cid]
        ang = rs * (2 * math.pi / ring)
        mid = math.atan2(np.sin(ang).sum(), np.cos(ang).sum()) * ring / (2 * math.pi)
        un = mid + ((rs - mid + ring / 2) % ring - ring / 2)
        out[cid] = (int(math.floor(un.mean() + 0.5)) % ring, int(math.floor(ys.mean() + 0.5)))
    return out


def ring_side(spacing):
    """The side of the center ring of a stone: 4 blocks below spacing 8, 5 from spacing 8, 6 at spacing 12."""
    return 4 + (spacing >= 8) + (spacing >= 12)


def center_ring(hub, side, ring):
    """(perimeter blocks, square blocks) of the center ring around hub. The square holds the ring and its inside."""
    x0 = hub[0] - (side - 1) // 2
    y0 = hub[1] - (side - 1) // 2
    square = {((x0 + i) % ring, y0 + j) for i in range(side) for j in range(side)}
    edge = [((x0 + i) % ring, y0 + j) for i in range(side) for j in range(side)
            if i in (0, side - 1) or j in (0, side - 1)]
    return edge, square


def delaunay(P, L, box, axis0):
    """The dual: a center ring in each stone, and one straight groove between the centers of two neighbor stones.

    Two stones share a border when 2 or more neighbor block pairs join them. Each
    ring goes in where its blocks are wall and the local rules hold. A line runs
    from center to center and loses the blocks inside the two ring squares, so it
    starts and ends on a ring. The lines go in the sequence of their length. A hash
    of the two centers sets the sequence of equal lengths. Each end of a line then
    stops where the line first touches the grooves. A line stays only when the
    local rules hold after it. A center without a ring may become a plus crossing,
    which the plus crossing repair splits.
    Returns the groove mask, the center blocks and the counts {"lines", "joined", "kept", "dropped"}.
    """
    ring, rows = L.shape
    comp = components(L >= 0, L)
    skip = set(np.unique(comp[:, [0, rows - 1]]).tolist())
    pairs = Counter()
    for other in (np.roll(comp, -1, axis=0), shift(comp, 0, 1, -1)):
        m = (comp >= 0) & (other >= 0) & (comp != other)
        a, b = np.minimum(comp[m], other[m]), np.maximum(comp[m], other[m])
        for x, y in zip(a.tolist(), b.tolist()):
            pairs[(x, y)] += 1
    ids = sorted({c for pr, n in pairs.items() if n >= 2 for c in pr} - skip)
    hub = centroids(comp, ids)
    G = np.zeros(L.shape, dtype=bool)
    side = ring_side(P.spacing)
    squares_of = {}
    for cid in sorted(ids, key=lambda c: zlib.crc32(f"{P.seed}:{P.pillar}:{hub[c][0]}:{hub[c][1] + axis0}".encode())):
        edge, square = center_ring(hub[cid], side, ring)
        if not all(1 <= y < rows - 1 and not G[r, y] and not box[r, y] for r, y in square):
            continue
        if any(G[(r + d[0]) % ring, y + d[1]] for r, y in square for d in DIRS4):
            continue
        for p in edge:
            G[p] = True
        if local_ok(G, box, edge):
            squares_of[cid] = square
        else:
            for p in edge:
                G[p] = False
    lines = []
    for (a, b), n in pairs.items():
        if n < 2 or a in skip or b in skip:
            continue
        (ha, ca), (hb, cb) = sorted(((hub[a], a), (hub[b], b)), key=lambda t: (t[0][1], t[0][0]))
        if ha == hb or box[ha] or box[hb]:
            continue
        dr, da = wrap(hb[0] - ha[0], ring), hb[1] - ha[1]
        blocks = [ha]
        r, y = ha
        for s in line_steps(dr, da):
            r, y = (r + s[0]) % ring, y + s[1]
            blocks.append((r, y))
        sa, sb = squares_of.get(ca, set()), squares_of.get(cb, set())
        lo, hi = 0, len(blocks)
        while lo < hi and blocks[lo] in sa:
            lo += 1
        while hi > lo and blocks[hi - 1] in sb:
            hi -= 1
        if hi - lo < 1 or any(p in sa or p in sb for p in blocks[lo:hi]):
            continue
        plain = {p for p, sq in ((ha, sa), (hb, sb)) if not sq}
        tag = f"{P.seed}:{P.pillar}:{ha[0]}:{ha[1] + axis0}:{hb[0]}:{hb[1] + axis0}"
        lines.append((dr * dr + da * da, zlib.crc32(tag.encode()), blocks[lo:hi], plain))
    lines.sort(key=lambda t: t[:2])
    dual = {"lines": len(lines), "joined": 0, "kept": 0, "dropped": 0}
    hubs = set()
    for _, _, full, plain in lines:
        blocks = trim(G, full)
        new = [p for p in dict.fromkeys(blocks) if not G[p] and not box[p]]
        if not new:
            dual["joined"] += 1
            continue
        for p in new:
            G[p] = True
        if local_ok(G, box, new, plain | hubs):
            dual["kept"] += 1
            hubs.update(plain)
        else:
            dual["dropped"] += 1
            for p in new:
                G[p] = False
    for r, y in jog_pluses(P, G, box, axis0):
        for d in DIRS4:
            a1 = ((r + d[0]) % ring, y + d[1])
            G[a1] = False
            if local_ok(G, box, [a1]):
                break
            G[a1] = True
    return G, sorted(hubs), dual


# ---------------------------------------------------------------- base band


def label_key(P):
    """The parameters that the labels and the grooves depend on."""
    return (P.cells, P.spacing, jitter_of(P), P.radius, P.seed, P.pillar, P.box, P.face, P.ring)


@lru_cache(maxsize=64)
def labels(key, w0, w1):
    """The settled labels, the grooves and the box of chunks [w0, w1)."""
    cells, spacing, jitter, radius, seed, pillar, box_at, face, ring = key
    P = Params(cells, spacing, jitter, radius, seed, pillar, False, False, 1.0, box_at, 0.0, face, ring, False)
    axis0, length = w0 * CHUNK, (w1 - w0) * CHUNK
    box = box_masks(P, axis0, length)
    L = nearest(P, axis0, length)
    L[box] = -1
    if radius > 0:
        L = round_corners(L, radius)
    for _ in range(8):
        G = settle(L, face)
        vgrooves = G.copy()
        if cells != "voronoi":
            break
        left = jog_pluses(P, G, box, axis0)
        if not left:
            break
        changed = False
        for p in left:
            changed |= merge_cell(L, p, length)
        if not changed:
            break
    hubs, dual = [], None
    if cells != "voronoi":
        G, hubs, dual = delaunay(P, L, box, axis0)
    return Labels(L, G, vgrooves, box, hubs, dual)


def margin(P):
    """The chunks of margin on each side of a window, so any window gives the same chunk."""
    return 2 * LOOK + 4 + math.ceil(3 * P.spacing / CHUNK) + math.ceil(P.radius / CHUNK)


def key_text(P):
    """The random stream prefix of a parameter set without its loop share."""
    text = (f"{P.seed}:{P.pillar}:{P.ring}:{P.cells}:{P.spacing}:{jitter_of(P)}:{P.radius}:"
            f"{P.surface}:{P.cut}:{P.box}:{P.chance}")
    return text + (f":{P.face}" if P.face != 2 else "")


@lru_cache(maxsize=64)
def base(P, w0, w1):
    """The band of chunks [w0, w1) with grooves, box and surface paths, before the loop rationing."""
    lab = labels(label_key(P), w0, w1)
    axis0, length = w0 * CHUNK, (w1 - w0) * CHUNK
    band = sp.Band(P.ring, axis0, length, ())
    band.kind[lab.grooves] = sp.GROOVE
    band.kind[lab.box] = sp.BOX
    pins = set()
    paths = []
    if P.surface or P.cut:
        m = margin(P)
        paths = choose_paths(P, band, (w0 + m - PATH_BELOW) * CHUNK, (w1 - m + PATH_ABOVE) * CHUNK)
        for path in paths:
            sp.place(band, path, P.cut)
            if not P.cut:
                pins.update(path.ends)
    vg = {(int(r), int(y) + axis0) for r, y in zip(*np.nonzero(lab.vgrooves))}
    hubs = tuple((r, y + axis0) for r, y in lab.hubs)
    return Base(band, frozenset(pins), frozenset(vg), hubs, tuple(paths))


# ---------------------------------------------------------------- surface paths


def unit(p, q, ring):
    """The unit step from p to q, with the ring wrapped."""
    return (wrap(q[0] - p[0], ring), q[1] - p[1])


class Grooves:
    """Degree and chain queries on the grooves of one band, before any surface path."""

    def __init__(self, band):
        self.band = band
        self.chains = {}

    def groove(self, p):
        return self.band.inside(p) and self.band.get(p) == sp.GROOVE

    def links(self, p):
        return [q for q in sp.neighbors(self.band, p)
                if self.band.get(q) == sp.GROOVE and sp.linked(self.band, p, q)]

    def chain(self, g):
        """(id, direction class, block count) of the chain through g, or None when g is no chain block.

        The direction class is "ring" or "axis" by the larger extent between the two chain ends.
        """
        if g in self.chains:
            return self.chains[g]
        nb = self.links(g)
        if len(nb) != 2:
            self.chains[g] = None
            return None
        blocks = [g]
        ends = []
        for first in nb:
            prev, cur = g, first
            while True:
                if cur == g:
                    break
                links = self.links(cur)
                if len(links) != 2:
                    break
                blocks.append(cur)
                prev, cur = cur, links[0] if links[1] == prev else links[1]
            ends.append(cur)
        e1, e2 = sorted(ends, key=sp.akey)
        dr, da = abs(wrap(e2[0] - e1[0], self.band.ring)), abs(e2[1] - e1[1])
        cls = "ring" if dr > da else ("axis" if da > dr else None)
        out = ((sp.akey(e1), sp.akey(e2), len(blocks)), cls, len(blocks))
        for b in blocks:
            self.chains[b] = out
        return out


END_RUN = 1


def end_ok(gr, g, n):
    """True when a path can end at groove g, arriving with step n.

    g sits inside a straight groove run of 2 * END_RUN + 1 blocks across n. No
    block of the run is a junction, so the path never meets a stone corner. The
    parity of g picks the one side a path may come from.
    """
    ring = gr.band.ring
    t = (1, 0) if n[0] == 0 else (0, 1)
    positive = n in ((1, 0), (0, 1))
    if positive != ((g[0] + g[1]) % 2 == 0):
        return False
    run = [((g[0] + k * t[0]) % ring, g[1] + k * t[1]) for k in range(-END_RUN, END_RUN + 1)]
    if not all(gr.groove(q) for q in run):
        return False
    for q in run[1:-1]:
        if len(gr.links(q)) != 2:
            return False
    if len(gr.links(run[0])) > 2 or len(gr.links(run[-1])) > 2:
        return False
    info = gr.chain(g)
    return info is not None and info[1] == ("ring" if t == (1, 0) else "axis")


def legs(path, ring):
    """The step sequence of a path as a list of (direction, block count)."""
    seq = [path.ends[0]] + list(path.blocks) + [path.ends[-1]]
    out = []
    for a, b in zip(seq, seq[1:]):
        d = unit(a, b, ring)
        if out and out[-1][0] == d:
            out[-1][1] += 1
        else:
            out.append([d, 1])
    return out


def path_ok(gr, band, path, edges):
    """The chaos rules of one candidate, before the module fit check."""
    if len(path.ends) != 2:
        return False
    g1, g2 = path.ends
    ring = band.ring
    if not end_ok(gr, g1, unit(path.blocks[0], g1, ring)) or not end_ok(gr, g2, unit(path.blocks[-1], g2, ring)):
        return False
    if path.turns == 2:
        d1 = legs(path, ring)[0][0]
        want = "axis" if d1[0] == 0 else "ring"
        used = {gr.chain(g1)[0], gr.chain(g2)[0]}
        if not any(cid not in used and cls == want and n >= 3 for cid, cls, n in edges):
            return False
    return True


def geometry_ok(band, path, region, narrow=False):
    """No wall strip under 2 blocks beside the path, and every part of the face keeps a 2x2 square.

    With `narrow`, a straight path may leave a strip of 1 block on each side and parts without a square.
    """
    ring = band.ring
    own = set(path.blocks)
    seq = set(path.ends) | own
    for p in path.blocks:
        for d in DIRS4:
            q = ((p[0] + d[0]) % ring, p[1] + d[1])
            if q in seq:
                continue
            if not band.inside(q) or band.get(q) != sp.WALL:
                return False
            q2 = ((p[0] + 2 * d[0]) % ring, p[1] + 2 * d[1])
            if q2 in own or (band.inside(q2) and band.get(q2) != sp.WALL and not narrow):
                return False
    if narrow:
        return True
    rest = region - own
    seen = set()
    for s in rest:
        if s in seen:
            continue
        part = {s}
        stack = [s]
        while stack:
            r, y = stack.pop()
            for d in DIRS4:
                q = ((r + d[0]) % ring, y + d[1])
                if q in rest and q not in part:
                    part.add(q)
                    stack.append(q)
        seen |= part
        if not any(((r + 1) % ring, y) in part and (r, y + 1) in part and ((r + 1) % ring, y + 1) in part
                   for r, y in part):
            return False
    return True


def choose_paths(P, band, lo, hi):
    """One surface path per face with the chance P.chance, each from the stream of its face.

    Only the faces with a block in the axis range [lo, hi) get a path. The other
    faces lie too far from the scene to change it.

    The candidates come from the four node lattices of the face: every second
    block around and along, at each of the four parity offsets.

    A face that holds no candidate under the strict rules and is 3 blocks across
    takes a straight surface path through its middle line, when the path is not cut.

    A face draws from the band before any surface path, so the faces do not depend
    on each other. The parity rule of the path ends keeps two paths off one groove block.
    """
    ring = band.ring
    gr = Grooves(band)
    wall = band.kind == sp.WALL
    comp = components(wall)
    rows = band.length
    skip = set(np.unique(comp[:, [0, rows - 1]]).tolist())
    found = groups(comp)
    faces = []
    for f, (rs, ys) in found.items():
        if f in skip or ys.max() + band.axis0 < lo or ys.min() + band.axis0 >= hi:
            continue
        region = {(int(r), int(y) + band.axis0) for r, y in zip(rs, ys)}
        faces.append((min(region, key=sp.akey), region))
    faces.sort(key=lambda t: sp.akey(t[0]))
    key = key_text(P)
    out = []
    for anchor, region in faces:
        rng = random.Random(f"{key}:{anchor[0]}:{anchor[1]}:path")
        if rng.random() >= P.chance:
            continue
        edges = set()
        for r, y in region:
            for d in DIRS4:
                q = ((r + d[0]) % ring, y + d[1])
                if gr.groove(q):
                    info = gr.chain(q)
                    if info is not None:
                        edges.add(info)
        classes = {0: [], 1: [], 2: []}
        seen = set()
        for pr, pa in ((0, 0), (1, 0), (0, 1), (1, 1)):
            nodes = {p for p in region if p[0] % 2 == pr and p[1] % 2 == pa}
            for c in sp.surface_candidates(band, region, nodes):
                k = (c.blocks, c.ends)
                if k not in seen and path_ok(gr, band, c, edges):
                    seen.add(k)
                    classes[c.turns].append(c)
        pick = None
        for narrow in ((False, True) if not P.cut else (False,)):
            turns = [k for k in (0, 1, 2) if classes[k] and (k == 0 or not narrow)]
            rng.shuffle(turns)
            for k in turns:
                pool = sorted(classes[k], key=lambda c: [sp.akey(b) for b in c.blocks + c.ends])
                rng.shuffle(pool)
                for c in pool:
                    if sp.fits(band, c) and geometry_ok(band, c, region, narrow):
                        pick = c
                        break
                if pick:
                    break
            if pick:
                break
        if pick:
            out.append(pick)
    return out


# ---------------------------------------------------------------- rationing


def chunk_graph(graph):
    """Split each groove graph edge where its chain enters another chunk, so each piece spans 2 chunks at most."""
    verts = set(graph.vertices)
    edges = {}

    def add(u, v, chain):
        if sp.akey(u) > sp.akey(v):
            u, v, chain = v, u, list(reversed(chain))
        e = sp.Edge(u, v, tuple(chain))
        edges[sp.edge_key(e)] = e

    for e in graph.edges:
        u = e.u
        chain = []
        for b in e.chain:
            if b[1] // CHUNK != u[1] // CHUNK:
                verts.add(b)
                add(u, b, chain)
                u, chain = b, []
            else:
                chain.append(b)
        add(u, e.v, chain)
    return sp.GrooveGraph(sorted(verts, key=sp.akey), [edges[k] for k in sorted(edges)])


LOOK = 2
PATH_BELOW = 4
PATH_ABOVE = 2 * LOOK + 4


def sample_segments(graph, segment_of, segments, rng_for, share, removable):
    """The loop share rationing of surfacepaths.sample_segments, for tree segments with several components.

    A connector sees each component of the tree segment below it as one virtual
    node, as in the module. It sees the tree segment above it as fewer nodes: two
    components join when the groove graph of the LOOK tree segments above links them.
    So no cycle runs down through one connector of a tree segment and back up
    through the other, while that cycle spans LOOK tree segments or fewer.
    Returns the removed blocks and the counts {"cycles", "kept", "unbreakable"}.
    """
    own = {}
    for i, e in enumerate(graph.edges):
        own.setdefault(sp.segment_owner(segment_of(e.u), segment_of(e.v)), []).append(i)
    verts_of = {}
    for v in graph.vertices:
        verts_of.setdefault(segment_of(v), []).append(v)

    def components_of(t, top):
        uf = sp.UnionFind()
        for u in range(t, top + 1):
            for v in verts_of.get(u, ()):
                uf.find(sp.akey(v))
            for i in own.get(u, ()):
                e = graph.edges[i]
                if t <= segment_of(e.u) <= top and t <= segment_of(e.v) <= top:
                    uf.union(sp.akey(e.u), sp.akey(e.v))
        return uf

    removed = []
    stats = {"cycles": 0, "kept": 0, "unbreakable": 0}
    for s in segments:
        rng = rng_for(s)
        if s % 2:
            below, above = components_of(s - 1, s - 1), components_of(s + 1, s + 1 + 2 * LOOK)
        else:
            below = above = None

        def key(v):
            t = segment_of(v)
            if t == s:
                return (0,) + sp.akey(v)
            if t == s - 1:
                return (1, 0) + below.find(sp.akey(v))
            return (1, 1) + above.find(sp.akey(v))

        idx = own.get(s, [])
        uf = sp.UnionFind()
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
        tree = sp.spanning_forest(nodes, pairs, rng)
        cotree = [free[j] for j in range(len(free)) if j not in tree]
        rng.shuffle(cotree)
        picks = [rng.choice(options[i]) for i in cotree]
        keep = int(share * len(cotree) + 0.5)
        stats["cycles"] += len(cotree)
        stats["kept"] += keep
        removed.extend(picks[keep:])
    return removed, stats


def junctions(band):
    """The groove blocks with 3 or more groove neighbors."""
    G = band.kind == sp.GROOVE
    deg = (G & np.roll(G, 1, axis=0)).astype(np.int8) + (G & np.roll(G, -1, axis=0))
    deg = deg + (G & shift(G, 0, 1, False)) + (G & shift(G, 0, -1, False))
    return {(int(r), int(y) + band.axis0) for r, y in zip(*np.nonzero(G & (deg >= 3)))}


def scene(P, lo, hi):
    """The band of chunks [lo, hi) after surface paths and loop rationing."""
    m = margin(P)
    b = base(P._replace(share=None), lo - m, hi + m)
    band = b.band.crop(b.band.axis0, b.band.length)
    removed, graph = [], None
    stats = {"cycles": 0, "kept": 0, "unbreakable": 0}
    if P.share < 1.0:
        graph = chunk_graph(sp.groove_graph(band))
        joints = junctions(band)

        def removable(e):
            free = [x for x in e.chain if x not in b.pins]
            calm = [x for x in free
                    if not any(q in joints or band.get(q) == sp.BOX for q in sp.neighbors(band, x))]
            return calm or free

        removed, stats = sample_segments(
            graph, lambda v: v[1] // CHUNK, range(lo - 2, hi + 2),
            lambda s: random.Random(f"{key_text(P)}:{s}:segment"), P.share, removable)
        for x in removed:
            band.set(x, sp.WALL)
    out = band.crop(lo * CHUNK, (hi - lo) * CHUNK)
    set_nodes(out, b.hubs)
    marks = [x for x in removed if out.inside(x)]
    return Scene(out, marks, graph, stats, b.vgrooves, frozenset(removed))


def set_nodes(band, hubs):
    """Groove nodes: the junction blocks and the plain dual centers. Face nodes: the surface center of each face."""
    band.groove_nodes = junctions(band) | {h for h in hubs if band.inside(h) and band.get(h) == sp.GROOVE}
    wall = band.kind == sp.WALL
    comp = components(wall)
    ids = [c for c in np.unique(comp).tolist() if c >= 0]
    band.face_nodes = set()
    for cid, (r, y) in centroids(comp, ids).items():
        if comp[r, y] == cid:
            band.face_nodes.add((r, y + band.axis0))


# ---------------------------------------------------------------- render


def jitter_tag(j):
    return f"{round(j * 100):03d}"


def suffix(P):
    """The file name suffix of one parameter set."""
    s = f"{P.cells}"
    if P.ring != PILLAR_RING:
        s += f"_ring{P.ring}"
    s += f"_s{P.spacing}_j{jitter_tag(P.jitter)}"
    if P.radius != DEFAULT_RADIUS:
        s += f"_r{P.radius}"
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
    if P.face != 2:
        s += f"_face{P.face}"
    return s


def label(P):
    """The label text of one render."""
    mode = "cut" if P.cut else ("surface" if P.surface else "plain")
    jit = jitter_of(P)
    jt = f"{P.jitter:g}" + (f" (cap {jit:g})" if jit != P.jitter else "")
    text = (f"{P.cells} {RINGS.get(P.ring, 'ring')} {P.ring} spacing {P.spacing} jitter {jt} radius {P.radius} "
            f"seed {P.seed} {mode} loops {round(P.share * 100)}%")
    text += f" box at {P.box}" if P.box is not None else ""
    return text + (f" merge width {P.face}" if P.face != 2 else "")


def shown(band):
    """A copy of the band for the renders. The pillar ring shows its four corner columns lighter."""
    out = sp.Band(band.ring, band.axis0, band.length, PILLAR_CORNERS if band.ring == PILLAR_RING else ())
    out.kind = band.kind
    out.groove_nodes = band.groove_nodes
    out.face_nodes = band.face_nodes
    return out


def render(P, chunks, scale, out_dir=OUT_DIR, overlay=False):
    """Render the band of chunks [0, chunks) and its channel image. Returns the readable image path."""
    sc = scene(P, 0, chunks)
    name = f"chaos_{suffix(P)}{'_overlay' if overlay else ''}.png"
    path = os.path.join(out_dir, name)
    marks = list(sc.removed)
    text = label(P)
    if overlay:
        marks += [x for x in sc.marks if sc.band.inside(x) and sc.band.get(x) == sp.WALL]
        text += " voronoi borders tinted"
    band = shown(sc.band)
    sp.write_band(band, path, scale, text, marks)
    sp.write_channels(band, os.path.join(out_dir, "channels", name))
    return path


# ---------------------------------------------------------------- checks


def raised(band):
    """True on the wall layer: wall blocks and surface path blocks."""
    return (band.kind == sp.WALL) | (band.kind == sp.SURFACE)


def orphans(band):
    """Every wall layer block without a wall layer neighbor. A neighbor outside the band counts as wall."""
    up = raised(band)
    ring = band.ring
    out = []
    for r, y in zip(*np.nonzero(up)):
        r, y = int(r), int(y)
        if not any(not (0 <= y + d[1] < band.length) or up[(r + d[0]) % ring, y + d[1]] for d in DIRS4):
            out.append((r, y + band.axis0))
    return out


def face_widths(band):
    """(faces under 2 wide, faces under 3 wide, faces) of the faces that do not touch the band ends.

    A face is a group of wall layer blocks. A surface path block lies on the wall
    layer, so it does not split a face. The width of a face is the side of the
    largest square of wall layer blocks inside it, up to 3.
    """
    up = raised(band)
    comp = components(up)
    skip = set(np.unique(comp[:, [0, band.length - 1]]).tolist())
    ids = set(np.unique(comp).tolist()) - skip - {-1}
    w2 = set(np.unique(comp[squares(up, 2)]).tolist())
    w3 = set(np.unique(comp[squares(up, 3)]).tolist())
    return len(ids - w2), len(ids - w3), len(ids)


def vertex_forest(graph, removed, lo, hi):
    """Cycles inside chunks [lo, hi) after rationing, and the component counts of the vertices there.

    The components count paths through the whole graph, before and after rationing,
    so a path that leaves the range still joins two vertices.
    """
    cut = set(removed)
    verts = [v for v in graph.vertices if lo <= v[1] // CHUNK < hi]

    def inside(e):
        return lo <= e.u[1] // CHUNK < hi and lo <= e.v[1] // CHUNK < hi

    full, kept, local = sp.UnionFind(), sp.UnionFind(), sp.UnionFind()
    for v in graph.vertices:
        full.find(v)
        kept.find(v)
    cycles = 0
    for e in graph.edges:
        full.union(e.u, e.v)
        if not any(x in cut for x in e.chain):
            kept.union(e.u, e.v)
            if inside(e) and not local.union(e.u, e.v):
                cycles += 1

    def count(uf):
        return len({uf.find(v) for v in verts})

    return cycles, count(full), count(kept)


def check_one(P, chunks):
    """Assert the rules on one parameter set. Returns a summary line."""
    tree_hi = chunks if chunks % 2 else chunks - 1
    sc = scene(P, 0, chunks)
    b = sc.band
    assert not sp.two_wide(b), (P, "2-wide", sp.two_wide(b)[:4])
    assert not sp.crossings(b), (P, "plus", sp.crossings(b)[:4])
    assert not orphans(b), (P, "orphan wall", orphans(b)[:4])
    under2, _, faces = face_widths(b)
    assert under2 == 0, (P, "face under 2 blocks", under2)
    assert sc.stats["unbreakable"] == 0, (P, "unbreakable", sc.stats)
    for c in range(chunks):
        alone = scene(P, c, c + 1).band
        part = b.kind[:, c * CHUNK:(c + 1) * CHUNK]
        assert np.array_equal(alone.kind, part), (P, "chunk differs", c)
    extra = ""
    if P.share == 0.0:
        cycles, full, kept = vertex_forest(sc.graph, sc.cut, 0, tree_hi)
        assert cycles == 0 and full == kept, (P, "vertex forest", cycles, full, kept)
        rank = sp.cycle_rank(b.crop(0, tree_hi * CHUNK))
        assert rank == 0, (P, "block cycles", rank)
        extra = f" trees {kept}"
    return f"{suffix(P)}: ok, {faces} faces{extra}"


def check_group(args):
    """Check one base parameter set at each loop share. Runs in a worker process."""
    P, shares, chunks = args
    return [check_one(P._replace(share=s), chunks) for s in shares]


def check_tasks(seeds):
    """The parameter sets of the check."""
    tasks = []
    for seed in seeds:
        base_p = Params("voronoi", 6, 0.5, DEFAULT_RADIUS, seed, 0, False, False, 1.0, None, 0.75)
        for spacing in (4, 6, 8, 12):
            for jitter in (0.0, 0.5, 1.0):
                tasks.append((base_p._replace(spacing=spacing, jitter=jitter), (1.0, 0.0)))
        for spacing in (6, 8, 12):
            tasks.append((base_p._replace(cells="delaunay", spacing=spacing), (1.0, 0.0)))
        for radius in (0, 2):
            tasks.append((base_p._replace(radius=radius), (1.0, 0.0)))
        for cells in CELLS:
            for spacing in (4, 6, 8):
                for surface, cut in ((True, False), (True, True)):
                    tasks.append((base_p._replace(cells=cells, spacing=spacing, surface=surface, cut=cut),
                                  (1.0, 0.5, 0.0)))
            for surface, cut in ((False, False), (True, False), (True, True)):
                tasks.append((base_p._replace(cells=cells, box=DEFAULT_BOX, surface=surface, cut=cut),
                              (1.0, 0.0)))
        for ring in (64, 28):
            for cells in CELLS:
                tasks.append((base_p._replace(cells=cells, ring=ring, surface=True), (1.0, 0.0)))
    return tasks


def check(chunks, seeds):
    """Run every check task on a process pool."""
    tasks = [(P, shares, chunks) for P, shares in check_tasks(seeds)]
    n = 0
    with ProcessPoolExecutor() as pool:
        for lines in pool.map(check_group, tasks):
            for line in lines:
                print(line)
                n += 1
    print(f"{n} scenes passed, each chunk also rendered alone")


# ---------------------------------------------------------------- guard


GUARD_JITTERS = tuple(round(0.05 * k, 2) for k in range(21))


def guard_one(args):
    """(spacing, jitter, stones under 3 wide, stones) over the seeds, without the jitter cap. Runs in a worker."""
    spacing, jitter, seeds, chunks = args
    bad = total = 0
    for seed in seeds:
        P = Params("voronoi", spacing, jitter, DEFAULT_RADIUS, seed, 0, False, False, 1.0, None, 0.75, 2,
                   PILLAR_RING, False)
        _, under3, faces = face_widths(scene(P, 0, chunks).band)
        bad += under3
        total += faces
    return spacing, jitter, bad, total


def min_spacing(chunks, seeds):
    """Print the stones under 3 blocks across per node spacing and jitter, the guard table and the jitter cap."""
    tasks = [(s, j, tuple(seeds), chunks) for s in SPACINGS for j in GUARD_JITTERS]
    with ProcessPoolExecutor() as pool:
        rows = {(s, j): (bad, total) for s, j, bad, total in pool.map(guard_one, tasks)}
    print("spacing  " + "  ".join(f"{j:4g}" for j in GUARD_JITTERS))
    for s in SPACINGS:
        print(f"{s:7d}  " + "  ".join(f"{rows[(s, j)][0]:4d}" for j in GUARD_JITTERS))
    print()
    print("jitter  smallest spacing with every stone at least 3 across, without the cap")
    for j in (0.0, 0.5, 1.0):
        fails = [s for s in SPACINGS if rows[(s, j)][0]]
        good = (max(fails) + 1) if fails else min(SPACINGS)
        print(f"{j:6g}  {good if good <= max(SPACINGS) else 'none'}")
    print()
    print("spacing  jitter cap: the largest jitter with no stone under 3 at it and below")
    for s in SPACINGS:
        cap = None
        for j in GUARD_JITTERS:
            if rows[(s, j)][0]:
                break
            cap = j
        print(f"{s:7d}  {cap if cap is not None else 'none'}")


def dual_one(args):
    """(spacing, summed dual counts) over the seeds. Runs in a worker."""
    spacing, seeds, chunks = args
    total = Counter()
    for seed in seeds:
        P = Params("delaunay", spacing, 0.5, DEFAULT_RADIUS, seed, 0, False, False, 1.0, None, 0.75)
        m = margin(P)
        lab = labels(label_key(P), -m, chunks + m)
        total.update(lab.dual)
    return spacing, total


def dual_stats(chunks, seeds):
    """Print the dual line counts at the node spacings 6, 8 and 12."""
    with ProcessPoolExecutor() as pool:
        rows = list(pool.map(dual_one, [(s, tuple(seeds), chunks) for s in (6, 8, 12)]))
    print("spacing  ring side  lines  joined  kept  dropped  dropped share")
    for s, t in rows:
        share = t["dropped"] / t["lines"] if t["lines"] else 0
        print(f"{s:7d}  {ring_side(s):9d}  {t['lines']:5d}  {t['joined']:6d}  {t['kept']:4d}  "
              f"{t['dropped']:7d}  {100 * share:12.1f}%")


# ---------------------------------------------------------------- main


def deliverables(chunks, scale, seed):
    """Render the image set of the README."""
    base_p = Params("voronoi", 6, 0.5, DEFAULT_RADIUS, seed, 0, False, False, 1.0, None, 0.75)
    jobs = []
    for spacing in (4, 6, 8, 12):
        for jitter in (0.0, 0.5, 1.0):
            jobs.append((base_p._replace(spacing=spacing, jitter=jitter), False))
    for spacing in (6, 8, 12):
        jobs.append((base_p._replace(cells="delaunay", spacing=spacing), False))
    jobs.append((base_p._replace(cells="delaunay", spacing=8), True))
    for radius in (0, 1, 2):
        if radius != DEFAULT_RADIUS:
            jobs.append((base_p._replace(radius=radius), False))
    for spacing in (6, 8):
        for surface, cut in ((True, False), (True, True)):
            jobs.append((base_p._replace(spacing=spacing, surface=surface, cut=cut), False))
    jobs.append((base_p._replace(spacing=4, surface=True), False))
    for share in (0.0, 0.5):
        jobs.append((base_p._replace(share=share), False))
    jobs.append((base_p._replace(box=DEFAULT_BOX), False))
    for ring in (64, 28):
        jobs.append((base_p._replace(ring=ring), False))
    with ProcessPoolExecutor() as pool:
        return list(pool.map(render_job, [(P, chunks, scale, ov) for P, ov in jobs]))


def render_job(args):
    P, chunks, scale, overlay = args
    return render(P, chunks, scale, overlay=overlay)


def share_value(text):
    v = int(text)
    if not 0 <= v <= 100 or v % 5:
        raise argparse.ArgumentTypeError("the loop share is a percent from 0 to 100 in steps of 5")
    return v / 100


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--cells", choices=CELLS, default="voronoi")
    ap.add_argument("--ring", type=int, choices=sorted(RINGS), default=PILLAR_RING,
                    help="ring length: 48 pillar, 64 I-beam, 28 bridge plate")
    ap.add_argument("--spacing", type=int, default=6, help="node spacing: the grid cell size in blocks, 4 to 12")
    ap.add_argument("--jitter", type=float, default=0.5, help="the offset range of each point, 0 to 1")
    ap.add_argument("--radius", type=int, default=DEFAULT_RADIUS, help="corner radius in blocks")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--pillar", type=int, default=0)
    ap.add_argument("--chunks", type=int, default=12)
    ap.add_argument("--surface", action="store_true", help="place surface paths")
    ap.add_argument("--cut", action="store_true", help="carve the surface paths as grooves")
    ap.add_argument("--loops", type=share_value, default=1.0, metavar="PERCENT",
                    help="loop share in percent, 0 to 100 in steps of 5")
    ap.add_argument("--no-loops", action="store_true", help="loops off: the loop share is 0")
    ap.add_argument("--box", type=int, nargs="?", const=DEFAULT_BOX, default=None, metavar="AXIS",
                    help="one junction box centered at this axis position, on the pillar ring only")
    ap.add_argument("--chance", type=float, default=0.75, help="chance that a face gets a surface path")
    ap.add_argument("--min-face", type=int, default=2, help="merge width: a face under this many blocks across merges")
    ap.add_argument("--overlay", action="store_true", help="tint the voronoi borders on the walls")
    ap.add_argument("--scale", type=int, default=5)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--check-seeds", type=int, default=3)
    ap.add_argument("--min-spacing", action="store_true", help="print the minimum feature guard tables")
    ap.add_argument("--dual-stats", action="store_true", help="print the dual line counts")
    ap.add_argument("--all", action="store_true", help="render the image set of the README")
    args = ap.parse_args()
    if not 0.0 <= args.jitter <= 1.0:
        ap.error("the jitter is a value from 0 to 1")
    if args.spacing not in SPACINGS:
        ap.error("the node spacing is a whole number from 4 to 12")
    if args.box is not None and args.ring != PILLAR_RING:
        ap.error("the junction box exists on the pillar ring only")
    os.makedirs(os.path.join(OUT_DIR, "channels"), exist_ok=True)
    seeds = range(1, args.check_seeds + 1)
    if args.check:
        check(args.chunks, seeds)
        return
    if args.min_spacing:
        min_spacing(args.chunks, seeds)
        return
    if args.dual_stats:
        dual_stats(args.chunks, seeds)
        return
    if args.all:
        for p in deliverables(args.chunks, args.scale, args.seed):
            print(p)
        return
    share = 0.0 if args.no_loops else args.loops
    P = Params(args.cells, args.spacing, args.jitter, args.radius, args.seed, args.pillar,
               args.surface or args.cut, args.cut, share, args.box, args.chance, args.min_face, args.ring)
    print(render(P, args.chunks, args.scale, overlay=args.overlay))


if __name__ == "__main__":
    main()
