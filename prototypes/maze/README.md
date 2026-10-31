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
- Origin Shift samples one spanning tree per segment. The default start tree is the breadth-first tree from a seeded root. See [Start trees](#start-trees).
- Wilson's algorithm is the reference sampler. With the degree rule, both converge to the same spanning tree distribution. See [Bias](#bias).
- A virtual node touches the interior nodes of the facing ruling, never its corners.

## Bias

The option `--bias P` sets the bias of Origin Shift. P is a value from 0 to 1. The default is 0.5.

The option `--rule` picks the bias rule. The rules are `class` and `degree`. The default is `class`.

- An axis edge joins two rulings, along the shaft axis.
- A ring edge joins two neighbor nodes of one ruling.
- An edge to a virtual node is an axis edge.
- In `junction.py`, a patch crossing is an axis edge.
- A patch edge along the shaft axis is an axis edge. A patch edge across the shaft axis is a ring edge.
- On a column, the axis is Y.
- A bias above 0.5 favors maze paths along the shaft. A bias below 0.5 favors maze paths around the shaft.
- The breadth-first start tree does not change with the bias or the rule.

### Class rule

- At each Origin Shift step, the walker picks the axis class with chance P and the ring class with chance 1 - P.
- Then the walker picks a uniform neighbor inside the chosen class.
- When the chosen class has no edge at the walker, the walker falls back to the other class. A corner node has no axis edge.
- At P = 0.5, a node with equal axis and ring degree picks a uniform neighbor.
- A node on the end ruling of a segment with no virtual node has one axis edge and two ring edges. At P = 0.5 it takes its axis edge with chance 1/2, not 1/3.
- Wilson's algorithm ignores the bias of the class rule. It samples the uniform spanning tree.
- Under the class rule, the chance of one edge depends on the number of edges of each class at the walker. So the class rule gives no edge a fixed weight.
- The class rule is not a walk with edge weights. So Wilson's algorithm has no exact reference for it.

### Degree rule

- Every axis edge has weight P. Every ring edge has weight 1 - P.
- At each Origin Shift step, the walker picks one edge at its node in proportion to the edge weight.
- At P = 0.5, every edge has the same weight. So the walker picks a uniform neighbor at every node.
- At P = 0 or P = 1, the walker never picks an edge of weight 0 while its node has an edge of nonzero weight.
- When every edge at the node has weight 0, the pick is uniform over all edges at the node.
- For P between 0 and 1, Origin Shift with the degree rule converges to the weighted spanning tree distribution.
- In the weighted spanning tree distribution, the chance of a tree is proportional to the product of its edge weights.
- With `--sampler wilson --rule degree`, the loop-erased random walk of Wilson's algorithm takes the same weighted steps.
- Wilson's algorithm with weighted steps samples the weighted spanning tree distribution exactly. It is the reference for the degree rule.
- At P = 0 or P = 1, the weighted walk cannot reach every node. Then Origin Shift does not mix, and the loop-erased walk never ends.
- So Wilson's algorithm with the degree rule refuses P = 0 and P = 1. `--compare` refuses them too.

## Start trees

Origin Shift starts from a start tree. Under a strong bias, the walker seldom takes an edge of the disfavored class. So parts of the start tree survive the steps. At P = 0.9, the breadth-first start tree leaves ribs in the column band. A rib is a block of stacked ring paths in the middle of a segment.

The option `--start` picks the start tree. The default is `bfs`.

| Start | Start tree |
|---|---|
| `bfs` | The breadth-first tree from the seeded root. |
| `uniform` | A uniform spanning tree. Wilson's algorithm with uniform steps samples it from the segment random. |
| `serpentine` | One path that snakes along the axis. It runs along one ring position, steps one ring position on the turn ruling, and runs back along the next ring position. |
| `hilbert` | A generalized Hilbert curve over the rectangle of 24 ring positions by the rulings of the segment. |

- The serpentine starts at ring position 1. It runs along each ring position that is not a corner.
- The serpentine crosses a corner ring position with two ring edges on the turn ruling.
- A corner node that the serpentine does not visit hangs as a leaf on a ring edge.
- The Hilbert curve is the "gilbert" recursion of Jakub Červený. It fills a rectangle of any size.
- The seam of the Hilbert rectangle is at the corner ring position 0.
- A curve start keeps only the curve edges that the graph has. The graph has no axis edge at a corner. The rectangle has no edge across its seam.
- A union-find pass over the graph edges joins the curve pieces into one tree. Then it adds the virtual nodes and the patch nodes.
- A virtual node joins the start tree by one edge.
- In `junction.py`, a curve start runs over each block of rulings that axis edges join. A junction box cuts the rulings of its junction segment into two blocks.
- The flag `--scaled-steps` divides the steps per node by min(P, 1 - P) and rounds the result.
- With `--scaled-steps`, 16 steps per node become 160 at P = 0.9 and at P = 0.1. They become 32 at P = 0.5.
- `--scaled-steps` refuses P = 0 and P = 1.
- `--start` and `--scaled-steps` apply only to `--sampler originshift`. Wilson's algorithm has no start tree.
- The `bfs` start takes the same random values as before `--start` existed. So the `bfs` images do not change.

The five approaches against the ribs are:

| Approach | Options |
|---|---|
| A | `--sampler wilson --rule degree`. Wilson's algorithm with weighted steps is the sampler. |
| B | `--start uniform` |
| C | `--start serpentine` |
| D | `--start hilbert` |
| E | `--scaled-steps`. The `bfs` start stays. |

- In approach A, the bias slider of the mod runs from 5% to 95%.
- The prototype accepts every bias strictly between 0 and 1 for Wilson's algorithm with the degree rule.

### Residue

- The residue is the share of the final tree edges that are also start tree edges.
- `--stats` prints the residue of each row.
- The `base` value is the mean overlap of two independent Wilson trees of the same segment graph. The Wilson row shows it as its residue.
- The `-base` column is the residue minus `base`.
- The `mixed` column is the mean overlap of the start tree with an independent Wilson tree of the same segment graph. A fully mixed Origin Shift reaches `mixed`.
- The `-mixed` column is the residue minus `mixed`. A positive value is the part of the start tree that survives the steps.
- `base` and `mixed` differ, because a start tree is not a sample of the weighted spanning tree distribution. A start tree with many edges of the disfavored class has a low `mixed`.
- At 0 steps per node, the residue is 1.
- For a curve start, `--stats` also prints the share of the curve edges that the start keeps.
- The Wilson trees take the weighted steps of the degree rule. With the class rule, they take uniform steps.

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

## Chunk paint

The mod paints one chunk at a time. The chunk paint of `junction.py` models that.

- The chunk paint fills the junction scene, then writes the carves chunk by chunk. A chunk is 16 blocks in X and Z.
- A carve is one maze path: the surface block that becomes air and the block beneath it that becomes the maze path block.
- The surface rule skips a carve whose surface block is outside the chunk. So a maze path at a chunk edge leaves the interior block under it.
- The each rule writes each block of a carve that is inside the chunk. The mod uses the each rule.
- An exposed interior block is a maze path block of the whole-scene paint that the chunk paint does not write.
- `--cz` sets the Z of the shaft center. The default is 0.
- A shaft center at Z 5 or Z 10 inside its chunk puts the surface and the block beneath it in two chunks. A patch does the same at Z 6 or Z 9.

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

Render the three layouts with a bias toward maze paths around the shaft:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.1
```

Render the three layouts with a bias toward maze paths along the shaft:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.9
```

Print the dead-end fraction, the share of corners that carry a crossing, and the crossing counts per sampler and step count:

```bash
python3 prototypes/maze/originshift.py --stats
```

Print the same statistics at one bias:

```bash
python3 prototypes/maze/originshift.py --stats --bias 0.9
```

Print the statistics of the degree rule. The Wilson row takes the same weighted steps:

```bash
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.5
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.9
```

Print the statistics of the class rule at the default bias:

```bash
python3 prototypes/maze/originshift.py --stats --rule class --bias 0.5
```

`--stats-steps` sets the Origin Shift steps per node of the table. The default is `1,4,16,64`. `--stats-count` sets the segments per row. The default is 200.

Print more step counts with more segments per row:

```bash
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.9 --stats-steps 16,32,64,128 --stats-count 1000
```

Render the three layouts with the degree rule:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.9 --rule degree
```

Render the comparison of the four samplers at one bias:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.9 --compare
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.5 --compare
```

Render one junction from both sides and confirm that the shaft is one tree:

```bash
python3 prototypes/maze/junction.py --seed 1 --cx 203 --cy 70
```

Render the same junction at one bias:

```bash
python3 prototypes/maze/junction.py --seed 1 --cx 203 --cy 70 --bias 0.9
```

Render the same junction with the degree rule:

```bash
python3 prototypes/maze/junction.py --seed 1 --cx 203 --cy 70 --bias 0.9 --rule degree
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

Render the same column at one bias:

```bash
python3 prototypes/maze/junction.py --column-band --bias 0.9
```

Render the column band of the class rule and the degree rule side by side at one bias:

```bash
python3 prototypes/maze/junction.py --column-band --bias 0.9 --compare
```

Check that the shaft and the column stay one tree with the degree rule:

```bash
python3 prototypes/maze/junction.py --cluster-stats --count 600 --rule degree --bias 0.9
python3 prototypes/maze/junction.py --column-stats --count 200 --rule degree --bias 0.9
```

Every scene of `junction.py` accepts `--bias P` and `--rule`. This includes `--cluster-stats`, `--column-stats` and `--cut-stats`.

Every scene of both programs also accepts `--start` and `--scaled-steps`.

Render the column bands of the five approaches side by side, and the four start trees with no step:

```bash
python3 prototypes/maze/junction.py --column-band --bias 0.9 --rule degree --compare-starts
python3 prototypes/maze/junction.py --column-band --bias 0.1 --rule degree --compare-starts
```

Render the connector bands of the five approaches as stacked rows:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.9 --rule degree --compare-starts
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.1 --rule degree --compare-starts
```

Render the Hilbert start tree of the column with no Origin Shift step:

```bash
python3 prototypes/maze/junction.py --column-band --bias 0.9 --rule degree --start hilbert --steps-per-node 0
```

Print the statistics and the residue of one start tree, and of the scaled steps:

```bash
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.9 --start hilbert
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.9 --scaled-steps
```

With `--scaled-steps`, `--stats` scales each step count of `--stats-steps`. The default step counts become 10, 40, 160 and 640 at P = 0.9. So that run takes longer than the run without `--scaled-steps`.

Print the statistics of every start tree that the comparison uses:

```bash
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.9 --start serpentine --stats-steps 16,64 --stats-count 400
python3 prototypes/maze/originshift.py --stats --rule degree --bias 0.9 --scaled-steps --stats-steps 16 --stats-count 400
```

Check that the shaft and the column stay one tree with one start tree:

```bash
python3 prototypes/maze/junction.py --cluster-stats --count 300 --rule degree --bias 0.9 --start hilbert
python3 prototypes/maze/junction.py --column-stats --count 100 --rule degree --bias 0.9 --start hilbert
python3 prototypes/maze/junction.py --column-stats --count 100 --rule degree --bias 0.9 --scaled-steps
```

`--compare-starts` needs `--rule degree`. In `junction.py`, it also needs `--column-band`.

Measure the stranded components of the cut model:

```bash
python3 prototypes/maze/junction.py --cut-stats --count 40
```

Count the exposed interior blocks of each chunk paint rule for every shaft center Z inside one chunk:

```bash
python3 prototypes/maze/junction.py --chunk-stats
```

Render the chunk paint of each rule at one shaft center Z:

```bash
python3 prototypes/maze/junction.py --chunk-paint --cz 10
python3 prototypes/maze/junction.py --chunk-paint --cz 9
```

## Output

The images are in `images/`.

- `band_<layout>_<sampler>.png` is the unwrapped surface. One block is 4 pixels. The top strip alternates color per chunk. The lighter rows are the four corners of the shaft.
- `closeup/` holds the first six chunks of each band at 10 pixels per block. The render of the bands writes them.
- `graph_view.svg` shows three segments of the `oneroot` and `connector` layouts as node graphs, plus the contracted view of one connector. Big dots are corner nodes.
- `column_band.png` is the unwrapped surface of one segmented column from the bottom build limit to the top build limit. The junction boxes have a blue tint.
- `junction_front_<role>_patch.png` and `junction_back_<role>_patch.png` are isometric views of one junction. The role names the patch that faces the viewer. Raised blocks are maze walls. Recessed blocks are maze paths. The junction box has a blue tint.
- A bias other than 0.5 adds `_bias<P>` to the file name, for example `band_connector_originshift_bias0.9.png`, `closeup/band_connector_originshift_bias0.9.png`, `graph_view_bias0.9.svg`, `column_band_bias0.9.png` and `junction_back_shaft_patch_bias0.9.png`.
- A Wilson render with the class rule gets no bias suffix, because Wilson's algorithm ignores the bias of the class rule.
- The degree rule adds `_degree` after the bias suffix, for example `band_connector_originshift_bias0.9_degree.png` and `junction_back_shaft_patch_bias0.9_degree.png`.
- At P = 0.5, the degree rule adds only `_degree`, for example `band_connector_originshift_degree.png`.
- A Wilson render with the degree rule keeps the bias suffix, for example `band_connector_wilson_bias0.9_degree.png`.
- `compare_bias<P>.png` stacks four bands of the `connector` layout at one bias. A label strip sits above each band.
- From top to bottom, the labels are "class P", "degree P", "wilson degree P" and "wilson uniform".
- "class P" and "degree P" are Origin Shift bands. "wilson degree P" is Wilson's algorithm with weighted steps. "wilson uniform" is Wilson's algorithm with uniform steps.
- `compare_column_bias<P>.png` puts two column bands side by side. A label strip sits above each band.
- The left band is "class P". The right band is "degree P". Both are Origin Shift bands.
- The labels use a 5x8 bitmap font in `originshift.py`.
- A start other than `bfs` adds `_<start>` after the rule suffix, for example `column_band_bias0.9_degree_serpentine.png`.
- `--scaled-steps` adds `_scaled`, for example `column_band_bias0.9_degree_scaled.png`.
- `--steps-per-node 0` adds `_start0`, for example `column_band_bias0.9_degree_hilbert_start0.png`. That image shows the start tree only.
- A step count other than 0 and 16 adds `_steps<n>`, for example `band_connector_originshift_bias0.9_degree_steps64.png`.
- Wilson's algorithm gets none of these suffixes.
- A column band of Wilson's algorithm adds `_wilson` after `column_band`, for example `column_band_wilson_bias0.9_degree.png`.
- `compare_starts_bias<P>.png` stacks six connector bands at one bias. A label strip sits above each band.
- From top to bottom, the labels are "bfs 16", "uniform 16", "serpentine 16", "hilbert 16", "bfs scaled" and "wilson".
- "wilson" is Wilson's algorithm with weighted steps. The other five are Origin Shift bands with the degree rule.
- `compare_starts_column_bias<P>.png` puts six column bands side by side, with the same labels from left to right.
- `compare_starts_only_bias<P>.png` puts the four start trees of the column side by side, with no Origin Shift step.
- From left to right, its labels are "bfs start", "uniform start", "serpentine start" and "hilbert start".
- The column comparisons use 5 pixels per block.
- `--compare-starts` also writes each band of a comparison under its own file name.
- The folder holds the bands, closeups and graph views for P = 0.1, 0.25, 0.5, 0.75 and 0.9 with seed 1 and 24 chunks.
- The folder holds the junction scene with seed 1 at X 203 and Y 70, and the column band, for P = 0.1, 0.5 and 0.9.
- The folder holds the degree rule bands, closeups, graph views, junction scenes and column bands for P = 0.1, 0.5 and 0.9, with the same seed and place.
- The folder holds `compare_bias<P>.png` and `compare_column_bias<P>.png` for P = 0.1, 0.5 and 0.9, with seed 1 and 24 chunks.
- The folder holds `compare_starts_bias<P>.png`, `compare_starts_column_bias<P>.png`, `compare_starts_only_bias<P>.png` and their bands for P = 0.1 and 0.9, with seed 1 and 24 chunks.

- `chunk_paint_<rule>_<view>_cz<Z>.png` is the chunk paint of one rule from the front or the back, at shaft center Z. The exposed interior blocks are red.
- The folder holds the chunk paint of both rules at Z 10 and Z 9. Z 10 shows the shaft and the column. Z 9 shows the patches.

- `seed3/` holds one junction with seed 3, rendered before the bias option existed.
