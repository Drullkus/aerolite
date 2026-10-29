# Maze prototype

This folder holds the standalone maze prototype for the shaft surface. It needs Python 3 and no packages.

## Model

- The shaft surface is a cylinder. It is 48 blocks around and infinite along the shaft axis.
- A ruling is one ring of nodes at an odd block along the axis. One chunk holds 8 rulings.
- Around the ring, nodes sit on even blocks. The four corners are nodes. Each face has 7 nodes from corner to corner, 5 of them interior.
- A corner node has edges around the ring only. It has no edge along the axis, so no path runs along a corner.
- A corner node carves only when it passes a path from one face to the other. A corner leaf and its edge stay wall. So a two-block margin runs along every corner, with a notch wherever a path crosses it.
- Every block between two nodes is an edge. An edge in the tree is a maze path. Every other block is a maze wall.
- The program cuts the cylinder into segments. Each segment is a pure function of the seed and its index.
- Origin Shift samples one spanning tree per segment. The start tree is the breadth-first tree from a seeded root.
- Wilson's algorithm is the reference sampler. Both converge to the uniform spanning tree.
- A virtual node touches the interior nodes of the facing ruling, never its corners.

## Layouts

| Layout | Segments | Boundary |
|---|---|---|
| `oneroot` | Every segment is a tree. | One crossing per boundary. The boundary is a wall ring with one gap. |
| `forest` | Every segment is a forest. Each component has one forward crossing. | Many crossings. The whole maze can split into several components. |
| `connector` | Tree segments and connector segments alternate. A connector holds two virtual nodes, one per neighbor. | Many crossings. The whole maze is one tree. |

## Junction

`junction.py` models one column that crosses one X shaft. The junction is a 15x15x15 box at the crossing. Its +Z and -Z faces are the two patches. The seed gives one patch to the shaft and the other to the column.

- A junction segment replaces the chunks that hold the nearest surviving rulings on both sides of the box. That is two chunks, or three when the box sits at a chunk end.
- The junction segment graph is the rulings of those chunks minus the box rulings, plus the 7x7 patch nodes, plus a crossing from each interior patch border node onto the nearest ruling. One tree spans all of it.
- The junction segment keeps the chunk alternation. An end chunk with an odd index gives it a virtual node on that side.
- The junction scene shows the earlier column model: the pieces below and above the box are trees, and the column patch samples one tree with one virtual node per piece.
- The column scene is the current column model. The column is cut into 16-block sections like a horizontal shaft. The column patch is part of a column junction segment. At a build limit, a segment gets no virtual node on the outer side. `--column-stats` checks that the column is one tree.
- A crossing onto a patch fills the blocks along the ruling up to the patch border. The patch sits one block out from the shaft surface.
- Two boxes can sit so close on one shaft that their junction segments share a chunk. Then one junction segment spans both, with one patch per box. `--cluster-stats` checks that the shaft stays one tree.
- The cut model is the earlier design. It samples plain segments through the box, cuts them, and rejoins the stub components through the patch. `--cut-stats` prints how many components stay stranded.

## Commands

Run every command from the repository root.

Render the three layouts and the graph view:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24
```

Render with the reference sampler:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --sampler wilson
```

Print the dead-end fraction, the share of corners that carry a crossing, and the crossing counts per sampler and step count:

```bash
python3 prototypes/maze/originshift.py --stats
```

Render one junction from both sides and confirm that the shaft is one tree:

```bash
python3 prototypes/maze/junction.py --seed 1 --cx 203 --cy 70
```

Check that close boxes in one junction segment keep the shaft one tree:

```bash
python3 prototypes/maze/junction.py --cluster-stats --count 600
```

Check that a segmented column with two junctions is one tree:

```bash
python3 prototypes/maze/junction.py --column-stats --count 200
```

Render the unwrapped surface of one segmented column:

```bash
python3 prototypes/maze/junction.py --column-band
```

Measure the stranded components of the cut model:

```bash
python3 prototypes/maze/junction.py --cut-stats --count 40
```

## Output

The images are in `images/`.

- `band_<layout>_<sampler>.png` is the unwrapped surface. One block is 4 pixels. The top strip alternates color per chunk. The lighter rows are the four corners of the shaft.
- `closeup/` holds six chunks at 10 pixels per block.
- `graph_view.svg` shows three segments of the `oneroot` and `connector` layouts as node graphs, plus the contracted view of one connector. Big dots are corner nodes.
- `column_band.png` is the unwrapped surface of one segmented column from the bottom build limit to the top build limit. The junction boxes have a blue tint.
- `junction_front_<role>_patch.png` and `junction_back_<role>_patch.png` are isometric views of one junction. The role names the patch that faces the viewer. Raised blocks are maze walls. Recessed blocks are maze paths. The junction box has a blue tint.
