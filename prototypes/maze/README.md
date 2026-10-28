# Maze prototype

This folder holds the standalone maze prototype for the shaft surface. It needs Python 3 and no packages.

## Model

- The shaft surface is a cylinder. It is 48 blocks around and infinite along the shaft axis.
- Nodes sit on odd blocks. Edges sit on even blocks. Every other block is a maze wall.
- The node grid is 24 nodes around. One chunk holds 8 node rows along the axis.
- The program cuts the cylinder into segments. Each segment is a pure function of the seed and its index.
- Origin Shift samples one spanning tree per segment. The start tree is the breadth-first tree from a seeded root.
- Wilson's algorithm is the reference sampler. Both converge to the uniform spanning tree.

## Layouts

| Layout | Segments | Boundary |
|---|---|---|
| `oneroot` | Every segment is a tree. | One crossing per boundary. The boundary is a wall ring with one gap. |
| `forest` | Every segment is a forest. Each component has one forward crossing. | Many crossings. The whole maze can split into several components. |
| `connector` | Tree segments and connector segments alternate. A connector holds two virtual nodes, one per neighbor. | Many crossings. The whole maze is one tree. |

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

Print the dead-end fraction and the crossing counts per sampler and step count:

```bash
python3 prototypes/maze/originshift.py --stats
```

## Output

The images are in `images/`.

- `band_<layout>_<sampler>.png` is the unwrapped surface. One block is 4 pixels. The top strip alternates color per chunk. The lighter rows are the four corners of the shaft.
- `graph_view.svg` shows three segments of the `oneroot` and `connector` layouts as node graphs, plus the contracted view of one connector.
