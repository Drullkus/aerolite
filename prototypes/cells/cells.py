#!/usr/bin/env python3
"""The power rule island cells of Aerolite as a standalone prototype.

The lattice holds one jittered site per lattice box. A block takes the cell with the least power rule value.
README.md in this folder states every formula, the reach rule and the commands.

Commands:
  python3 cells.py --check    runs the checks and prints the lost cell table
  python3 cells.py --tables   prints the JSON tables and writes tables.json
"""

import argparse
import json
import math
import os
import random
import sys

MASK = 0xFFFFFFFF
SALT_SITE_X = 0x51
SALT_SITE_Y = 0x52
SALT_SITE_Z = 0x53
SALT_WEIGHT = 0xC3
SALT_FILL = 0xF1
OTHER_WEIGHT = 0.5


def mix32(h):
    """An integer finalizer. Each input bit reaches each output bit."""
    h &= MASK
    h ^= h >> 16
    h = (h * 0x7FEB352D) & MASK
    h ^= h >> 15
    h = (h * 0x846CA68B) & MASK
    h ^= h >> 16
    return h


def hash_cell(seed, ix, iy, iz, salt):
    """A seeded 32-bit value of one lattice index and one salt."""
    h = mix32((seed & MASK) ^ ((salt * 0x9E3779B9) & MASK))
    h = mix32(h ^ (ix & MASK))
    h = mix32(h ^ (iy & MASK))
    return mix32(h ^ (iz & MASK))


def unit(h):
    return h / 4294967296.0


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def norm(a):
    return math.sqrt(dot(a, a))


# ---- reach rule ------------------------------------------------------------------------------------

def winner_bound(jitter):
    """R / S: a winning site is at most this far from the block, in cell sizes."""
    a = 0.5 + jitter / 2.0
    return math.sqrt(3.0 * a * a + 1.0)


def search_offsets(jitter):
    """The box offsets whose site can win for a block of the center box."""
    a = 0.5 + jitter / 2.0
    bound = winner_bound(jitter) ** 2
    reach = int(math.floor(a + winner_bound(jitter)))
    return _offsets(reach, lambda n: max(0.0, abs(n) - a), bound), reach


def wall_offsets(jitter):
    """The box offsets from a site box whose site can hold a wall of that cell."""
    bound = (2.0 * winner_bound(jitter)) ** 2
    reach = int(math.floor(jitter + 2.0 * winner_bound(jitter)))
    return [o for o in _offsets(reach, lambda n: max(0.0, abs(n) - jitter), bound) if o != (0, 0, 0)], reach


def _offsets(reach, gap, bound):
    out = []
    for dx in range(-reach, reach + 1):
        for dy in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                if gap(dx) ** 2 + gap(dy) ** 2 + gap(dz) ** 2 <= bound + 1e-12:
                    out.append((dx, dy, dz))
    return out


def cube(reach):
    return [(dx, dy, dz) for dx in range(-reach, reach + 1) for dy in range(-reach, reach + 1) for dz in range(-reach, reach + 1)]


# ---- lattice -----------------------------------------------------------------------------------------

class Lattice:
    """The jittered lattice with the power rule. Positions in the public methods are in blocks."""

    def __init__(self, seed, cell_size, cell_height, layer_min_y, jitter, weight_min=0.5, weight_max=0.5,
                 fill_min=0.3, fill_max=0.9, weight_fn=None):
        self.seed = seed
        self.S = float(cell_size)
        self.H = float(cell_height)
        self.layer_min_y = layer_min_y
        self.jitter = jitter
        self.k = self.S / self.H
        self.weight_min = min(weight_min, weight_max)
        self.weight_max = max(weight_min, weight_max)
        self.fill_min = min(fill_min, fill_max)
        self.fill_max = max(fill_min, fill_max)
        self.weight_fn = weight_fn
        self.search, self.search_reach = search_offsets(jitter)
        self.walls, self.wall_reach = wall_offsets(jitter)
        self._sites = {}
        self._weights = {}

    # Scaled space: Y times S / H, so each lattice box is a cube of side S.
    def scale(self, p):
        return (p[0], p[1] * self.k, p[2])

    def unscale(self, q):
        return (q[0], q[1] / self.k, q[2])

    def site(self, c):
        """The scaled site of a cell. It does not depend on a cell weight."""
        s = self._sites.get(c)
        if s is None:
            ix, iy, iz = c
            j = self.jitter
            S = self.S
            ux = unit(hash_cell(self.seed, ix, iy, iz, SALT_SITE_X))
            uy = unit(hash_cell(self.seed, ix, iy, iz, SALT_SITE_Y))
            uz = unit(hash_cell(self.seed, ix, iy, iz, SALT_SITE_Z))
            s = ((ix + 0.5 + j * (ux - 0.5)) * S,
                 self.layer_min_y * self.k + (iy + 0.5 + j * (uy - 0.5)) * S,
                 (iz + 0.5 + j * (uz - 0.5)) * S)
            self._sites[c] = s
        return s

    def weight(self, c):
        """The cell weight: the roll in row 0, 0.5 in every other row."""
        w = self._weights.get(c)
        if w is None:
            if c[1] != 0:
                w = OTHER_WEIGHT
            elif self.weight_fn is not None:
                w = self.weight_fn(self, c)
            else:
                u = unit(hash_cell(self.seed, c[0], c[1], c[2], SALT_WEIGHT))
                w = self.weight_min + (self.weight_max - self.weight_min) * u
            self._weights[c] = w
        return w

    def fill(self, c):
        u = unit(hash_cell(self.seed, c[0], c[1], c[2], SALT_FILL))
        return self.fill_min + (self.fill_max - self.fill_min) * u

    def box_of_scaled(self, q):
        S = self.S
        return (math.floor(q[0] / S), math.floor((q[1] - self.layer_min_y * self.k) / S), math.floor(q[2] / S))

    def power(self, q, c):
        """The power rule value of the scaled position for the cell."""
        s = self.site(c)
        dx = q[0] - s[0]
        dy = q[1] - s[1]
        dz = q[2] - s[2]
        return dx * dx + dy * dy + dz * dz - self.weight(c) * self.S * self.S

    def owner_scaled(self, q, offsets=None):
        b = self.box_of_scaled(q)
        best = None
        best_v = math.inf
        for o in (self.search if offsets is None else offsets):
            c = (b[0] + o[0], b[1] + o[1], b[2] + o[2])
            v = self.power(q, c)
            if v < best_v:
                best_v = v
                best = c
        return best

    def owner(self, p):
        return self.owner_scaled(self.scale(p))

    def brute_owner(self, p, extra=2):
        return self.owner_scaled(self.scale(p), cube(self.search_reach + extra))

    def wall(self, c, other):
        """The wall of cell c toward the other cell: V, h and the block space normal N."""
        si = self.site(c)
        sj = self.site(other)
        V = sub(sj, si)
        h = dot(V, V) / 2.0 + (self.weight(c) - self.weight(other)) * self.S * self.S / 2.0
        N = (V[0], V[1] * self.k, V[2])
        return V, h, N

    def candidates(self, c, offsets=None):
        offs = self.walls if offsets is None else offsets
        return [(c[0] + o[0], c[1] + o[1], c[2] + o[2]) for o in offs if o != (0, 0, 0)]

    def lost(self, c, offsets=None):
        """A lost cell has h <= 0 toward one candidate, so its site lies outside it."""
        for other in self.candidates(c, offsets):
            if self.wall(c, other)[1] <= 0.0:
                return True
        return False

    def evaluate(self, c, p, offsets=None, detail=False):
        """The gauge, the wall distance and the cell radius of cell c at the block position p."""
        si = self.site(c)
        d = sub(self.scale(p), si)
        gauge = -math.inf
        wall_distance = math.inf
        radius = math.inf
        g_arg = w_arg = r_arg = None
        for other in self.candidates(c, offsets):
            V, h, N = self.wall(c, other)
            n = norm(N)
            x = dot(d, V)
            g = x / h
            wd = (h - x) / n
            r = h / n
            if g > gauge:
                gauge, g_arg = g, other
            if wd < wall_distance:
                wall_distance, w_arg = wd, other
            if r < radius:
                radius, r_arg = r, other
        if detail:
            return gauge, wall_distance, radius, (g_arg, w_arg, r_arg)
        return gauge, wall_distance, radius

    def island_body(self, p):
        """(fill - gauge) * cell radius in row 0, else None. No distortion."""
        c = self.owner(p)
        if c[1] != 0 or self.lost(c):
            return None
        gauge, _, radius = self.evaluate(c, p)
        return (self.fill(c) - gauge) * radius


# ---- the 0.5.0 lattice formulas, for check (c) ---------------------------------------------------

def reach_050(distance, size, slack):
    return max(1, math.ceil(distance / size + slack) - 1)


def nearest_050(lat, p):
    S, H, j = lat.S, lat.H, lat.jitter
    off = 0.5 + j / 2
    far = math.sqrt(2 * (off * S) ** 2 + (off * H) ** 2)
    rxz, ry = reach_050(far, S, off), reach_050(far, H, off)
    bx = math.floor(p[0] / S)
    by = math.floor((p[1] - lat.layer_min_y) / H)
    bz = math.floor(p[2] / S)
    best, best_d = None, math.inf
    for dx in range(-rxz, rxz + 1):
        for dy in range(-ry, ry + 1):
            for dz in range(-rxz, rxz + 1):
                c = (bx + dx, by + dy, bz + dz)
                s = lat.unscale(lat.site(c))
                dd = dot(sub(s, p), sub(s, p))
                if dd < best_d:
                    best, best_d = c, dd
    return best


def evaluate_050(lat, c, p):
    S, H, j = lat.S, lat.H, lat.jitter
    off = 0.5 + j / 2
    far = math.sqrt(2 * (off * S) ** 2 + (off * H) ** 2)
    rxz, ry = reach_050(2 * far, S, j), reach_050(2 * far, H, j)
    s = lat.unscale(lat.site(c))
    d = sub(p, s)
    gauge, spacing = -math.inf, math.inf
    for dx in range(-rxz, rxz + 1):
        for dy in range(-ry, ry + 1):
            for dz in range(-rxz, rxz + 1):
                if dx == dy == dz == 0:
                    continue
                t = lat.unscale(lat.site((c[0] + dx, c[1] + dy, c[2] + dz)))
                V = sub(t, s)
                sp = dot(V, V)
                gauge = max(gauge, 2.0 * dot(d, V) / sp)
                spacing = min(spacing, sp)
    return gauge, math.sqrt(spacing) / 2.0


# ---- checks --------------------------------------------------------------------------------------

RANGES = [(0.5, 0.5), (0.25, 0.75), (0.0, 1.0)]
JITTERS = [0.0, 0.5, 0.8, 1.0]
HEIGHTS = [128, 256]
SIZE = 256


def extreme_weight(lat, c):
    """A cell weight of exactly 0 or 1, the hardest case for the reach."""
    return float(hash_cell(lat.seed, c[0], c[1], c[2], SALT_WEIGHT) & 1)


def random_point(rng, lat, rows=(-1.5, 2.5), span=6):
    """A block position near row 0 inside a span of lattice boxes."""
    return (rng.uniform(0, span) * lat.S,
            lat.layer_min_y + rng.uniform(rows[0], rows[1]) * lat.H,
            rng.uniform(0, span) * lat.S)


class Report:
    def __init__(self):
        self.failures = 0

    def line(self, ok, text):
        print(("PASS " if ok else "FAIL ") + text)
        if not ok:
            self.failures += 1


def sweep_configs():
    for (lo, hi) in RANGES + [("extreme", "extreme")]:
        for j in JITTERS:
            for H in HEIGHTS:
                yield lo, hi, j, H


def make_lattice(seed, lo, hi, j, H):
    if lo == "extreme":
        return Lattice(seed, SIZE, H, 0, j, 0.0, 1.0, weight_fn=extreme_weight)
    return Lattice(seed, SIZE, H, 0, j, lo, hi)


def check_owner(rep, seed, points):
    """(a) The owner lookup over the search offsets equals a brute force lookup over the reach plus 2."""
    worst = 0
    total = 0
    for lo, hi, j, H in sweep_configs():
        lat = make_lattice(seed, lo, hi, j, H)
        rng = random.Random(seed * 7 + int(j * 100) + H)
        bad = 0
        for _ in range(points):
            p = random_point(rng, lat)
            q = lat.scale(p)
            a = lat.owner_scaled(q)
            b = lat.owner_scaled(q, cube(lat.search_reach + 2))
            if a != b and abs(lat.power(q, a) - lat.power(q, b)) > 1e-6:
                bad += 1
            b0 = lat.box_of_scaled(q)
            worst = max(worst, max(abs(b[i] - b0[i]) for i in range(3)))
            total += 1
        rep.line(bad == 0, f"(a) owner, weights {lo}-{hi}, jitter {j}, height {H}: {bad} of {points} differ "
                           f"(search reach {lat.search_reach}, {len(lat.search)} offsets)")
    print(f"     largest owner box offset seen: {worst} over {total} points")


def check_three_walls(rep, seed, triples):
    """(b) The three walls of a cell triple share one line."""
    worst = 0.0
    for lo, hi, j, H in sweep_configs():
        if lo == "extreme" and j == 0.0:
            continue
        lat = make_lattice(seed, lo, hi, j, H)
        rng = random.Random(seed * 13 + int(j * 100) + H)
        done = 0
        while done < triples:
            q = lat.scale(random_point(rng, lat))
            b = lat.box_of_scaled(q)
            ranked = sorted((lat.power(q, (b[0] + o[0], b[1] + o[1], b[2] + o[2])), (b[0] + o[0], b[1] + o[1], b[2] + o[2]))
                            for o in lat.search)
            ci, cj, ck = ranked[0][1], ranked[1][1], ranked[2][1]
            Vij, hij, _ = lat.wall(ci, cj)
            Vik, hik, _ = lat.wall(ci, ck)
            Vjk, hjk, _ = lat.wall(cj, ck)
            direction = cross(Vij, Vik)
            if norm(direction) < 1e-6 * norm(Vij) * norm(Vik):
                continue
            # A point on both walls of ci: the least norm solution in the span of the two normals.
            si = lat.site(ci)
            x0 = solve_two_planes(Vij, hij, Vik, hik)
            sj = lat.site(cj)
            for t in (0.0, 1.0):
                pt = tuple(si[i] + x0[i] + t * lat.S * direction[i] / norm(direction) for i in range(3))
                residual = abs(dot(sub(pt, sj), Vjk) - hjk) / norm(Vjk)
                pi, pj, pk = lat.power(pt, ci), lat.power(pt, cj), lat.power(pt, ck)
                spread = (max(pi, pj, pk) - min(pi, pj, pk)) / (lat.S * lat.S)
                worst = max(worst, residual, spread)
            done += 1
    rep.line(worst < 1e-7, f"(b) three walls meet in one line: largest residual {worst:.3e} (tolerance 1e-7, in blocks and S^2)")


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def solve_two_planes(n1, d1, n2, d2):
    """The point x = a n1 + b n2 with x . n1 = d1 and x . n2 = d2."""
    a11, a12, a22 = dot(n1, n1), dot(n1, n2), dot(n2, n2)
    det = a11 * a22 - a12 * a12
    a = (d1 * a22 - d2 * a12) / det
    b = (d2 * a11 - d1 * a12) / det
    return tuple(a * n1[i] + b * n2[i] for i in range(3))


def check_plain_voronoi(rep, seed, points):
    """(c) Every weight at 0.5 and the cell height equal to the cell size give the cells of 0.5.0."""
    for j in JITTERS:
        lat = Lattice(seed, SIZE, SIZE, 0, j, 0.5, 0.5)
        rng = random.Random(seed * 17 + int(j * 100))
        owner_bad = value_bad = 0
        worst = 0.0
        for _ in range(points):
            p = random_point(rng, lat)
            a = lat.owner(p)
            b = nearest_050(lat, p)
            if a != b:
                owner_bad += 1
                continue
            gauge, _, radius = lat.evaluate(a, p)
            gauge0, half0 = evaluate_050(lat, a, p)
            err = max(abs(gauge - gauge0), abs(radius - half0) / SIZE)
            worst = max(worst, err)
            if err > 1e-9:
                value_bad += 1
        rep.line(owner_bad == 0 and value_bad == 0,
                 f"(c) weights 0.5, height = size, jitter {j}: {owner_bad} owners and {value_bad} values of {points} differ "
                 f"from 0.5.0 (largest difference {worst:.2e})")


def check_reach(rep, seed, points):
    """(d) The derived reaches miss no cell and no wall: the values equal a wider search."""
    face_reach = {}
    for lo, hi, j, H in sweep_configs():
        lat = make_lattice(seed, lo, hi, j, H)
        rng = random.Random(seed * 19 + int(j * 100) + H)
        wide = [o for o in cube(lat.wall_reach + 1) if o != (0, 0, 0)]
        bad = 0
        tested = 0
        tries = 0
        stats = face_reach.setdefault(j, [0, 0, 0, 0, 0])
        while tested < points and tries < points * 20:
            tries += 1
            p = random_point(rng, lat, rows=(0.0, 1.0))
            c = lat.brute_owner(p)
            if c[1] != 0 or lat.lost(c, wide):
                continue
            tested += 1
            r1 = lat.evaluate(c, p, None, detail=True)
            r2 = lat.evaluate(c, p, wide)
            if any(abs(r1[i] - r2[i]) > 1e-9 * max(1.0, abs(r2[i])) for i in range(3)):
                bad += 1
            outer = False
            for arg in r1[3]:
                off = (arg[0] - c[0], arg[1] - c[1], arg[2] - c[2])
                stats[0] = max(stats[0], abs(off[0]), abs(off[2]))
                stats[1] = max(stats[1], abs(off[1]))
                stats[2] = max(stats[2], abs(off[0]) + abs(off[1]) + abs(off[2]))
                outer = outer or abs(off[1]) > 1 or abs(off[0]) > 2 or abs(off[2]) > 2
            stats[3] += 1 if outer else 0
            stats[4] += 1
        lost_bad = 0
        for ix in range(4):
            for iz in range(4):
                c = (ix, 0, iz)
                if lat.lost(c) != lat.lost(c, wide):
                    lost_bad += 1
        rep.line(bad == 0 and lost_bad == 0 and tested == points,
                 f"(d) reach, weights {lo}-{hi}, jitter {j}, height {H}: {bad} of {tested} values and {lost_bad} of 16 lost flags "
                 f"differ from reach + 1 (wall reach {lat.wall_reach}, {len(lat.walls)} offsets)")
    for j, s in sorted(face_reach.items()):
        print(f"     jitter {j}: the deciding walls reach {s[0]} boxes in X and Z, {s[1]} rows in Y, {s[2]} boxes in sum; "
              f"{s[3]} of {s[4]} points take a wall beyond 2 boxes in X or Z or beyond 1 row in Y")


def check_convex(rep, seed, cells_per_config):
    """(e) A cell that is not lost holds its site and is convex (sample test)."""
    for lo, hi, j, H in sweep_configs():
        lat = make_lattice(seed, lo, hi, j, H)
        rng = random.Random(seed * 23 + int(j * 100) + H)
        site_bad = convex_bad = lost_bad = 0
        tested = 0
        for n in range(cells_per_config):
            c = (n % 6, 0, n // 6)
            si = lat.site(c)
            p_site = lat.unscale(si)
            own = lat.brute_owner(p_site)
            lost = lat.lost(c)
            # The lost flag equals the site test: another cell ties or wins at the site.
            own_value = lat.power(si, c)
            site_outside = any(lat.power(si, (c[0] + o[0], c[1] + o[1], c[2] + o[2])) <= own_value
                               for o in cube(lat.search_reach + 2) if o != (0, 0, 0))
            if lost != site_outside:
                lost_bad += 1
            if lost:
                continue
            tested += 1
            if own != c:
                site_bad += 1
            inside = []
            R = winner_bound(j) * lat.S
            for _ in range(60):
                q = (si[0] + rng.uniform(-R, R), si[1] + rng.uniform(-R, R), si[2] + rng.uniform(-R, R))
                if lat.owner_scaled(q) == c:
                    inside.append(q)
            for a in range(0, len(inside) - 1, 2):
                qa, qb = inside[a], inside[a + 1]
                for t in (0.25, 0.5, 0.75):
                    qm = tuple(qa[i] + t * (qb[i] - qa[i]) for i in range(3))
                    if lat.owner_scaled(qm) != c:
                        convex_bad += 1
        rep.line(site_bad == 0 and convex_bad == 0 and lost_bad == 0,
                 f"(e) convex, weights {lo}-{hi}, jitter {j}, height {H}: {tested} cells, {site_bad} miss their site, "
                 f"{convex_bad} segment points leave the cell, {lost_bad} lost flags differ from the site test")


def check_block_space(rep, seed, points):
    """(f) The owner, the lost flag and the values equal a direct computation in block space."""
    S = float(SIZE)
    layer = -48
    for H in (32, 128, 1024):
        for j in (0.5, 1.0):
            for lo, hi in ((0.0, 1.0), ("extreme", "extreme")):
                if lo == "extreme":
                    lat = Lattice(seed, SIZE, H, layer, j, 0.0, 1.0, weight_fn=extreme_weight)
                else:
                    lat = Lattice(seed, SIZE, H, layer, j, lo, hi)
                k = S / H

                def site_b(c):
                    u = [unit(hash_cell(seed, c[0], c[1], c[2], salt)) for salt in (SALT_SITE_X, SALT_SITE_Y, SALT_SITE_Z)]
                    return ((c[0] + 0.5 + j * (u[0] - 0.5)) * S, layer + (c[1] + 0.5 + j * (u[1] - 0.5)) * H,
                            (c[2] + 0.5 + j * (u[2] - 0.5)) * S)

                def weight_b(c):
                    if c[1] != 0:
                        return 0.5
                    if lo == "extreme":
                        return float(hash_cell(seed, c[0], c[1], c[2], SALT_WEIGHT) & 1)
                    return lo + (hi - lo) * unit(hash_cell(seed, c[0], c[1], c[2], SALT_WEIGHT))

                def power_b(b, c):
                    s = site_b(c)
                    return (b[0] - s[0]) ** 2 + (k * (b[1] - s[1])) ** 2 + (b[2] - s[2]) ** 2 - weight_b(c) * S * S

                rng = random.Random(seed * 29 + int(j * 100) + H)
                owner_bad = lost_bad = value_bad = rows = 0
                for _ in range(points):
                    p = (rng.uniform(0, 6) * S, layer + rng.uniform(-0.5, 1.5) * H, rng.uniform(0, 6) * S)
                    box = (math.floor(p[0] / S), math.floor((p[1] - layer) / H), math.floor(p[2] / S))
                    ranked = sorted((power_b(p, (box[0] + o[0], box[1] + o[1], box[2] + o[2])), (box[0] + o[0], box[1] + o[1], box[2] + o[2]))
                                    for o in cube(lat.search_reach + 2))
                    c = ranked[0][1]
                    own = lat.owner(p)
                    if own != c and abs(power_b(p, own) - ranked[0][0]) > 1e-6 * S * S:
                        owner_bad += 1
                        continue
                    others = [(c[0] + o[0], c[1] + o[1], c[2] + o[2]) for o in cube(lat.wall_reach) if o != (0, 0, 0)]
                    sc = site_b(c)
                    lost = any(power_b(sc, o) <= power_b(sc, c) for o in others)
                    if lost != lat.lost(c):
                        lost_bad += 1
                    if lost:
                        continue
                    gauge, wall_distance, radius = -math.inf, math.inf, math.inf
                    for o in others:
                        # The power difference is linear in blocks, so unit steps give its exact gradient.
                        f0 = power_b(sc, c) - power_b(sc, o)
                        fp = power_b(p, c) - power_b(p, o)
                        grad = [power_b(tuple(sc[i] + (i == a) for i in range(3)), c)
                                - power_b(tuple(sc[i] + (i == a) for i in range(3)), o) - f0 for a in range(3)]
                        gn = math.sqrt(dot(grad, grad))
                        gauge = max(gauge, (fp - f0) / -f0)
                        wall_distance = min(wall_distance, -fp / gn)
                        radius = min(radius, -f0 / gn)
                    fill = lat.fill_min + (lat.fill_max - lat.fill_min) * unit(hash_cell(seed, c[0], c[1], c[2], SALT_FILL))
                    body = (fill - gauge) * radius if c[1] == 0 else None
                    rows += 1 if c[1] != 0 else 0
                    g2, w2, r2 = lat.evaluate(c, p)
                    b2 = lat.island_body(p)
                    err = max(abs(gauge - g2), abs(wall_distance - w2) / max(1.0, abs(w2)), abs(radius - r2) / max(1.0, r2))
                    if err > 1e-7 or (body is None) != (b2 is None) or (body is not None and abs(body - b2) > 1e-7 * max(1.0, abs(b2))):
                        value_bad += 1
                rep.line(owner_bad == 0 and lost_bad == 0 and value_bad == 0,
                         f"(f) block space, weights {lo}-{hi}, jitter {j}, height {H}, island bottom {layer}: {owner_bad} owners, "
                         f"{lost_bad} lost flags and {value_bad} values of {points} differ ({rows} points outside the row 0)")


def lost_table(seed, side):
    print()
    print(f"Lost cells of row 0, cell size {SIZE}, {side * side} cells per entry, seed {seed}:")
    print()
    header = "| Cell weight range | Jitter | " + " | ".join(f"Height {H}" for H in HEIGHTS) + " |"
    print(header)
    print("|---|---|" + "---|" * len(HEIGHTS))
    for lo, hi in RANGES + [("extreme", "extreme")]:
        for j in JITTERS:
            shares = []
            for H in HEIGHTS:
                lat = make_lattice(seed, lo, hi, j, H)
                lost = sum(1 for ix in range(side) for iz in range(side) if lat.lost((ix, 0, iz)))
                shares.append(f"{100.0 * lost / (side * side):.1f}%")
            label = "0 or 1" if lo == "extreme" else f"{lo} to {hi}"
            print(f"| {label} | {j} | " + " | ".join(shares) + " |")


def run_check(seed, quick):
    n = 60 if quick else 200
    rep = Report()
    print(f"Seed {seed}. Cell size {SIZE}. Weight ranges {RANGES} and 0 or 1. Jitter {JITTERS}. Heights {HEIGHTS}.")
    print()
    for j in JITTERS:
        offs, r = search_offsets(j)
        walls, rw = wall_offsets(j)
        print(f"jitter {j}: R/S {winner_bound(j):.4f}, search reach {r} ({len(offs)} offsets), wall reach {rw} ({len(walls)} offsets)")
    print()
    check_owner(rep, seed, n)
    check_three_walls(rep, seed, n // 4)
    check_plain_voronoi(rep, seed, n)
    check_reach(rep, seed, n // 2)
    check_convex(rep, seed, 12 if quick else 24)
    check_block_space(rep, seed, n // 4)
    lost_table(seed, 12 if quick else 32)
    print()
    print("FAILURES: %d" % rep.failures if rep.failures else "All checks passed.")
    return 1 if rep.failures else 0


# ---- tables ----------------------------------------------------------------------------------------

def grid(v, step):
    return round(v / step) * step


def table_case(name, S, H, sites, queries):
    """One case: explicit sites in blocks with weight, fill and row, and the expected values at each query point."""
    k = S / H
    sites = [dict(s, row=s.get("row", 0)) for s in sites]

    def scaled(p):
        return (p[0], p[1] * k, p[2])

    sc = [scaled((s["x"], s["y"], s["z"])) for s in sites]

    def wall(i, j):
        V = sub(sc[j], sc[i])
        h = dot(V, V) / 2.0 + (sites[i]["weight"] - sites[j]["weight"]) * S * S / 2.0
        return V, h, (V[0], V[1] * k, V[2])

    lost = [any(wall(i, j)[1] <= 0.0 for j in range(len(sites)) if j != i) for i in range(len(sites))]
    out = []
    for p in queries:
        q = scaled(p)
        power = [dot(sub(q, sc[i]), sub(q, sc[i])) - sites[i]["weight"] * S * S for i in range(len(sites))]
        owner = min(range(len(sites)), key=lambda i: power[i])
        walls = []
        gauge, wd, radius = -math.inf, math.inf, math.inf
        d = sub(q, sc[owner])
        for j in range(len(sites)):
            if j == owner:
                continue
            V, h, N = wall(owner, j)
            walls.append({"other": j, "h": h})
            if not lost[owner]:
                n = norm(N)
                gauge = max(gauge, dot(d, V) / h)
                wd = min(wd, (h - dot(d, V)) / n)
                radius = min(radius, h / n)
        entry = {"point": list(p), "owner": owner, "power": power, "walls": walls, "lost": lost[owner]}
        if lost[owner]:
            entry.update({"gauge": None, "wall_distance": None, "cell_radius": None, "island_body": None})
        else:
            # Only a cell of the row 0 holds an island.
            body = (sites[owner]["fill"] - gauge) * radius if sites[owner]["row"] == 0 else None
            entry.update({"gauge": gauge, "wall_distance": wd, "cell_radius": radius, "island_body": body})
        out.append(entry)
    return {"name": name, "cell_size": S, "cell_height": H, "sites": sites, "lost": lost, "queries": out}


def lattice_sites(seed, S, H, jitter, weight_of, span=1):
    """Explicit sites of a 3x3x3 block of the prototype lattice, snapped to 1/64 block."""
    lat = Lattice(seed, S, H, 0, jitter)
    sites = []
    for ix in range(-span, span + 1):
        for iy in range(-1, 2):
            for iz in range(-span, span + 1):
                s = lat.unscale(lat.site((ix, iy, iz)))
                w = weight_of(ix, iy, iz)
                f = grid(lat.fill_min + (lat.fill_max - lat.fill_min) * unit(hash_cell(seed, ix, iy, iz, SALT_FILL)), 1 / 64)
                sites.append({"x": grid(s[0], 1 / 64), "y": grid(s[1], 1 / 64), "z": grid(s[2], 1 / 64), "weight": w, "fill": f, "row": iy})
    return sites


def query_points(seed, S, H, count):
    rng = random.Random(seed)
    pts = [(0.5 * S, 0.5 * H, 0.5 * S)]
    while len(pts) < count:
        pts.append((rng.randint(-S // 2, 3 * S // 2) + 0.5, rng.randint(-H // 2, 3 * H // 2) + 0.5, rng.randint(-S // 2, 3 * S // 2) + 0.5))
    return pts


def build_tables(seed):
    cases = []
    half = lambda ix, iy, iz: 0.5

    def rolled(ix, iy, iz):
        if iy != 0:
            return 0.5
        return grid(unit(hash_cell(seed, ix, iy, iz, SALT_WEIGHT)), 1 / 64)

    def extreme(ix, iy, iz):
        if iy != 0:
            return 0.5
        return float(hash_cell(seed, ix, iy, iz, SALT_WEIGHT) & 1)

    cases.append(table_case("plain voronoi, weights 0.5, height = size", 256, 256,
                            lattice_sites(seed, 256, 256, 0.8, half), query_points(seed + 1, 256, 256, 12)))
    cases.append(table_case("rolled weights 0 to 1, height 128", 256, 128,
                            lattice_sites(seed, 256, 128, 0.8, rolled), query_points(seed + 2, 256, 128, 12)))
    cases.append(table_case("weights 0 or 1, height 128, jitter 1", 256, 128,
                            lattice_sites(seed + 5, 256, 128, 1.0, extreme), query_points(seed + 3, 256, 128, 12)))
    two = [{"x": 0.0, "y": 64.0, "z": 0.0, "weight": 0.0, "fill": 0.5},
           {"x": 256.0, "y": 64.0, "z": 0.0, "weight": 1.0, "fill": 0.5}]
    cases.append(table_case("weight 0 against weight 1 at the site distance S: the wall sits on the lighter site", 256, 256,
                            two, [(-10.5, 64.0, 0.0), (-0.5, 64.0, 0.0), (10.5, 64.0, 0.0), (300.0, 70.0, 5.0)]))
    pair = [{"x": 0.0, "y": 64.0, "z": 0.0, "weight": 0.5, "fill": 0.5},
            {"x": 128.0, "y": 64.0, "z": 0.0, "weight": 0.5, "fill": 0.75},
            {"x": 64.0, "y": 128.0, "z": 0.0, "weight": 0.5, "fill": 0.25}]
    cases.append(table_case("three sites, weights 0.5, height 128: the Y scale 2", 256, 128,
                            pair, [(10.5, 64.0, 0.0), (64.0, 90.0, 0.0), (100.0, 70.0, 3.0), (64.0, 127.0, 0.0)]))
    lost = [{"x": 0.0, "y": 0.0, "z": 0.0, "weight": 0.0, "fill": 0.5},
            {"x": 128.0, "y": 0.0, "z": 0.0, "weight": 1.0, "fill": 0.5},
            {"x": -256.0, "y": 0.0, "z": 0.0, "weight": 0.5, "fill": 0.5}]
    cases.append(table_case("a lost cell without space: weight 0 at 128 blocks from weight 1", 256, 256,
                            lost, [(0.0, 0.0, 0.0), (-80.0, 0.0, 0.0), (-160.0, 0.0, 0.0), (200.0, 0.0, 0.0)]))
    return {
        "description": "Power rule island cells. Positions in blocks. Y scale = cell_size / cell_height. "
                       "Every site of a case is a cell of the lattice. A wall entry is the wall of the owner toward the other site. "
                       "A lost owner has null gauge, wall distance, cell radius and island body. "
                       "An owner outside the row 0 has a null island body. "
                       "A value matches when |actual - expected| <= tolerance * max(1, |expected|).",
        "seed": seed,
        "tolerance": 1e-9,
        "cases": cases,
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="run the checks and print the lost cell table")
    ap.add_argument("--quick", action="store_true", help="fewer samples for --check")
    ap.add_argument("--tables", action="store_true", help="print the JSON tables and write tables.json")
    ap.add_argument("--seed", type=int, default=20261113)
    args = ap.parse_args()
    status = 0
    if args.check:
        status = run_check(args.seed, args.quick)
    if args.tables:
        tables = build_tables(args.seed)
        text = json.dumps(tables, indent=1)
        print(text)
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tables.json")
        with open(path, "w") as f:
            f.write(text + "\n")
    if not args.check and not args.tables:
        ap.print_help()
    return status


if __name__ == "__main__":
    sys.exit(main())
