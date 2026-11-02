# Patterns prototype

This folder holds the prototypes of the pillar patterns that are not mazes. It needs Python 3, numpy and Pillow.

- `surfacepaths.py` is the shared module. Bricks uses it now. Tiles, Scales and Chaos import it unchanged later.
- `bricks.py` is the Bricks prototype.

## Band

- A band is the unwrapped outer layer of one pillar. It is a grid of ring position by axis position.
- The ring is 48 blocks around and wraps. The axis does not wrap.
- The corners are the ring positions 0, 12, 24 and 36. A corner block never connects along the axis.
- Each block has one kind: wall, groove, surface path or box.
- A groove is a maze path below the wall layer.
- A surface path is a block on the wall layer that becomes the surface path block.
- A box block belongs to a junction box. It is not part of the pattern.
- A band holds two node sets: groove nodes and face nodes. A node on a corner is a corner node.

## Module

`surfacepaths.py` knows no brick geometry. Each function has a docstring that states its contract.

| Function | Use |
|---|---|
| `Band` | The grid of block kinds and the node sets. `crop` copies an axis range. |
| `neighbors`, `linked` | The 4-neighbors of a block. The corner rule for axis steps. |
| `two_wide` | Every 2x2 square of path blocks. |
| `crossings` | Every path block with path neighbors on all four sides. |
| `corner_runs` | Every pair of path blocks that touch along the axis on a corner. |
| `groove_core` | The 2-core of the grooves. Only the 2-core holds cycles. |
| `groove_graph` | Junction vertices and the block chains between them, sorted. |
| `cycle_rank` | The number of independent cycles of the groove blocks. 0 means a forest. |
| `spanning_forest` | Wilson's algorithm, one uniform tree per component. |
| `sample_segments` | The loop share rationing, segment by segment. |
| `lattice` | The straight links and the groove exits of each face node in one region. |
| `surface_candidates` | Every surface path of one region. |
| `fits` | The 2-wide, plus and corner checks of one surface path against the band. |
| `pick_path` | One random fitting surface path, or none. |
| `place` | Writes a surface path as surface path blocks, or as grooves when cut. |
| `channel_image`, `write_channels` | The reference color-channel render. |
| `band_image`, `write_band` | The readable render. |

## Surface paths

- A surface path runs between face nodes inside one region, a brick in Bricks.
- It starts at a groove. It ends at a groove or at a face node.
- It has at most two corners. So it is a straight cut, an L, an S or a U.
- A path block touches path blocks only at its own neighbors in the path and at its end grooves.
- So a U with adjacent legs fails, and no 2-wide path exists.
- No path block gets path neighbors on all four sides.
- No path runs along a corner. A path can cross a corner around the ring.
- A brick gets a surface path with the chance `--chance`. The default is 0.75.
- A brick picks a path that ends at a node or a path that ends at a groove with equal chance.
- `--surface` writes surface path blocks. `--cut` carves the same paths as grooves.
- A cut path is part of the groove graph. A cut path that joins two grooves closes a cycle.
- A surface path that is not cut pins the two groove blocks at its ends. The rationing never removes a pinned block.

## Loop share

- The groove graph of a pattern has cycles. Every brick is one.
- `--loops P` sets the loop share in percent, from 0 to 100 in steps of 5. The default is 100.
- `--no-loops` is loops off. It gives the behavior of 0%.
- The graph is the 2-core of the grooves. Dead-end branches hang as trees and always stay.
- A vertex is a groove block with 3 or more groove neighbors. An edge is the chain of blocks between two vertices.
- Each segment samples a uniform spanning forest with Wilson's algorithm.
- The other edges of the segment are its cycles. A shuffle by the segment random sets their sequence.
- The segment keeps round(P x count) of them. It removes one block from each other one.
- The removed block is a random chain block. A block that is not a groove node comes first.
- At 0%, the grooves form a tree. At 100%, every groove stays.
- The removal of one share is a subset of the removal of a lower share.

## Segments

- A segment is one chunk, 16 blocks along the axis.
- A vertex belongs to the chunk of its axis position.
- Even segments are tree segments. Odd segments are connector segments.
- A tree segment owns the edges between its own vertices.
- A connector segment also owns the edges to its two neighbor tree segments.
- A connector sees each component of a neighbor tree segment as one virtual node.
- So the union of the segments is one tree at 0%, as in the maze connector layout.
- Each segment is a pure function of the seed, the pillar and the chunk index.
- A chunk layout draws its joints and surface paths from its own streams.
- The last course of a chunk draws first, from a stream that needs no other chunk.
- The first course of a chunk then fits around the last course of the chunk before it.
- The scene builds 4 chunks of margin on each side. Then any window gives the same chunk.

## Bricks

| Size | Courses per chunk | Course height | Bed joints | Brick width |
|---|---|---|---|---|
| `small` | 4 | 3 | Rows 3, 7, 11, 15 | 5, and 8 around a corner |
| `jumbled` | 3 | 4, 4 and 5 in a random sequence | After each course | 3 to 11 |
| `large` | 2 | 7 | Rows 7 and 15 | 11 |

- Small: a phase 0 face has a head joint at column 6. A phase 1 face has head joints at columns 3 and 9.
- Small: the phase alternates per course and per face. So a corner brick spans 5 blocks, the corner and 2 blocks.
- Small: the pillar random picks which face pair starts on phase 0.
- Jumbled: each face has two head joints at even columns 2 to 10, at least 4 apart.
- Jumbled: no head joint shares its ring position with a head joint of the course above or below. So no groove block is a plus crossing.
- Jumbled: a face falls back to one head joint when no free pair exists.
- Large: course 0 has its head joint at column 2 of every face. Course 1 has it at column 10.
- Large: every face has the same columns. So a brick spans 9 blocks, the corner and 1 block of the next face, 11 blocks.

### Node sets

| Size | Groove nodes on a bed joint | Groove nodes on a head joint | Face nodes |
|---|---|---|---|
| `small` | Columns 0, 3, 6, 9 and 12 | The middle row of the course | The middle row, at the odd brick offsets below the last offset |
| `jumbled` | Every even column | Course rows 1 and 2 of a 4-high course, rows 1 and 3 of a 5-high course | Every even column of a brick on those rows |
| `large` | Every even column | Course rows 1, 3 and 5 | Every even column of a brick on those rows |

- A brick offset counts from 0 at the first block after the left head joint.
- So a 5-wide small brick has face nodes at offsets 1 and 3. An 8-wide corner brick has them at 1, 3 and 5.
- A small face node on a corner is a corner node.
- The groove and face nodes of jumbled and large match `prototypes/reference/`.

## Junction box

- `--box AXIS` puts one 15x15x15 junction box on the pillar, centered at axis position AXIS. The default is 89.
- The box covers the axis positions AXIS - 7 to AXIS + 7 on all four faces.
- Faces 1 and 3 are the exposed faces. Faces 0 and 2 hold the column.
- The band shows the 13 middle columns of each box face. Columns 0, 1, 11 and 12 of an exposed face are bezel.
- The bezel is 3 blocks thick. The patch inside it is 9x9, at face columns 2 to 10.
- The pattern continues on the patch. The bezel and the box edges cut the grooves.
- So the groove graph at 0% is a forest: the pillar above the box, the pillar below it and the patch parts.

## Check

`--check` renders every size, every mode (plain, surface, cut), the loop shares 0%, 50% and 100%, and both box options for seeds 1 to 3. It asserts:

- No 2x2 square of path blocks exists.
- No path block has path neighbors on all four sides.
- No path blocks touch along the axis on a corner.
- No cycle edge is unbreakable.
- Each chunk rendered alone equals its part of the whole band.
- At 0%, the kept vertex graph of chunks 0 to 10 has no cycle and the same components as the full graph.
- At 0%, the groove blocks of chunks 0 to 10 form a forest. Without a box, they form one tree.
- The tree checks end on an even chunk, so no connector of the range links to a chunk outside it.

## Commands

Run every command from the repository root.

Render each size with surface paths off:

```bash
python3 prototypes/patterns/bricks.py --size small
python3 prototypes/patterns/bricks.py --size jumbled
python3 prototypes/patterns/bricks.py --size large
```

Render each size with surface paths on:

```bash
python3 prototypes/patterns/bricks.py --size small --surface
python3 prototypes/patterns/bricks.py --size jumbled --surface
python3 prototypes/patterns/bricks.py --size large --surface
```

Render each size with cut surface paths:

```bash
python3 prototypes/patterns/bricks.py --size small --cut
python3 prototypes/patterns/bricks.py --size jumbled --cut
python3 prototypes/patterns/bricks.py --size large --cut
```

Render small at the loop shares 0%, 50% and 100%:

```bash
python3 prototypes/patterns/bricks.py --size small --loops 0
python3 prototypes/patterns/bricks.py --size small --loops 50
python3 prototypes/patterns/bricks.py --size small --loops 100
```

Render small with loops off. The result equals `--loops 0`:

```bash
python3 prototypes/patterns/bricks.py --size small --no-loops
```

Render the junction box scenes:

```bash
python3 prototypes/patterns/bricks.py --size small --box
python3 prototypes/patterns/bricks.py --size small --box --surface
python3 prototypes/patterns/bricks.py --size large --box
python3 prototypes/patterns/bricks.py --size large --box --surface
```

Render the whole image set of this README:

```bash
python3 prototypes/patterns/bricks.py --all
```

Run the check:

```bash
python3 prototypes/patterns/bricks.py --check
```

`--check-seeds N` sets the seed count of the check. The default is 3. The check takes about 90 seconds.

Other options:

- `--seed N` sets the seed. The default is 1.
- `--pillar N` sets the pillar index. The default is 0.
- `--chunks N` sets the chunk count. The default is 12.
- `--chance X` sets the chance of a surface path per brick.
- `--scale N` sets the pixels per block of the readable render. The default is 5.

## Output

The images are in `images/`.

- `bricks_<size>.png` is the readable render. The ring runs across with the four faces side by side. The axis runs down.
- Column 0 repeats at the right edge, so each face shows 13 columns like the reference images.
- Walls are raised. Grooves are dark. Surface paths are orange. Box blocks are blue.
- The lighter columns are the corners.
- A wall block with a red tint is a groove block that the rationing removed.
- The strip on the left alternates color per chunk.
- A label strip on top names the size, the seed, the mode, the loop share and the box.
- `--surface` adds `_surface`. `--cut` adds `_cut`. A loop share other than 100% adds `_loops<P>`. `--box` adds `_box`.
- A seed other than 1 adds `_seed<N>`. A pillar other than 0 adds `_pillar<N>`.
- `channels/` holds the reference color-channel render of each image, one pixel per block.
- Red: 0 groove, 128 surface path, 255 wall. Green: 255 node, 128 corner node, 0 no node.
- Blue: 0 corner column, 255 face interior, 128 box block. The reference images have no box value.
- The folder holds the image set of `--all` with seed 1 and 12 chunks.
