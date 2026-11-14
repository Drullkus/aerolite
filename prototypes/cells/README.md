# Power rule cells prototype

This folder holds the prototype of the island cells with the power rule. It needs Python 3 and no packages.

- `cells.py` is the lattice of plan section 5.1 of `llm/plans/2026-11-13_island_variations.md`.
- `tables.json` holds the tables for the JUnit tests of the lattice. `cells.py --tables` writes it.
- `prototypes/weighted_cells.frag` draws the island body over the power rule cells.
- `prototypes/shadertoy/weighted_cells.shadertoy.glsl` is its port for one Shadertoy "Image" tab.
- The prototype has its own site hash. Its sites are not the sites of `VoronoiLattice`. No table depends on a site hash.

## Commands

| Task | Command |
|---|---|
| Run the checks and print the lost cell table | `python3 prototypes/cells/cells.py --check` |
| Run the checks with fewer samples | `python3 prototypes/cells/cells.py --check --quick` |
| Print the tables and write `tables.json` | `python3 prototypes/cells/cells.py --tables` |
| Use another seed | `--seed N`. The default is 20261113. |

- `--check` exits with status 1 when a check fails.
- The full `--check` takes about 60 seconds.

## Geometry

### Symbols

| Symbol | Meaning |
|---|---|
| `S` | The cell size in blocks. |
| `H` | The cell height in blocks. |
| `k` | The Y scale, `S / H`. |
| `j` | The jitter, 0 to 1. |
| `P` | The scaled position of a block: `(x, y * k, z)`. |
| `Si` | The scaled position of the site of cell i. |
| `wi` | The cell weight of cell i, 0 to 1. |
| `fi` | The rolled fill of cell i. |

### Lattice

- Each lattice box holds one site. A box is `S` wide along X and Z and `H` tall along Y.
- The row 0 starts at the island bottom.
- In scaled space, each box is a cube of side `S`.
- On each axis, the scaled site is the box center plus `(u - 0.5) * j * S`. A seeded hash gives `u` in [0, 1).
- The site of a cell does not depend on a cell weight.
- A cell of the row 0 rolls its cell weight uniform between the cell weight minimum and maximum.
- Every other cell has the cell weight 0.5.
- A cell of the row 0 rolls its fill uniform between the fill minimum and maximum.
- The cell weight roll and the fill roll have their own salts.

### Power rule

- The power rule value of a block for cell i is `p_i = |P - Si|^2 - wi * S^2`.
- The cell of a block is the cell with the least power rule value.
- For two cells i and j: `V = Sj - Si` and `h = |V|^2 / 2 + (wi - wj) * S^2 / 2`.
- The wall of cell i toward cell j is the plane `(P - Si) . V = h`.
- On that plane, `p_i = p_j`, because `p_i - p_j = 2 * ((P - Si) . V - h)`.
- So every wall is flat, and every cell is an intersection of half spaces. Every cell is convex.
- In block space, the normal of a wall is `N = (V.x, V.y * k, V.z)`.

### Lost cell

- A cell is lost when `h <= 0` toward one other cell.
- `h <= 0` toward cell j means `p_j <= p_i` at the site of cell i. So the site is not inside its own cell.
- A cell without space has no point at all. So its site is not inside it either, and the test also finds it.
- The prototype tests `h` against every candidate of the wall reach.
- At the site distance `S`, weight 1 against weight 0 gives `h = 0` on the lighter cell. The wall passes through its site, so that cell is lost.
- A lost cell holds no island.

### Values of a cell

The values below hold for a block of cell i when the cell is not lost.

| Value | Formula | Unit |
|---|---|---|
| Gauge | `g = max over walls of ((P - Si) . V) / h` | none |
| Wall distance | `min over walls of (h - (P - Si) . V) / |N|` | blocks |
| Cell radius | `R = min over walls of h / |N|` | blocks |
| Island body | `(fi - g) * R`, without distortion | blocks |

- The gauge is 0 at the site and 1 on the wall.
- The island is the set where the island body is positive: the polyhedron `(P - Si) . V <= fi * h` for every wall.
- The island is the cell scaled about its site by the fill.
- The prototype takes each maximum and minimum over every candidate of the wall reach, not only over the walls.
- The values are equal. Inside a cell that is not lost, a plane that is not a wall never decides them.
- Gauge: the ray from the site through the block leaves the cell through a wall. That wall has the least ray parameter, so it gives the largest gauge.
- Wall distance and cell radius: the foot of the nearest plane lies in the cell. So the nearest plane is a wall.

### Compare with 0.5.0

- With every cell weight at 0.5 and `H = S`, `k` is 1 and `h = |V|^2 / 2`.
- The gauge is then `2 * (P - Si) . V / |V|^2`. That is the gauge of `VoronoiLattice.evaluate`.
- The cell radius is then `min |V| / 2`. That is the half spacing of `VoronoiLattice.evaluate`.
- The island body is then `(fill - gauge) * halfSpacing`, the density of `VoronoiIslands` without distortion.
- With `H` other than `S`, equal weights give other cells than 0.5.0. See reading R26 of the plan.

## Reach rule

All distances of this section are in scaled space. `a = 0.5 + j / 2`.

### Search reach

1. A block lies in one box. The site of that box is at most `a * S` away on each axis. So the nearest site is at most `sqrt(3) * a * S` away.
2. Let m be the nearest site. The winner i has `|P - Si|^2 - wi * S^2 <= |P - Sm|^2 - wm * S^2`.
3. The weights lie in [0, 1]. So `|P - Si|^2 <= |P - Sm|^2 + S^2 <= (3 * a^2 + 1) * S^2`.
4. So a winner is at most `R_s = S * sqrt(3 * a^2 + 1)` away. This is the bound of plan section 5.1.
5. Along one axis, the site of the box at offset `n` is at least `max(0, |n| - a) * S` away.
6. A box offset `(nx, ny, nz)` holds no winner unless `sum of max(0, |n| - a)^2 <= 3 * a^2 + 1`.
7. The search reach is `floor(a + sqrt(3 * a^2 + 1))` boxes on each axis. Step 6 drops the far corners of that cube.

### Wall reach

1. Every point of a cell is owned by that cell. So by step 4, each point of the cell is at most `R_s` from its site.
2. A point of a wall belongs to both cells. So each site of a wall pair is at most `R_s` from that point.
3. So the two sites of a wall are at most `2 * R_s` apart.
4. Along one axis, two sites at box offset `n` are at least `max(0, |n| - j) * S` apart.
5. A box offset holds no wall unless `sum of max(0, |n| - j)^2 <= 4 * (3 * a^2 + 1)`.
6. The wall reach is `floor(j + 2 * sqrt(3 * a^2 + 1))` boxes on each axis. Step 5 drops the far corners of that cube.

### Reach table

| Jitter | `R_s / S` | Search reach | Search offsets | Wall reach | Wall offsets |
|---|---|---|---|---|---|
| 0 | 1.3229 | 1 | 27 | 2 | 80 |
| 0.5 | 1.6394 | 2 | 81 | 3 | 274 |
| 0.8 | 1.8520 | 2 | 117 | 4 | 484 |
| 1 | 2.0000 | 3 | 179 | 5 | 666 |

- The rule does not depend on the cell height, because the scaled boxes are cubes.
- The rule holds for every cell weight in [0, 1].
- In the sweep of check (d), the deciding walls reach at most 2 boxes in X and Z. They reach at most 2 rows in Y.
- So the derived wall reach is far wider than the walls that the sweep finds.

## Checks

`--check` runs six checks. Checks (a) to (e) run over a sweep. The sweep is every combination of these values:

- Cell weight ranges 0.5 to 0.5, 0.25 to 0.75, 0 to 1, and "0 or 1". "0 or 1" gives each cell of the row 0 the weight 0 or 1 at random.
- Jitter 0, 0.5, 0.8 and 1.
- Cell size 256 with cell height 128 and 256.

| Check | Rule | Result |
|---|---|---|
| (a) | The owner lookup over the search offsets equals a brute force lookup over the search reach plus 2. 200 points per sweep entry. | 32 PASS, 0 FAIL |
| (b) | The three walls of a cell triple share one line. Two points on the line of the walls i-j and i-k lie on the wall j-k. The three power rule values there are equal. | PASS, largest residual 3.0e-12 |
| (c) | With every cell weight at 0.5 and `H = S`, the owners, the gauge and the cell radius equal the 0.5.0 formulas. 200 points per jitter. | 4 PASS, 0 FAIL, largest difference 5.6e-17 |
| (d) | The gauge, the wall distance, the cell radius and the lost flag over the wall reach equal their values over the wall reach plus 1. 100 points per sweep entry. | 32 PASS, 0 FAIL |
| (e) | A cell that is not lost holds its site. Points of a segment between two points of the cell stay in the cell. The lost flag equals the site test. | 32 PASS, 0 FAIL |
| (f) | The owner, the lost flag and the four values of a cell equal a direct computation in blocks. 50 points per entry. | 12 PASS, 0 FAIL |

- Check (f) has its own sweep: cell heights 32, 128 and 1024, jitter 0.5 and 1, and the ranges 0 to 1 and "0 or 1".
- Check (f) puts the island bottom at Y -48. Its points span the rows -1, 0 and 1.
- Check (f) builds each site in blocks from the site hash of `cells.py`. It rolls the cell weight and the fill from their hashes.
- It takes each wall from the power rule value difference, which is linear in blocks. So it uses no formula for `N`.
- It expects no island body outside the row 0.
- Checks (a) to (e) work in scaled space with the island bottom at 0. So only check (f) tests the Y scale, `N` and the island body.

Output lines of the full run with the default seed:

```
     largest owner box offset seen: 1 over 6400 points
PASS (b) three walls meet in one line: largest residual 3.038e-12 (tolerance 1e-7, in blocks and S^2)
     jitter 0.0: the deciding walls reach 2 boxes in X and Z, 1 rows in Y, 3 boxes in sum; 0 of 800 points take a wall beyond 2 boxes in X or Z or beyond 1 row in Y
     jitter 0.5: the deciding walls reach 2 boxes in X and Z, 1 rows in Y, 3 boxes in sum; 0 of 800 points take a wall beyond 2 boxes in X or Z or beyond 1 row in Y
     jitter 0.8: the deciding walls reach 2 boxes in X and Z, 2 rows in Y, 4 boxes in sum; 2 of 800 points take a wall beyond 2 boxes in X or Z or beyond 1 row in Y
     jitter 1.0: the deciding walls reach 2 boxes in X and Z, 1 rows in Y, 4 boxes in sum; 0 of 800 points take a wall beyond 2 boxes in X or Z or beyond 1 row in Y
All checks passed.
```

## Lost cells

The share of lost cells of the row 0, cell size 256, 1024 cells per entry, seed 20261113:

| Cell weight range | Jitter | Height 128 | Height 256 |
|---|---|---|---|
| 0.5 to 0.5 | 0.0 | 0.0% | 0.0% |
| 0.5 to 0.5 | 0.5 | 0.0% | 0.0% |
| 0.5 to 0.5 | 0.8 | 0.0% | 0.0% |
| 0.5 to 0.5 | 1.0 | 0.0% | 0.0% |
| 0.25 to 0.75 | 0.0 | 0.0% | 0.0% |
| 0.25 to 0.75 | 0.5 | 0.6% | 0.6% |
| 0.25 to 0.75 | 0.8 | 3.5% | 3.5% |
| 0.25 to 0.75 | 1.0 | 6.0% | 6.0% |
| 0.0 to 1.0 | 0.0 | 0.0% | 0.0% |
| 0.0 to 1.0 | 0.5 | 8.1% | 8.1% |
| 0.0 to 1.0 | 0.8 | 14.6% | 14.6% |
| 0.0 to 1.0 | 1.0 | 18.5% | 18.5% |
| 0 or 1 | 0.0 | 47.5% | 47.5% |
| 0 or 1 | 0.5 | 33.3% | 33.3% |
| 0 or 1 | 0.8 | 35.8% | 35.8% |
| 0 or 1 | 1.0 | 38.0% | 38.0% |

- The two heights give the same share. In scaled space both lattices are the same cubes with the same sites.
- Equal cell weights give no lost cell.
- At jitter 0, the range 0 to 1 gives no lost cell. Only a weight difference of exactly 1 gives `h = 0` there.
- At jitter 0, "0 or 1" loses each weight 0 cell with a weight 1 neighbor.

## Tables

`tables.json` holds six cases. Each case is a closed set of explicit sites. Every site of a case is a cell.

| Key | Content |
|---|---|
| `tolerance` | A value matches when `|actual - expected| <= tolerance * max(1, |expected|)`. |
| `cases[].cell_size`, `cell_height` | `S` and `H` in blocks. |
| `cases[].sites[]` | `x`, `y`, `z` in blocks, `weight`, `fill` and `row`. Positions are multiples of 1/64. |
| `cases[].lost[]` | The lost flag of each site. |
| `cases[].queries[].point` | The query position in blocks. |
| `owner` | The index of the site with the least power rule value. No query is a tie. |
| `power[]` | The power rule value of the query for each site, in scaled square blocks. |
| `walls[]` | `other` and `h` of each wall of the owner toward each other site. |
| `lost` | The lost flag of the owner. |
| `gauge`, `wall_distance`, `cell_radius`, `island_body` | The values of the owner. Each is `null` for a lost owner. |

- The `row` of a lattice site is its lattice row. A site of cases 4 to 6 has the row 0.
- `island_body` is `null` for an owner outside the row 0. Only a cell of the row 0 holds an island.
- Cases 1 to 3 hold queries whose owner is outside the row 0.

The cases:

1. Plain Voronoi: 27 lattice sites, weights 0.5, `H = S = 256`, jitter 0.8.
2. Rolled weights 0 to 1 on the middle row, 0.5 on the other rows, `H = 128`, jitter 0.8.
3. Weights 0 or 1 on the middle row, `H = 128`, jitter 1. Five sites are lost.
4. Two sites at the distance `S`, weight 0 against weight 1. The lighter cell is lost, with space.
5. Three sites with weights 0.5 and `H = 128`, so the Y scale is 2.
6. A lost cell without space: weight 0 at 128 blocks from weight 1, with a third site behind it.

## Shader

- `prototypes/weighted_cells.frag` holds the shader between the lines `// SHADERTOY-BEGIN` and `// SHADERTOY-END`.
- The Shadertoy port is exactly that text.
- The defines at the top set the cell size, the cell height and the jitter. They also set the cell weight range and the fill range.
- `COLOR_BY_WEIGHT 1` colors each island by its rolled cell weight: blue at 0, gray at 0.5, orange at 1.
- `MARCH 0` steps from cell to cell. In each cell it clips the ray against the island polyhedron. The exit wall names the next cell.
- `MARCH 1` steps by the negative island body inside a cell of the row 0, capped at the cell exit.
- The negative island body is at most the distance to the island. So a `MARCH 1` step never passes the island.
- A lost cell and the rows -1 and 1 draw nothing.

### Limits of the view

- The shader holds three rows of sites, -1, 0 and 1. The mod lattice has every row.
- The shader takes the walls of a cell from the boxes within 2 in X and Z. The mod takes the wall reach.
- Check (d) found walls beyond that box set at 2 of 800 points, at jitter 0.8. Both came from the rows -2 or 2.
- The shader looks up the first cell among the 3x3x3 boxes. Check (a) found no owner beyond 1 box.
- The shader site hash is not the hash of `cells.py`. So the shader shows other sites than the checks.

### Validation

- This machine has no `glslangValidator`.
- A throwaway page in headless Chrome compiled the Shadertoy port as GLSL ES 3.00 in WebGL 2.
- Chrome ran with `--use-angle=metal`. The page reports the renderer only as "WebKit WebGL".
- The page wraps the port in the Shadertoy header: `iResolution`, `iTime`, `iMouse` and a `main` that calls `mainImage`.
- The page compiled and drew four variants: the defaults, `MARCH 1`, `COLOR_BY_WEIGHT 0`, and equal weights 0.5 with `H = 256`.
- Each variant compiled and linked with an empty log. Each draw at 640x360 kept the WebGL context.
- `MARCH 0` and `MARCH 1` differ in 208 of 230400 pixels by more than 24 in the RGB sum.
