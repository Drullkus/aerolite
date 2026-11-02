# Shapes prototype

This folder holds the three.js prototype of the pillar shapes of Aerolite 0.4.0. It shows the current Pillar, the I-beam and the Bridge, their columns and the 9 junction pairs.

## Open the page

- Open `index.html` from the file system in a browser, for example `open prototypes/shapes/index.html` on macOS.
- The page needs no build step and no network.
- `vendor/three.min.js` is three.js r147 (npm `three@0.147.0`). `vendor/OrbitControls.js` is the classic script of the same release, from `examples/js/controls/`. `vendor/LICENSE` is the MIT license of three.js.
- The page loads classic scripts only. So it runs from `file://`.
- `shapes.js` builds the voxel scenes. It has no DOM dependency.
- `scene.js` renders the scenes as instanced boxes and binds the controls.
- The page keeps its state in the URL hash. A copied URL opens the same scene.

## Controls

| Control | Effect |
|---|---|
| Scene | Junction pair, all 9 junction pairs, 3 junctions on one pillar, X and Z junctions on one column, horizontal pillar, all 3 horizontal shapes, column, all 3 columns. |
| Horizontal shape | The shape of the horizontal pillar. |
| Column shape | The shape of the column. |
| Column web plane | The plane of the I-beam web of a column, X or Z. The Bridge column uses it for the plane of its trusses. |
| Pattern | The placeholder pattern, or no pattern. |
| Chunks | The pillar length. In a junction scene, the chunks on each side of the box. |
| Shear band | The shear band height of the column ends, 8 to 64 blocks. |
| Node lattice | Draws a marker on each node. A white marker is a corner node. |
| Resistance view | Colors each block by its erosion resistance. |
| Cut away +Z half | Removes the blocks on the +Z side of each item. |
| Junction link | Cuts the ring pattern at the boxes, adds the crossings and shows the component check. See [Junction link](#junction-link). |
| Bridge crossing | For a horizontal Bridge: picks the plate and the side of its one crossing once per bridge, or once per junction. |
| Iso, Side, End, Top | Camera directions. Side looks along -Z. End looks along -X. |
| Orthographic | Switches to an orthographic camera. |

Drag to orbit. Right drag to pan. Scroll to zoom.

## Colors

| Role | Color |
|---|---|
| Interior | Gray |
| Maze wall | Sand |
| Maze path (groove floor) | Blue |
| Surface path (flush) | Light blue |
| Truss, red diagonal | Red |
| Truss, green diagonal | Green |
| Junction box | Purple |
| Bezel | Orange |
| Crossing (maze path block of a notch) | Lime |
| Crossing route | Lime line |
| Hole | Cyan outline |
| Node marker | Magenta |
| Corner node marker | White |

The resistance view uses a ramp from yellow (0) to dark purple (1).

## Model

- A cross section uses the offsets (u, v) from -6 to 6. v is the stacking direction: flange to flange, or plate to plate.
- A horizontal pillar runs along X. v is Y and u is Z.
- A column runs along Y. Web plane X puts v on X. Web plane Z puts v on Z.
- A ring block is a solid block with an air block among its 8 neighbors.
- The ring starts at (-6, 6) and takes its first step along +u. Ring block 0 is the corner of `Ring.java`.
- Nodes sit on even ring blocks. Rulings sit on odd blocks along the axis.
- A convex ring block on an even index is a corner node. It has no axis edge.
- The floor of a carved ring block is the block one step inward. A convex block takes the inward diagonal.

### Placeholder pattern

- Each ruling is one ring path with one gap.
- One axis edge at the node next to the gap joins the ruling to the next ruling.
- The gap moves by 5 nodes on each ruling. A gap never sits on a corner node.
- A corner node of degree one is dropped with its edge, like in the mod.
- The junction patches show a boustrophedon over the 5x5 patch nodes.

## I-beam

- The flanges are 13 wide and 4 thick, at |v| from 3 to 6.
- The web is 5 thick and 5 tall, at |u| up to 2 and |v| up to 2.
- A horizontal I-beam has a vertical web, with flanges at the top and the bottom.

### Ring length

- The exposed blocks of the H are 60 blocks around. 60 is even.
- At each concave corner, the two exposed blocks are diagonal neighbors, for example (3, 3) and (2, 2).
- A ring of the 60 exposed blocks puts the web nodes at v = 1 and v = -1. `i-beam.png` puts them at v = 2, 0 and -2.
- So the ring also holds the hidden block of each concave corner, for example (2, 3). The ring is then 64 blocks, with 32 nodes.
- With 64 blocks, the nodes match `i-beam.png`: the flange edge nodes at v = 6 and v = 4, and the web nodes at v = 2, 0 and -2.

| Index | Blocks (u, v) | Face |
|---|---|---|
| 0 to 12 | (-6, 6) to (6, 6) | Outer face of the top flange |
| 13 to 15 | (6, 5) to (6, 3) | Edge of the top flange |
| 16 to 18 | (5, 3) to (3, 3) | Inner face of the top flange |
| 19 | (2, 3) | Concave corner block |
| 20 to 24 | (2, 2) to (2, -2) | Web face |
| 25 | (2, -3) | Concave corner block |
| 26 to 28 | (3, -3) to (5, -3) | Inner face of the bottom flange |
| 29 to 31 | (6, -3) to (6, -5) | Edge of the bottom flange |
| 32 to 44 | (6, -6) to (-6, -6) | Outer face of the bottom flange |
| 45 to 63 | (-6, -5) to (-6, 5) | The mirror of 13 to 31 on the -u side |

### Corners

| Index | (u, v) | Kind | Ring role | Floor |
|---|---|---|---|---|
| 0 | (-6, 6) | Convex | Corner node | (-5, 5) |
| 12 | (6, 6) | Convex | Corner node | (5, 5) |
| 15 | (6, 3) | Convex | Edge | (5, 4) |
| 19 | (2, 3) | Concave | Edge | (1, 3) and (2, 4) |
| 25 | (2, -3) | Concave | Edge | (2, -4) and (1, -3) |
| 29 | (6, -3) | Convex | Edge | (5, -4) |
| 32 | (6, -6) | Convex | Corner node | (5, -5) |
| 44 | (-6, -6) | Convex | Corner node | (-5, -5) |
| 47 | (-6, -3) | Convex | Edge | (-5, -4) |
| 51 | (-2, -3) | Concave | Edge | (-1, -3) and (-2, -4) |
| 57 | (-2, 3) | Concave | Edge | (-1, 3) and (-2, 4) |
| 61 | (-6, 3) | Convex | Edge | (-5, 4) |

- The 8 convex corners are the 4 outer corners and the 4 inner flange corners.
- The 4 outer corners sit on even indices. They are corner nodes with no axis edge.
- The 4 inner flange corners sit on odd indices. They are ring edges, so a groove wraps around them. No axis groove runs along them.
- The 4 concave corners sit on odd indices. Each one is the ring edge between a flange inner face node and a web node.
- A groove across a concave corner removes the two nodes and the hidden corner block between them.
- The floor of the hidden corner block is its two solid neighbors off the ring. So the groove floor is one unbroken L around the corner.
- `images/ibeam-concave-end.png` shows the L from the end. `images/ibeam-concave.png` shows it from below.
- `i-beam.png` has green 128 on the flange edge nodes and green 255 on the web nodes. The page draws both kinds as one node kind.

### Column web plane

- A column I-beam picks its web plane, X or Z, by seed per column. The page has a toggle for it.
- Web plane X puts the web in the XY plane. The flanges sit at X from ±3 to ±6.

## Bridge

- The bridge is 13 wide and 13 tall.
- The top plate and the bottom plate are 3 thick and 13 wide.
- 7 rows of truss space lie between the plates, at v from -3 to 3.

### Layers across the bridge

| u | Block 1 to 13 | Layer | Content at v from -4 to 4 |
|---|---|---|---|
| 6 | 13 | Plate side column | Empty truss space |
| 5 | 12 | Outer truss layer, +u side | Green truss |
| 4 | 11 | Inner truss layer, +u side | Red truss |
| -3 to 3 | 4 to 10 | Inside | Hollow, 7 wide and 7 tall |
| -4 | 3 | Inner truss layer, -u side | Green truss |
| -5 | 2 | Outer truss layer, -u side | Red truss |
| -6 | 1 | Plate side column | Empty truss space |

- The trusses stand one block in from the plate sides. The truss space at u = 6 and u = -6 is empty.
- On the +u side, green is outside red. On the -u side, red is outside green.
- 8 blocks lie between the two red layers. 8 blocks lie between the two green layers.
- The red and green diagonals never intersect, because they live in different layers.

### Layers up the bridge

| v | Layer |
|---|---|
| 6 | Outer face of the top plate. It holds the grooves. |
| 5 | Middle layer of the top plate. It holds the groove floor. |
| 4 | Inner face of the top plate. It holds the flush surface paths and the truss anchors of row 2. |
| 3 to -3 | Truss rows 3 to 9 of `bridge.png` |
| -4 | Inner face of the bottom plate. It holds the truss anchors of row 10. |
| -5 | Middle layer of the bottom plate |
| -6 | Outer face of the bottom plate |

### Trusses

- Row r of `bridge.png` is v = 6 - r. The column of `bridge.png` is the axis position s.
- The truss rule covers rows 2 to 10, at v from 4 to -4.
- A red truss block sits at u = 4 and u = -5, where s mod 8 is (5 - r) mod 8 or (6 - r) mod 8.
- A green truss block sits at u = 5 and u = -4, where s mod 8 is (r + 1) mod 8 or (r + 2) mod 8.
- Each diagonal is 2 blocks wide per row. It runs 8 rows over 8 blocks of length, with a period of 8.
- Both diagonals anchor at s mod 8 = 3 and 4, in row 2 and row 10. These are the white blocks of `bridge.png`.
- The trusses dig one block into the plates. Rows 2 and 10 are the inner plate layers.
- Anchor rule: a truss anchor block replaces the plate block at its position. It wins over a maze wall and over a flush surface path.
- In the middle row, v = 0, both diagonals sit at s mod 8 = 7 and 0. They cross there in projection.
- A rotation of 180 degrees about the vertical axis at s = 3.5 maps each red layer onto a green layer. So both sides look the same.
- `images/bridge-side.png` matches `bridge.png` along two periods.

### Pattern surface

- Each plate has its own ring of 28 blocks: the outer face (13 blocks), one side block, the inner face (13 blocks) and the other side block.
- The 4 plate corners sit on even indices. They are corner nodes.
- The outer face carries grooves.
- The inner face is flat. The flush pattern is 11 blocks wide, at u from -5 to 5. It matches the 11-wide groove floors of the outer face.
- In that area, a path is a flush surface path block and a wall is a maze wall block.
- The inner corner nodes at u = 6 and u = -6 are not flush. They carve like the plate side.
- A truss anchor in the flush area wins over the pattern. See the anchor rule.
- Proposal for the plate side: the side holds 2 corner nodes and 1 ring edge, like the corner margin of the Pillar. It shows maze wall, with a groove where a path wraps from the outer face to the inner face.
- The trusses are their own block field. They carry no pattern.

## Junctions

- Each of the 9 shape pairs gets the 15x15x15 junction box with the 3-block bezel on its two exposed faces.
- In the mod, every column has exactly two junctions: one with an X pillar at one height and one with a Z pillar at another height.
- An X junction exposes the ±Z faces. A Z junction exposes the ±X faces.
- The patch on the + exposed face serves the horizontal pillar. The patch on the - exposed face serves the column.
- A horizontal Bridge gets a hole of 11x11 along its axis through the box, at |y| up to 5 and up to 5 across the axis.
- The bridge blocks persist inside the hole. Where a box block and a bridge block share a position, the box block wins.
- So inside the hole, the plate layers at |v| = 4 and 5 and all four truss layers remain, with their anchors. The open passage is 7 wide and 7 tall.
- A column Bridge through a box gets no hole and no tunnel. The box keeps its full 15x15x15 shape.
- So the solid box cuts each plate of a column Bridge into a piece below the box and a piece above it.
- The scene "X and Z junctions on one column" shows one column with an X junction at y = -24 and a Z junction at y = 24. The control Horizontal shape sets both horizontal pillars.

## Junction link

The toggle Junction link models how the pattern joins the patch maze at a junction. It keeps the placeholder ring pattern and the patch boustrophedon. It uses no maze sampler.

### Model

- A cut pillar drops the rulings inside the box, at the axis offsets -7 to 7.
- A cut pillar also drops each axis edge that touches a dropped ruling. So the rim at the axis offsets 8 and -8 is maze wall.
- A union-find pass takes the placeholder ring edges and the patch edges first.
- Then it takes each candidate crossing in a fixed sequence. It adds a crossing only when the crossing joins two different components.
- The sequence of the candidates of a Pillar face or an I-beam is a seeded shuffle per junction.
- A crossing joins a ring node on the nearest surviving ruling, at the axis offset 9, to the patch node at the same offset across the pillar.
- A crossing runs along the pillar outer layer at the axis offset 8 up to the box.
- Then it notches through the bezel at the face offsets 7, 6 and 5, onto the patch node at offset 4.
- Each notch block is a maze path. The block under each notch block is the maze path block. The page colors it lime.
- A corner node that holds a crossing is not dropped as a leaf.
- The crossings of a horizontal pillar land on the left and right border sides of its patch. The crossings of a column land on the top and bottom border sides.

### Rules per shape

| Shape | Surface that faces the patch | Crossing candidates |
|---|---|---|
| Pillar | The pillar face | The 3 patch side nodes between the patch corners, at offsets -2, 0 and 2, on both sides of the box. |
| I-beam, flange edges face the patch | The two flange edges and the recessed web face | The flange edge nodes at offsets 4 and -4, onto the patch corner nodes, on both sides of the box. The 3 web nodes face the box wall and take no crossing. |
| I-beam, flange face faces the patch | The 13-wide flange face | Like a Pillar face: offsets -2, 0 and 2, on both sides of the box. |
| Horizontal Bridge | The side face of each plate | Exactly one crossing: the plate side corner node at offset 4 (top plate) or -4 (bottom plate), from one side of the box. |
| Column Bridge, matched junction | The side face of each plate | Four crossings onto the column patch: each plate piece, below and above the box, through its side face corner node at offset 4 or -4. |
| Column Bridge, mismatched junction | The outer face of each plate | The near plate joins the column patch like a Pillar face, below and above. The far plate joins the patch of the horizontal pillar like a Pillar face, below and above. |

- A horizontal pillar always faces its patch with the flange edges or the plate sides.
- A column faces the patches of an X junction with the faces toward ±Z, and the patches of a Z junction with the faces toward ±X.
- The owner allows a crossing onto a patch corner node for the I-beam flange edges and the Bridge plate sides.
- The I-beam is one ring and one tree. The solid box cuts it like a Pillar. The patch joins the ring pieces on both sides of the box.
- The two plates of a horizontal Bridge are two trees. Nothing joins them.
- The patch of a horizontal Bridge takes one crossing, so the patch hangs off one plate and no loop forms. The other plate takes no crossing at that junction.
- The control Bridge crossing picks that plate and side by seed: once per bridge instance, or once per junction. It applies to a horizontal Bridge only.
- `link-bridge-run-instance.png` and `link-bridge-run-junction.png` show three junctions on one bridge in each mode.

### Column Bridge

- A column Bridge has one matched junction, where its plate sides face the patches, and one mismatched junction, where its plate outer faces face the patches.
- With the web plane X, the plate sides face ±Z. So the X junction is matched and the Z junction is mismatched. The web plane Z swaps them.
- Every plate piece joins the patch it faces, from below and from above, at both junctions.
- At the matched junction, both plates join the column patch. The union-find takes all four crossings.
- At the mismatched junction, the near plate faces the column patch and joins it through its 3 side nodes between the patch corners. The union-find keeps the crossings that join components.
- At the mismatched junction, the far plate faces the patch of the horizontal pillar and joins it the same way. Those crossings land on the top and bottom border sides of that patch.
- So the column tree touches the tree of the horizontal pillar at the mismatched junction. The owner accepts this one exception to the rule that no tree touches another pillar.
- The two column patches belong to different trees, so no loop closes. Every end piece joins a patch.
- `link-column-xz.png` and `link-column-xz-webz.png` show the column Bridge with both junctions.
- `link-column-far-plate.png` shows the far plate crossings onto the patch of the horizontal pillar.

### End pieces

- A column Pillar or a column I-beam has no end piece problem. Its ring chains through both column patches, so the pieces below, between and above the junctions join one tree.
- A column Bridge has no end piece problem either. Each plate piece joins the patch it faces at its junction.

### Bridge hole

- A horizontal Bridge passes through each box inside the hole. Its plate trees run through the box uncut.
- The flush inner layer persists through the hole.
- The bezel on the exposed faces still frames the patch.
- The one bridge crossing notches through that bezel at the face offsets 7, 6 and 5, on the row of the plate side corner node, at y = 4 or y = -4.
- `link-bridge-corner.png` shows the notch.

### Check

- The panel shows one check line per junction scene.
- The line lists the component count of each pillar pattern through its junctions: one count per ring, so a horizontal Bridge gives one count per plate.
- The line also lists the patches that join no pillar ("floating"), and the number of crossings.
- A column Bridge gives one count for a group: its two plates, its patches and each horizontal pillar that it touches.
- The target of that group is 1, plus 1 for each touched horizontal Bridge, because only one plate of that Bridge joins its patch.
- A scene with one junction per column has no matched junction when the column Bridge mismatches it. That target then adds 1, and the line says "no matched junction".
- The other targets are 1 for a Pillar or an I-beam, 1 for each plate of a horizontal Bridge, and no floating patch.
- A line that meets the target reads OK. Any other line reads CHECK.

### Results

The scene "X and Z junctions on one column", for both web planes and both bridge modes:

| Horizontal + column | Horizontal pillars | Column | Check |
|---|---|---|---|
| Pillar + Pillar | X 1, Z 1 | 1 | OK |
| I-beam + Pillar | X 1, Z 1 | 1 | OK |
| Bridge + Pillar | X top 1, bottom 1; Z top 1, bottom 1 | 1 | OK |
| Pillar + I-beam | X 1, Z 1 | 1 | OK |
| I-beam + I-beam | X 1, Z 1 | 1 | OK |
| Bridge + I-beam | X top 1, bottom 1; Z top 1, bottom 1 | 1 | OK |
| Pillar + Bridge | X 1, Z 1 | group 1, target 1 | OK |
| I-beam + Bridge | X 1, Z 1 | group 1, target 1 | OK |
| Bridge + Bridge | X top 1, bottom 1; Z top 1, bottom 1 | group 2, target 2 | OK |

- With the web plane X, the column Bridge touches the Z pillar. With the web plane Z, it touches the X pillar.
- One junction, column Bridge matched (web plane X): group 1 for each horizontal shape.
- One junction, column Bridge mismatched (web plane Z): group 2 for a Pillar or an I-beam, group 3 for a Bridge. These columns have no matched junction, so the targets are 2 and 3.
- No patch floats in any scene. Every crossing joins two components, so no loop forms.
- The flange face of a column I-beam takes the 3 crossings of a Pillar face. Corner crossings apply only to the I-beam flange edges and to the Bridge.

## Erosion and shear

- The Pillar keeps its current resistance: it rises over 5 layers from the outer layer to the 3x3 core.
- Each plate and the web keep their middle layer at resistance 1. Resistance rises from each surface inward.
- The resistance of a plate block is its run of solid blocks to the nearest air along the plate thickness, divided by the layers to the middle, at most 1.
- A flange is 4 thick. Its two middle layers have resistance 1. Its surface layers have resistance 0.
- The web is 5 thick. Its layers have resistance 0, 0.5, 1, 0.5 and 0.
- A bridge plate is 3 thick. Its middle layer has resistance 1. Its two faces have resistance 0.
- Proposal: a truss block has the constant resistance 0.5. A truss anchor also has 0.5.
- A box block takes the largest of the box resistance and the resistance of each pillar through it, like today.
- Each column keeps the shear band at both ends, for each shape. The page uses one value noise. The threshold rises from 0 at the inner band edge to 1 at the end.

## Open questions

- The ring of the I-beam holds the 4 hidden concave corner blocks, so it is 64 blocks. The other choice is the 60 exposed blocks, with the web nodes at odd v.
- The inner flange corners are ring edges, not corner nodes. So the I-beam has no corner margin there.
- The two plates of a Bridge have separate rings. The sampler then makes one tree per plate, or joins the plates through the trusses.
- A truss block has the resistance 0.5 on the page.
- The Bridge column takes its truss plane from the same seeded choice as the I-beam web.

## Images

| File | Content |
|---|---|
| `shape-pillar.png`, `shape-ibeam.png`, `shape-bridge.png` | Each horizontal shape alone |
| `shapes-all.png` | The 3 horizontal shapes side by side |
| `ibeam-end-lattice.png` | The I-beam from the end, with the node lattice |
| `ibeam-concave.png`, `ibeam-concave-lattice.png` | A concave corner of the I-beam from below |
| `ibeam-concave-end.png` | A concave corner from the end, with the L of the groove floor |
| `bridge-side.png` | The bridge from the side, orthographic |
| `bridge-end.png` | The bridge from the end, orthographic, with the node lattice |
| `junction-<horizontal>-<column>.png` | Each of the 9 junction pairs |
| `junction-ibeam-ibeam-webz.png` | The I-beam pair with the web plane Z |
| `junctions-all.png` | The 9 junction pairs in one grid |
| `bridge-hole-cut.png` | The bridge hole through a box, with the +Z half cut away |
| `resistance-pillar.png`, `resistance-ibeam.png`, `resistance-bridge.png` | The resistance view of each shape from the end |
| `resistance-junction-cut.png` | The resistance view of a bridge junction, cut |
| `column-pillar.png`, `column-ibeam.png`, `column-bridge.png` | Each column with both sheared ends |
| `columns-sheared.png` | The 3 columns side by side |
| `link-junction-<horizontal>-<column>.png` | Each of the 9 junction pairs with the junction link and its check line |
| `link-junctions-all.png`, `link-junctions-all-webz.png` | The 9 pairs in one grid with the 9 check lines, web plane X and Z |
| `link-ibeam-corner.png` | The I-beam flange edge crossing onto a patch corner node |
| `link-bridge-corner.png` | The one bridge crossing, notched through the bezel beside the hole |
| `link-bridge-run-instance.png`, `link-bridge-run-junction.png` | Three junctions on one bridge, in both bridge crossing modes |
| `link-column-ibeam-webz.png` | The column patch of a column I-beam with the flange face toward the patch |
| `link-column-bridge.png` | The column patch of a column Bridge at a matched junction, with its four crossings |
| `link-column-xz.png`, `link-column-xz-webz.png` | One column Bridge with an X junction and a Z junction, web plane X and Z |
| `link-column-far-plate.png` | The far plate crossings onto the patch of the horizontal pillar at the mismatched junction |

- A WebKit view took the images. A small Swift program loads the page in a `WKWebView` and saves a snapshot of the page.
- Each image name matches a URL hash. For example, `bridge-side.png` is `index.html#scene=pillar&h=bridge&chunks=2&ortho=1&view=side`.
