# Chaos prototype

This folder holds the Chaos prototype. It needs Python 3, numpy and Pillow.

- `chaos.py` is the Chaos prototype.
- It imports `prototypes/patterns/surfacepaths.py` unchanged. See `prototypes/patterns/README.md` for the band, the module and the render colors.
- Chaos reads like cobblestone: small, rounded, irregular stones of even size, with one-block grooves between them.
- The cell type is Voronoi or Delaunay.

## Rings

`--ring N` sets the ring length of the band.

| Ring | Shape |
|---|---|
| 48 | Pillar. The default. |
| 64 | I-beam |
| 28 | Bridge plate |

- The band wraps around the ring. The axis does not wrap.
- The corner columns of the pillar ring, 0, 12, 24 and 36, take no special role.
- A stone can span a corner. A groove can cross a corner, and a groove can run along a corner.
- The pillar renders show the four corner columns lighter. The other rings show no corners.

## Point set

- The points live on the unwrapped band, not in 3D.
- A 2D grid of cells covers the band, ring position by axis position.
- The cell count around the ring is the ring length over the node spacing, rounded half up.
- The ring cell width is the ring length over that count. So the grid wraps without a seam.
- The axis cell height is the node spacing.

| Ring | Spacing | Cells around | Ring cell width |
|---|---|---|---|
| 48 | 6 | 8 | 6 |
| 48 | 5 | 10 | 4.8 |
| 64 | 6 | 11 | 5.82 |
| 64 | 12 | 5 | 12.8 |
| 28 | 6 | 5 | 5.6 |
| 28 | 8 | 4 | 7 |

- Each cell holds one point.
- On each axis, the point sits at the cell center plus (u - 0.5) x jitter x cell size. A hash of the world seed, the pillar, the cell and the axis gives u in [0, 1).
- So each point is a pure function of the seed, the pillar and the cell. A chunk needs no other chunk.
- `--spacing` is the node spacing, from 4 to 12 blocks in steps of 1. The default is 6.
- `--jitter` is the offset range, from 0 to 1. The default is 0.5. Jitter 0 gives a regular grid.

### Jitter cap

The minimum feature guard sets a jitter cap per node spacing. A larger jitter takes the cap. The render label shows the cap.

| Spacing | Jitter cap |
|---|---|
| 4, 5 | None. No jitter keeps every stone 3 blocks across. |
| 6 | 0.25 |
| 7 | 0.6 |
| 8 | 0.7 |
| 9, 10 | 0.85 |
| 11, 12 | None |

- So at spacing 6, jitter 0.5 and jitter 1 give the same stones as jitter 0.25.

## Voronoi

- Each block takes the code of the nearest point on the band. The distance wraps around the ring. The code is the stone label.
- A point lies inside its own cell. So the nearest point is at most 2 cells away, and the search covers 25 cells.
- A distance tie takes the lower cell code.

### Groove rule

- A block is a groove when its 2x2 window holds two labels. The window is the block, the next block around, and the two blocks one step along.
- So a border between two columns is a groove in the left column. A border between two rows is a groove in the upper row.
- Each border is one block wide. A diagonal border is a staircase.
- A box block has no label and does not count. So the bezel makes no groove.

### Label rules

The labels settle before the groove rule. The settle repeats until no label changes.

- A block is thick when it lies in a 2x2 square of one label.
- A thin block takes the most frequent other label of its 8 neighbors.
- A block that stays thin also gives its new label to the rest of the 2x2 square around it that holds the most of that label.
- Thick labels give no 2x2 groove square.
- A face is a group of wall blocks that connect along the ring and the axis.
- A face without a square of 2x2 wall blocks takes the most frequent label next to it. The grooves of its own label around it take that label too.
- `--min-face N` sets that merge width. The default is 2.
- A small face never takes the label of another small face, unless that label is higher. So two small faces never swap.
- A small face that comes back after a merge gives its whole stone the new label.
- The first 2 and the last 2 rows of a window keep their labels. The window margin hides them.

### Plus crossing

- Four stones that meet at one block corner make a plus crossing. Jitter 0 makes one at every grid corner.
- The repair takes one arm off the crossing for one block. The arm then runs around that block through the two blocks next to a side arm.
- So the crossing becomes two T junctions one block apart.
- The first variant takes the lower arm and runs it on the side of the higher ring position. On a regular grid, each stone then gives blocks to one repair only.
- The other 7 variants follow in the sequence of the random of the crossing position.
- The crossings go in the sequence of a hash of their position. So no sweep across the band sets the result.
- The first variant that passes the local rules stays. A crossing that waits for its own repair does not count against a variant.
- When no variant fits, the smallest stone at the crossing takes the label of its longest border. The labels then settle again.

The local rules hold around each change:

- No 2x2 groove square.
- No plus crossing.
- No orphan wall block. An orphan has no wall neighbor.
- Each face next to the change keeps a 2x2 wall square.

## Corner radius

- `--radius R` sets the corner radius. 0 is off. The default is 1.
- The radius acts at each T junction of the raw labels.
- A T junction is a block corner whose 4 blocks hold 3 labels, with the repeated label on two neighbor blocks.
- The repeated label is the stone with the flat side. The two other blocks are the corners of their stones.
- The flat stone takes the corner triangle of each corner stone.
- The corner triangle holds the blocks of that stone at i blocks around and j blocks along from its corner block, with i + j < R.
- So R 1 takes 1 block from each corner, R 2 takes 3 blocks and R 3 takes 6 blocks.
- A block that two triangles claim takes the nearer junction, then the lower label.
- The flat stone grows a bump at each T junction. The two corner stones get a cut corner.
- A plus crossing has 4 labels, so the radius does not touch it. A regular grid keeps its square stones.
- R 1 rounds the stones at spacing 6. R 2 rounds the stones at spacing 12.
- R 2 at spacing 6 and R 3 merge stones and make the outlines uneven.
- In Delaunay, the radius moves the stones and so their centers.

The first version of this radius was a majority vote of the labels in a disk. On the 2D band, the vote keeps almost every corner of a jittered grid square. The corners of those stones are near 90 degrees, and no stone holds most of a disk at a T junction. So the vote does not round the stones, and the chamfer replaces it.

## Delaunay

- The Delaunay stones are the settled Voronoi stones. The Voronoi grooves are not drawn.
- The center of a stone is its mean block position on the unwrapped band, rounded to a block. The ring position is a circular mean.
- Two stones share a border when 2 or more neighbor block pairs join them.

### Center ring

- A square groove ring sits at the center of each stone. Its inside is wall.

| Spacing | Ring side | Inside |
|---|---|---|
| 4 to 7 | 4 | 2x2 |
| 8 to 11 | 5 | 3x3 |
| 12 | 6 | 4x4 |

- The rings go in, in the sequence of a hash of their centers.
- A ring goes in when its square and the blocks around the square hold no groove and no box, and the local rules hold.
- A stone without a ring keeps a plain center block.

### Lines

- A straight 4-connected line joins the centers of two stones that share a border.
- The line loses its blocks inside the two ring squares. So it starts and ends next to a ring.
- The lines go from the shortest to the longest. A hash of the two centers sets the sequence of equal lengths.
- Each end of a line stops where the line first touches the grooves. So a line meets a ring or an earlier line in a T.
- A line stays only when the local rules hold after it.
- A plain center can become a plus crossing. The plus crossing repair splits it after all lines.

`--dual-stats` prints the line counts for jitter 0.5, over 12 chunks and 3 seeds:

| Spacing | Ring side | Lines | Kept | Dropped |
|---|---|---|---|---|
| 6 | 4 | 4673 | 4511 | 162, 3.5% |
| 8 | 5 | 3164 | 3099 | 65, 2.1% |
| 12 | 6 | 1510 | 1507 | 3, 0.2% |

- Without rings and without the jitter cap, 28% of the lines drop at spacing 6, 20% at spacing 8 and 17% at spacing 12.
- Spacing 6 uses the jitter cap 0.25.

## Surface paths

- `--surface` puts at most one surface path on each face. `--cut` carves the same paths as grooves.
- A face gets a path with the chance `--chance`. The default is 0.75. Each face draws from its own random, seeded by its first block.
- The candidates come from `surface_candidates` of the module, on the four node lattices of the face.
- A node lattice holds every second block around and along. The four lattices are its four parity offsets.
- A path enters through a groove and leaves through a groove.
- A path has zero, one or two turns.
- A path enters and leaves orthogonally. Its end groove sits in a straight run of 3 grooves across the path.
- No block of that run is a junction. So a path never meets a stone corner.
- The end groove belongs to a chain whose two ends lie more along the run than across it.
- A two-turn path runs its middle leg perpendicular to a third edge of the face. That edge is neither the entry edge nor the exit edge, and it holds 3 or more blocks.
- The module check `fits` holds: no 2-wide path and no plus crossing.
- No wall strip under 2 blocks runs beside the path. Each part of the face beside the path keeps a 2x2 wall square.
- The parity of the end groove picks the one side a path can come from. So two faces never end paths on one groove block.
- So each face draws from the band before any surface path, and the faces do not depend on each other.

### Narrow stones

- At spacing 4, a stone is about 3 blocks across. No path leaves 2 blocks of wall on each side.
- A face with no candidate under the rules above can take a straight surface path. Each side of that path keeps a wall strip of 1 block.
- So a 3-wide stone takes a straight path through its middle line.
- A surface path block lies on the wall layer. So the stone stays one face, and the face width check counts it as wall.
- A cut path never takes this rule. A cut through a 3-wide stone leaves two faces 1 block wide.

## Loop share

- `--loops P` sets the loop share in percent, from 0 to 100 in steps of 5. The default is 100.
- `--no-loops` is loops off. It gives the behavior of 0%.
- The rationing follows `prototypes/patterns/README.md`, with the changes below.
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

## Junction box

- `--box AXIS` puts one 15x15x15 junction box on the pillar ring, centered at axis position AXIS. The default is 89.
- The box exists on the pillar ring only.
- The box covers the same blocks as in Bricks. Faces 1 and 3 are the exposed faces. The patch inside the bezel is 9x9.
- The pattern continues on the patch from the band. The bezel and the box edges cut the grooves.
- The patch faces follow the label rules like every other face.

## Minimum feature guard

`--min-spacing` renders Voronoi with the default radius on the pillar ring, over 12 chunks and 3 seeds. It does not apply the jitter cap. It counts the stones that are less than 3 blocks across, for each node spacing from 4 to 12 and each jitter from 0 to 1 in steps of 0.05.

- The width of a stone is the side of the largest square of wall layer blocks inside it.
- A stone that touches the band ends does not count.

| Jitter | Smallest spacing without the cap | Smallest spacing with the cap |
|---|---|---|
| 0 | 6 | 6 |
| 0.5 | 7 | 6 |
| 1 | 10 | 6 |

- At spacing 4 and 5, every jitter leaves stones under 3 blocks.
- At jitter 0 and spacing 4, every stone has 3x3 wall blocks. The plus crossing repair takes 2 blocks from one stone at each grid corner.
- A larger jitter makes some stones smaller than 3 blocks.
- At spacing 5, the ring cell width is 4.8. So stones alternate between 3 and 4 wall blocks, and the repair takes from the narrow ones.
- From spacing 6, a cap keeps every stone 3 blocks across. The cap is the largest jitter that passes at that jitter and at every lower one.

## Check

`--check` renders these sets for seeds 1 to 3:

- Voronoi at spacing 4, 6, 8 and 12, each at jitter 0, 0.5 and 1, at the loop shares 100% and 0%.
- Delaunay at spacing 6, 8 and 12, at 100% and 0%.
- Voronoi at spacing 6 with the radius 0 and 2, at 100% and 0%.
- Both cell types at spacing 4, 6 and 8 with surface paths and with cut paths, at 100%, 50% and 0%.
- Both cell types at spacing 6 with the box, plain, with surface paths and with cut paths, at 100% and 0%.
- Both cell types on the rings 64 and 28 with surface paths, at 100% and 0%.

It asserts:

- No 2x2 square of path blocks exists.
- No path block has path neighbors on all four sides.
- No wall layer block is an orphan.
- No face under 2 blocks exists. A face that touches the band ends does not count.
- No cycle edge is unbreakable.
- Each chunk rendered alone equals its part of the whole band.
- At 0%, the kept vertex graph of chunks 0 to 10 has no cycle.
- At 0%, the rationing joins the same vertices of chunks 0 to 10 as the full graph. A path can leave the range.
- At 0%, the groove blocks of chunks 0 to 10 form a forest.

The check runs on a process pool. The full check takes about 2 minutes on 12 cores.

## Commands

Run every command from the repository root.

Render Voronoi at the default node spacing 6, jitter 0.5 and radius 1:

```bash
python3 prototypes/chaos/chaos.py
```

Render a node spacing and a jitter:

```bash
python3 prototypes/chaos/chaos.py --spacing 4 --jitter 0
python3 prototypes/chaos/chaos.py --spacing 12 --jitter 1
```

Render the corner radius:

```bash
python3 prototypes/chaos/chaos.py --radius 0
python3 prototypes/chaos/chaos.py --radius 2
```

Render Delaunay with the center rings:

```bash
python3 prototypes/chaos/chaos.py --cells delaunay
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 8
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 12
```

Render Delaunay with the Voronoi borders tinted on the walls:

```bash
python3 prototypes/chaos/chaos.py --cells delaunay --spacing 8 --overlay
```

Render surface paths and cut surface paths:

```bash
python3 prototypes/chaos/chaos.py --surface
python3 prototypes/chaos/chaos.py --cut
python3 prototypes/chaos/chaos.py --spacing 8 --surface
python3 prototypes/chaos/chaos.py --spacing 8 --cut
python3 prototypes/chaos/chaos.py --spacing 4 --surface
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

Render the junction box scene:

```bash
python3 prototypes/chaos/chaos.py --box
```

Render the I-beam ring and the bridge plate ring:

```bash
python3 prototypes/chaos/chaos.py --ring 64
python3 prototypes/chaos/chaos.py --ring 28
```

Render the whole image set of this README:

```bash
python3 prototypes/chaos/chaos.py --all
```

Run the check:

```bash
python3 prototypes/chaos/chaos.py --check
python3 prototypes/chaos/chaos.py --check --check-seeds 1
```

Print the minimum feature guard tables and the dual line counts:

```bash
python3 prototypes/chaos/chaos.py --min-spacing
python3 prototypes/chaos/chaos.py --dual-stats
```

`--check-seeds N` sets the seed count of the check, the guard and the dual counts. The default is 3.

Other options:

- `--seed N` sets the seed. The default is 1.
- `--pillar N` sets the pillar index. The default is 0.
- `--chunks N` sets the chunk count. The default is 12.
- `--chance X` sets the chance of a surface path per face.
- `--min-face N` sets the merge width of the label rules. The default is 2.
- `--scale N` sets the pixels per block of the readable render. The default is 5.

## Output

The images are in `images/`. The render is the readable render of the module, with the ring unwrapped.

- `chaos_<cells>_s<spacing>_j<jitter>.png` is one render on the pillar ring. The jitter is in percent with three digits, so `j050` is jitter 0.5.
- A ring other than 48 adds `_ring<N>` after the cell type.
- A radius other than 1 adds `_r<R>`. `--surface` adds `_surface`. `--cut` adds `_cut`.
- A loop share other than 100% adds `_loops<P>`. `--box` adds `_box`. `--overlay` adds `_overlay`.
- A seed other than 1 adds `_seed<N>`. A pillar other than 0 adds `_pillar<N>`. A merge width other than 2 adds `_face<N>`.
- The label strip names the jitter cap when it applies.
- A wall block with a red tint is a groove block that the rationing removed. In an overlay, the red tint also marks the Voronoi borders.
- `channels/` holds the color-channel render of each image, one pixel per block. The channel values follow `prototypes/patterns/README.md`.
- In the green channel, the groove nodes are the junction blocks and the plain Delaunay centers. The face nodes are the surface centers of the faces.
- The folder holds the image set of `--all` with seed 1 and 12 chunks.

### Image set

| Image | Shows |
|---|---|
| `chaos_voronoi_s{4,6,8,12}_j{000,050,100}.png` | Voronoi at each node spacing and jitter |
| `chaos_delaunay_s{6,8,12}_j050.png` | Delaunay with the center rings |
| `chaos_delaunay_s8_j050_overlay.png` | Delaunay over the tinted Voronoi borders |
| `chaos_voronoi_s6_j050_r0.png`, `chaos_voronoi_s6_j050.png`, `chaos_voronoi_s6_j050_r2.png` | The corner radius 0, 1 and 2 |
| `chaos_voronoi_s{6,8}_j050_surface.png` | Surface paths |
| `chaos_voronoi_s{6,8}_j050_cut.png` | Cut surface paths |
| `chaos_voronoi_s4_j050_surface.png` | Straight surface paths through narrow stones |
| `chaos_voronoi_s6_j050_loops{0,50}.png` | The loop shares 0% and 50% |
| `chaos_voronoi_s6_j050_box.png` | The junction box |
| `chaos_voronoi_ring64_s6_j050.png` | The I-beam ring |
| `chaos_voronoi_ring28_s6_j050.png` | The bridge plate ring |
