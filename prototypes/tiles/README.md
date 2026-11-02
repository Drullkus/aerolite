# Tiles prototype

This folder holds the prototypes of the Tiles and Scales pillar patterns. It needs Python 3, numpy and Pillow.

- `tiles.py` is the Tiles prototype. It also holds the layout code, the surface path code and the checks that Scales uses.
- `scales.py` is the Scales prototype. It imports `tiles.py`.
- Both import `prototypes/patterns/surfacepaths.py` unchanged. See `prototypes/patterns/README.md` for the band, the module and the loop share.

## Groove layouts

A face column counts from 0 at the left corner to 12 at the right corner. A row counts from 0 at the start of a chunk.

| Size | Tile | Axis grooves | Ring grooves | Tile columns around the ring |
|---|---|---|---|---|
| `small` | 3x3 | Face columns 2, 6 and 10 | Rows 3, 7, 11 and 15 | 12 |
| `large` | 7x7 | Face columns 2 and 10 | Rows 7 and 15 | 8, with the 4 corner strips |

- Small: the tile around a corner is column 11, the corner and column 1. So every small tile is 3 wide.
- Large: the corner strip (column 11, the corner, column 1) is a 3-wide by 7-tall tile.
- An axis groove runs the full length of the pillar.
- A ring groove runs the full ring. It crosses the corners.
- Stagger none puts a plus crossing at each point where an axis groove meets a ring groove. The reference images show these crossings.

### Stagger

| Stagger | Sizes | Layout |
|---|---|---|
| `none` | Small and large | The grid above. |
| `x` | Small | Each course shifts by half a tile period. Even courses have axis grooves at face columns 3, 7 and 11. Odd courses have them at columns 1, 5 and 9. |
| `y` | Small and large | Each tile column shifts by half a tile height from its neighbors. |

- A course is one row of tiles between two ring grooves. A chunk holds 4 small courses or 2 large courses.
- Stagger x is a running bond of tiles. The corner lies inside a tile. So the ring grooves cross the corner, and no corner node exists.
- Stagger y has two phases. Phase 0 has the ring grooves of stagger none. Phase 1 has ring grooves at rows 1, 5, 9 and 13 for small, rows 3 and 11 for large.
- Stagger y alternates the phase from tile column to tile column around the ring.
- Small has 12 tile columns. So neighbor faces show different images.
- Large has 8 tile columns. So the corner strips take one phase and the 7-wide tiles take the other.
- The pillar random picks which tile columns take phase 0.
- With phase 0 on the corner strips, large y has tile grooves at rows 3 and 11 and corner strip grooves at rows 7 and 15.
- Stagger x and stagger y give T junctions only. They have no plus crossing.

### Encased

- `--encased` keeps the corner strip (column 11, the corner, column 1) solid along the whole pillar.
- No ring groove crosses an encased corner strip. The other grooves stay as they are.
- Encased y keeps the phase of each tile column of stagger y. So the tile columns on the two sides of a corner strip take the same phase.
- Encased excludes stagger x. `tiles.py` refuses the combination.
- The corner strip splits the grooves into one component per face. So the grooves of an encased pillar are four trees at 0%.

### Node sets

- Nodes sit on the odd rows.
- Stagger none and stagger y: nodes sit on the even face columns, 0 to 12.
- Stagger x and Scales: nodes sit on the odd face columns, 1 to 11.
- A node on a groove is a groove node. A node inside a tile is a face node.
- A small tile has one face node, its center.
- A large tile has a 3x3 grid of face nodes, at face columns 4, 6 and 8 and the tile rows 1, 3 and 5.
- A large corner strip has 3 corner nodes, on the corner at the tile rows 1, 3 and 5. A corner node has no axis link.
- An encased corner strip has a corner node on every odd row.
- A Scales tail block is a face node in the channel render. It is not a node of the surface paths.
- The node sets match `prototypes/reference/`.

## Scales

- Scales are small tiles with stagger x plus a tail.
- The tail is the block of the ring groove row next to the tile, in the tile center column, on the tail side.
- The tail stays wall. It caps the axis groove of the next course. So the scale reads as a shingle.
- `--tail down` points the tail toward +axis. `--tail up` points it toward -axis. `--tail seeded` lets the pillar random pick. The default is `seeded`.
- The tails cut every ring groove into pieces of 3 blocks. Each piece holds one axis groove of 3 blocks.
- So the grooves of Scales are a forest of small pieces. Cut paths join the pieces.
- The tail never carves, and the rationing never removes it.
- `--over-tails` lets a surface path run from the tile center over the tail into the axis groove beyond it.
- With `--cut`, that path carves the tail. So no orphan wall block remains. This is the only case where the tail goes.
- A path over a tail touches the two ring grooves beside the tail. The fit test of `tiles.py` allows these two contacts.
- So a path over a tail makes the tail a block with path on all four sides. The check exempts these tail blocks from the plus crossing test.
- `--over-tails` needs `--surface` or `--cut`. `scales.py` refuses it alone.

## Surface paths

- Each tile is one region. A path runs between the face nodes of one tile.
- Small tiles and Scales: a path runs straight through the center, or it turns 90 degrees at the center. A path can also end at the center.
- Large tiles: a path has at most two corners, with the rules of `surfacepaths.py`. A U has legs that do not touch.
- A tile gets a surface path with the chance `--chance`. The default is 0.75.
- No path is 2 wide. No path makes a new plus crossing. No path runs along a corner.
- With `--cut`, a path must not leave a wall block with grooves on all four sides. `--orphans` turns this rule off.
- In a 3x3 tile, a turn at the center leaves the inner corner block between two grooves and the path. So a cut 3x3 path never turns.
- With `--cut`, an encased corner strip gets no path, because no groove crosses it.
- Stagger x and Scales: the axis grooves above and below a tile center end in T junctions. So their paths run around the ring or end at the center.
- Stagger y: the ring rows of the tile center meet T junctions on both sides. So small y paths run along the axis or end at the center.

## Tiles per chunk

- A tile belongs to the chunk that holds its center row. The center row is the top row plus half the height, rounded down.
- An encased corner strip is cut into pieces of one period: 4 rows for small, 8 rows for large.
- The last tiles of a chunk have their center row in the last period of the chunk: rows 12 to 15 for small, 8 to 15 for large.
- The last tiles of a chunk draw first, from a stream that needs no other chunk.
- The other tiles of the chunk then fit around the last tiles of the chunk before it and of the chunk itself.
- No tile touches a tile of the same class in the next chunk. So each chunk is a pure function of the seed, the pillar and the chunk index.
- The scene builds 4 chunks of margin on each side, as in Bricks.

## Loop share

- The loop share follows `prototypes/patterns/README.md`. `--loops P` sets the share. `--no-loops` gives 0%.
- The rationing never removes a pinned block, a tail block or an attachment block.
- A pinned block is an end groove of a surface path that is not cut.
- An attachment block is a groove of the 2-core that holds a groove outside the 2-core. Its removal strands that groove.
- A groove graph edge whose end vertices lie more than one chunk apart gets a new vertex at each chunk boundary. Long edges occur in Scales with cut paths over tails.

## Junction box

- `--box AXIS` puts one 15x15x15 junction box on the pillar, centered at axis position AXIS. The default is 89.
- Faces 1 and 3 are the exposed faces. Columns 0, 1, 11 and 12 of an exposed face are bezel.
- The patch inside the bezel is 9x9. The pattern continues on the patch. The bezel and the box edges cut the grooves.

## Check

`tiles.py --check` runs every allowed layout. `scales.py --check` runs both tail directions with paths over tails on and off. Each runs every mode (plain, surface, cut), the loop shares 0%, 50% and 100% and both box options. It asserts:

- No 2x2 square of path blocks exists.
- No path block has path neighbors on all four sides, except the plus crossings of the groove layout and the tails under paths over tails.
- No path blocks touch along the axis on a corner.
- No cycle edge is unbreakable.
- With `--cut`, no wall block has grooves on all four sides.
- Without cut paths over tails, every tail block stays on the wall layer.
- Each chunk rendered alone equals its part of the whole band.
- At 0%, the kept vertex graph of chunks 0 to 10 has no cycle and the same components as the full graph.
- At 0%, the groove blocks of chunks 0 to 10 form a forest.
- Tiles without a box and without encased: at 0%, the vertex graph of chunks 0 to 10 is one tree.
- Tiles without a box: no groove component lies enclosed in the band.

`--check-seeds N` sets the seed count. The default is 2. The tiles check takes about 90 seconds per seed. The scales check takes about 11 seconds per seed.

## Commands

Run every command from the repository root.

Render each layout with surface paths off:

```bash
python3 prototypes/tiles/tiles.py --size small
python3 prototypes/tiles/tiles.py --size small --stagger x
python3 prototypes/tiles/tiles.py --size small --stagger y
python3 prototypes/tiles/tiles.py --size large
python3 prototypes/tiles/tiles.py --size large --stagger y
```

Render the encased layouts:

```bash
python3 prototypes/tiles/tiles.py --size small --encased
python3 prototypes/tiles/tiles.py --size small --stagger y --encased
python3 prototypes/tiles/tiles.py --size large --encased
python3 prototypes/tiles/tiles.py --size large --stagger y --encased
```

Render each size with surface paths and with cut paths:

```bash
python3 prototypes/tiles/tiles.py --size small --surface
python3 prototypes/tiles/tiles.py --size small --cut
python3 prototypes/tiles/tiles.py --size large --surface
python3 prototypes/tiles/tiles.py --size large --cut
```

Render small at the loop shares 0% and 50%:

```bash
python3 prototypes/tiles/tiles.py --size small --loops 0
python3 prototypes/tiles/tiles.py --size small --loops 50
```

Render the junction box scenes:

```bash
python3 prototypes/tiles/tiles.py --size small --box
python3 prototypes/tiles/tiles.py --size large --box
```

Render Scales with each tail option:

```bash
python3 prototypes/tiles/scales.py
python3 prototypes/tiles/scales.py --tail up
python3 prototypes/tiles/scales.py --tail down
```

Render Scales with surface paths and cut paths, with paths over tails off and on:

```bash
python3 prototypes/tiles/scales.py --surface
python3 prototypes/tiles/scales.py --cut
python3 prototypes/tiles/scales.py --surface --over-tails
python3 prototypes/tiles/scales.py --cut --over-tails
```

Render Scales with cut paths at the loop shares 0% and 50%, and the junction box scene:

```bash
python3 prototypes/tiles/scales.py --cut --loops 0
python3 prototypes/tiles/scales.py --cut --loops 50
python3 prototypes/tiles/scales.py --box
```

Render the whole image set of this README:

```bash
python3 prototypes/tiles/tiles.py --all
python3 prototypes/tiles/scales.py --all
```

Run the checks:

```bash
python3 prototypes/tiles/tiles.py --check
python3 prototypes/tiles/scales.py --check
```

These combinations stop with a message:

```bash
python3 prototypes/tiles/tiles.py --size large --stagger x
python3 prototypes/tiles/tiles.py --stagger x --encased
python3 prototypes/tiles/scales.py --over-tails
```

Other options of both programs:

- `--seed N` sets the seed. The default is 1.
- `--pillar N` sets the pillar index. The default is 0.
- `--chunks N` sets the chunk count. The default is 12.
- `--chance X` sets the chance of a surface path per tile. The default is 0.75.
- `--orphans` lets a cut path leave a wall block with grooves on all four sides.
- `--scale N` sets the pixels per block of the readable render. The default is 5.

## Output

The images are in `images/`. The render follows Bricks.

- The ring runs across with the four faces side by side. The axis runs down. Column 0 repeats at the right edge.
- Walls are raised. Grooves are dark. Surface paths are orange. Box blocks are blue.
- The lighter columns are the corners.
- A wall block with a red tint is a groove block that the rationing removed.
- The strip on the left alternates color per chunk.
- `tiles_<size>.png` is a tiles render. `_x` or `_y` names the stagger. `_encased` names encased.
- `scales.png` is a Scales render with the seeded tail. `scales_tailup.png` and `scales_taildown.png` set the tail.
- `_overtails` names paths over tails. `_surface` and `_cut` name the mode.
- A loop share other than 100% adds `_loops<P>`. `--box` adds `_box`. `--orphans` adds `_orphans`.
- A seed other than 1 adds `_seed<N>`. A pillar other than 0 adds `_pillar<N>`.
- `channels/` holds the reference color-channel render of each image, one pixel per block. The channels follow Bricks.
- The folder holds the image set of `--all` with seed 1 and 12 chunks.
