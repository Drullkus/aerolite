# Chaos prototype

This folder holds the Chaos prototype of the pillar patterns. It needs Python 3, numpy and Pillow.

- `chaos.py` is the Chaos prototype.
- It imports `prototypes/patterns/surfacepaths.py` unchanged. See `prototypes/patterns/README.md` for the band, the module and the render colors.
- Chaos cuts the outer layer into cells. The cells come from a jittered 3D point set.
- The cell type is Voronoi or Delaunay.

## Point set

- The world is cut into a 3D grid of cubes. The node spacing is the cube size in blocks.
- Each cube holds one point.
- On each axis, the point sits at the cube center plus (u - 0.5) x jitter x spacing. A hash of the world seed, the cube coordinates and the axis gives u in [0, 1).
- Jitter 0 gives a regular grid. Jitter 1 puts the point anywhere in its cube.
- So each point is a pure function of the seed and its cube. A chunk needs no other chunk.
- The prototype pillar runs along world Z. Its center line is at X 0 and Y 64. `--pillar N` moves it 101 blocks along X.
- Each outer layer block uses the world position of its center.

## Voronoi

- Each block takes the code of the nearest point in 3D. The code is the cell label.
- A point lies inside its own cube. So the nearest point is at most 2 cubes away, and the search covers 125 cubes.
- A distance tie takes the lower cube code.
- The distance is 3D. So the cells wrap around the pillar corners.

### Groove rule

- A block is a groove when its 2x2 window holds two labels. The window is the block, the next block around, and the two blocks one step along.
- So a border between two columns is a groove in the left column. A border between two rows is a groove in the upper row.
- Each border is one block wide. A diagonal border is a staircase that connects along the ring and the axis.
- A box block has no label and does not count. So the bezel makes no groove.

### Label rules

The labels settle before the groove rule. The settle repeats until no label changes.

- A corner column takes the labels of the next column around. So no border runs along a corner.
- A block is thick when it lies in a 2x2 square of one label.
- A thin block takes the most frequent other label of its 8 neighbors.
- A block that stays thin also gives its new label to the rest of the 2x2 square around it that holds the most of that label.
- Thick labels give no 2x2 groove square and no groove run along a corner.
- A face is a group of wall blocks that connect along the ring and the axis.
- A face without a square of 2x2 wall blocks takes the most frequent label next to it. The grooves of its own label around it take that label too.
- `--min-face N` sets that merge width. The default is 2. At 3, a face without a 3x3 wall square merges.
- A small face never takes the label of another small face, unless that label is higher. So two small faces never swap.
- A small face that comes back after a merge gives its whole cell the new label.
- The first 2 and the last 2 rows of a window keep their labels. The window margin hides them.

### Plus crossing

- Four cells that meet at one block corner make a plus crossing. Jitter 0 makes one at every grid corner.
- The repair takes one arm off the crossing for one block. The arm then runs around that block through the two blocks next to a side arm.
- So the crossing becomes two T junctions one block apart.
- Each crossing has 8 variants: 4 arms times 2 sides. The random of the crossing position sets their sequence.
- The first variant that passes the local rules stays.
- When no variant fits, the smallest cell at the crossing takes the label of its longest border. The labels then settle again.

The local rules hold around each change:

- No 2x2 groove square.
- No plus crossing.
- No groove run along a corner.
- No orphan wall block. An orphan has no wall neighbor.
- Each face next to the change keeps a 2x2 wall square.

## Delaunay

- The Delaunay cells are the settled Voronoi cells. The Voronoi grooves are not drawn.
- The surface center of a cell is its mean block position on the unwrapped band, rounded to a block.
- The ring position of the center is a circular mean. So a cell across ring 0 keeps its center.
- Two cells share a border when 2 or more neighbor block pairs join them.
- A straight 4-connected line joins the surface centers of two cells that share a border.
- The lines go from the shortest to the longest. A hash of the two centers sets the sequence of equal lengths.
- Each end of a line stops where the line first touches the grooves. So a line meets an earlier line in a T.
- A line stays only when the local rules hold after it. Only its two surface centers may become plus crossings.
- The plus crossing repair of Voronoi then splits each plus crossing at a center into two T junctions.
- A crossing that no variant fits loses the first arm block whose removal keeps the local rules.
- A line that crosses an earlier line is dropped. A line that runs too close beside an earlier line is dropped.
- So the dual is a subgraph of the Delaunay graph. The overlay image shows which lines drop.
- At jitter 0 the cells form a regular grid. So the dual is a regular grid with split crossings.

### Delaunay at the corners

- The unwrapped band is the unfolded pillar. A line is straight on the band.
- So on the pillar a line is straight on each face. It bends over the pillar edge like a line on an unfolded box.
- An axis step on a corner column moves to the next column, after the next ring step.
- At the end of a line, an axis step on a corner column moves before the last ring step instead.
- A line that runs along a corner drops.
- Many cells straddle a corner. Their centers sit next to the corner, so their lines run beside the corner.

## Corner radius

- `--radius R` sets the corner radius in blocks. 0 is off.
- Each block takes the most frequent label in the disk of radius R around it on the unwrapped band.
- The disk holds the blocks whose centers lie within R of the block center.
- A tie keeps the own label, else takes the lowest label. Box blocks do not vote.
- The filter runs once on the raw labels, before the labels settle.
- A straight border keeps its place, because each block sees more of its own side.
- At a junction, the cell with the largest angle holds most of the disk. It takes the tips of the sharper cells.
- So the sharper corners become arcs of about radius R.
- An arc of radius R cuts R x 0.15 blocks off a corner of 120 degrees, and R x 0.41 blocks off a corner of 90 degrees.
- So the radius shows mostly at T junctions. R 3 moves a 90 degree corner by about 1 block, and R 6 by about 2.5 blocks.
- Three equal corners and the four corners of a regular grid keep their shape, because no cell holds most of the disk.
- A cell smaller than about half the disk vanishes.
- In Delaunay, the radius moves the cells and so the surface centers. The lines stay straight.

## Surface paths

- `--surface` puts at most one surface path on each face. `--cut` carves the same paths as grooves.
- A face gets a path with the chance `--chance`. The default is 0.75. Each face draws from its own random, seeded by its first block.
- The candidates come from `surface_candidates` of the module, on the four node lattices of the face.
- A node lattice holds every second block around and along. The four lattices are its four parity offsets.
- A path enters through a groove and leaves through a groove. A path that ends at a node does not count.
- A path has zero, one or two turns.
- A path enters and leaves orthogonally. Its end groove sits in a straight run of 3 grooves across the path.
- No block of that run is a junction. So a path never meets a cell corner.
- The end groove belongs to a chain whose two ends lie more along the run than across it.
- A two-turn path runs its middle leg perpendicular to a third edge of the face. That edge is neither the entry edge nor the exit edge, and it holds 3 or more blocks.
- The module check `fits` holds: no 2-wide path, no plus crossing, no run along a corner.
- No wall strip under 2 blocks runs beside the path.
- Each part of the face beside the path keeps a 2x2 wall square. So no orphan wall block exists.
- The parity of the end groove picks the one side a path can come from. So two faces never end paths on one groove block.
- So each face draws from the band before any surface path, and the faces do not depend on each other.

## Loop share

- `--loops P` sets the loop share in percent, from 0 to 100 in steps of 5. The default is 100.
- `--no-loops` is loops off. It gives the behavior of 0%.
- The rationing follows `prototypes/patterns/README.md`, with the two changes below.
- A cut surface path is a groove. So it counts as a cycle edge.
- A surface path that is not cut pins the two groove blocks at its ends.
- A removed block is a chain block away from a junction and from the box, when one exists.

### Segments

- An edge of the groove graph splits where its chain enters another chunk. The split block becomes a vertex.
- So each edge spans 2 chunks at most, as the module needs.
- A chaos tree segment can hold several components. The module expects one.
- With the module rule, a cycle can run down through one connector of a tree segment and up through the other.
- So `chaos.py` has its own `sample_segments`.
- A connector sees each component of the tree segment below it as one virtual node, as in the module.
- A connector sees two components of the tree segment above it as one node when the full groove graph of the next 2 tree segments above joins them.
- So at 0%, no cycle exists that spans 2 tree segments above a connector or fewer.
- No longer cycle appears in the check.

## Junction box

- `--box AXIS` puts one 15x15x15 junction box on the pillar, centered at axis position AXIS. The default is 89.
- The box covers the same blocks as in Bricks. Faces 1 and 3 are the exposed faces. The patch inside the bezel is 9x9.
- A patch block samples the point set at its own position on the box face, one block out from the pillar surface.
- So the pattern paints the box face. The bezel and the box edges cut the grooves.
- The patch faces follow the label rules like every other face.

## Minimum feature guard

`--min-spacing` renders Voronoi with radius 0 at each node spacing from 3 to 40, over 12 chunks and 3 seeds. It counts the faces that are less than 3 blocks across.

- The width of a face is the side of the largest square of wall blocks inside it.
- A face that touches the band ends does not count.
- The label rules already merge each face under the merge width. So at merge width 2 the guard looks for faces of width 2.
- The guard runs at the merge widths 2 and 3.

| Jitter | Smallest spacing, merge width 2 | Smallest spacing, merge width 3 |
|---|---|---|
| 0 | 7 | 7 |
| 0.5 | 40 | 8 |
| 1 | None up to 40 | 7 |

- At jitter 0, the cells are a regular grid. A spacing of 7 gives 6x6 walls, less the plus crossing repair.
- At jitter above 0, a 3D cell that grazes the pillar surface leaves a thin face at any spacing.
- At merge width 2, one or two faces of width 2 remain up to spacing 40. So no spacing guards them.
- At merge width 3, those thin faces merge. Then every face is 3 blocks across from spacing 8.
- The merge costs cells. At spacing 8, jitter 0.5 merges about 26% of the raw cells at width 3 and 22% at width 2.
- Many raw cells only graze the surface. Those merge at both widths.
- The check passes at merge width 3 too.

## Check

`--check` renders these sets for seeds 1 to 3:

- Both cell types at the node spacings 6, 9 and 12, each at jitter 0, 0.5 and 1, at the loop shares 100% and 0%.
- Both cell types at spacing 12 with the radius 3 and 6, at 100% and 0%.
- Both cell types at spacing 9 and 12 with surface paths and with cut paths, at 100%, 50% and 0%.
- Both cell types at spacing 6 with the box, plain, with surface paths and with cut paths, at 100% and 0%.

It asserts:

- No 2x2 square of path blocks exists.
- No path block has path neighbors on all four sides.
- No path blocks touch along the axis on a corner.
- No wall block is an orphan.
- No face under 2 blocks exists. A face that touches the band ends does not count.
- No cycle edge is unbreakable.
- Each chunk rendered alone equals its part of the whole band.
- At 0%, the kept vertex graph of chunks 0 to 10 has no cycle.
- At 0%, the rationing joins the same vertices of chunks 0 to 10 as the full graph. A path can leave the range.
- At 0%, the groove blocks of chunks 0 to 10 form a forest.

The check runs on a process pool. It takes about 5 minutes on 12 cores.

## Commands

Run every command from the repository root.

Render Voronoi at the default node spacing 9 and jitter 0.5:

```bash
python3 prototypes/chaos/chaos.py
```

Render Delaunay:

```bash
python3 prototypes/chaos/chaos.py --cells delaunay
```

Render a node spacing and a jitter:

```bash
python3 prototypes/chaos/chaos.py --spacing 12 --jitter 1
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 6 --jitter 0
```

Render the corner radius:

```bash
python3 prototypes/chaos/chaos.py --spacing 12 --radius 3
python3 prototypes/chaos/chaos.py --spacing 12 --radius 6
```

Render surface paths and cut surface paths:

```bash
python3 prototypes/chaos/chaos.py --spacing 12 --surface
python3 prototypes/chaos/chaos.py --spacing 12 --cut
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 12 --surface
```

Render the loop shares 0% and 50%:

```bash
python3 prototypes/chaos/chaos.py --loops 0
python3 prototypes/chaos/chaos.py --loops 50
```

Render loops off. The result equals `--loops 0`:

```bash
python3 prototypes/chaos/chaos.py --no-loops
```

Render the junction box scenes:

```bash
python3 prototypes/chaos/chaos.py --spacing 6 --box
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 6 --box
```

Render Delaunay with the Voronoi borders tinted on the walls:

```bash
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 12 --overlay
```

Render the whole image set of this README:

```bash
python3 prototypes/chaos/chaos.py --all
```

Run the check:

```bash
python3 prototypes/chaos/chaos.py --check
```

Print the minimum feature guard table:

```bash
python3 prototypes/chaos/chaos.py --min-spacing
```

`--check-seeds N` sets the seed count of the check and of the guard. The default is 3.

Other options:

- `--seed N` sets the seed. The default is 1.
- `--pillar N` sets the pillar index. The default is 0.
- `--chunks N` sets the chunk count. The default is 12.
- `--chance X` sets the chance of a surface path per face.
- `--min-face N` sets the merge width of the label rules. The default is 2.
- `--scale N` sets the pixels per block of the readable render. The default is 5.

## Output

The images are in `images/`. The render is the readable render of the module, with the four faces unwrapped side by side.

- `chaos_<cells>_s<spacing>_j<jitter>.png` is one render. The jitter is in percent with three digits, so `j050` is jitter 0.5.
- A radius other than 0 adds `_r<R>`. `--surface` adds `_surface`. `--cut` adds `_cut`.
- A loop share other than 100% adds `_loops<P>`. `--box` adds `_box`. `--overlay` adds `_overlay`.
- A seed other than 1 adds `_seed<N>`. A pillar other than 0 adds `_pillar<N>`. A merge width other than 2 adds `_face<N>`.
- A wall block with a red tint is a groove block that the rationing removed. In an overlay, the red tint also marks the Voronoi borders.
- `channels/` holds the color-channel render of each image, one pixel per block. The channel values follow `prototypes/patterns/README.md`.
- In the green channel, the groove nodes are the junction blocks and the Delaunay centers. The face nodes are the surface centers of the faces.
- The folder holds the image set of `--all` with seed 1 and 12 chunks.

### Image set

| Image | Shows |
|---|---|
| `chaos_voronoi_s{6,9,12}_j{000,050,100}.png` | Voronoi at each node spacing and jitter |
| `chaos_delaunay_s{6,9,12}_j{000,050,100}.png` | Delaunay at each node spacing and jitter |
| `chaos_{voronoi,delaunay}_s12_j050_r{3,6}.png` | The corner radius at 3 and 6 |
| `chaos_{voronoi,delaunay}_s12_j050_surface.png` | Surface paths |
| `chaos_{voronoi,delaunay}_s12_j050_cut.png` | Cut surface paths |
| `chaos_{voronoi,delaunay}_s9_j050_loops{0,50}.png` | The loop shares 0% and 50% |
| `chaos_{voronoi,delaunay}_s6_j050_box.png` | The junction box |
| `chaos_delaunay_s12_j050_overlay.png` | Delaunay over the tinted Voronoi borders |
