# Plan: island variations

Date: 2026-11-13. Status: draft 2, for the approval of the owner. No subagent starts before the owner approves this file.

The owner gets tools to build island variations and to ship them as prefabs. Each island cell picks one island variation from a weighted list. Each island variation holds its own density function, its own biome source and its own settings.

Read `CLAUDE.md` and `~/.claude/CLAUDE.md` first. Section 1 holds the decisions of the owner. Section 2 holds the readings of the session. The owner can strike any reading before the approval.

## 1. Decisions of the owner

All of 2026-11-13. "Request" is the first message of the session. "Round" is a question round of the session.

| # | Decision | Source |
|---|---|---|
| D1 | An island variation dispatches its own density function and its own biome source. | Request |
| D2 | Empty is an island variation. The empty chance setting goes away. | Request |
| D3 | A weighted list picks the island variation of each cell. A list entry can be another list. | Request |
| D4 | The lattice keeps the cell size, the cell height, the island bottom and the jitter. Each other slider of 0.5.0 moves to the island variation. | Request, round 1 |
| D5 | Only the row 0 holds islands. The sites above and below hold nothing. They shape the cells of the row 0. | Request |
| D6 | An island variation holds a cell weight range inside 0 to 1. The default is 0.5. Each cell rolls its cell weight in the range of its variation. | Request |
| D7 | The cell weight pushes the wall between two sites. The cells follow the power rule. A block takes the site with the least squared distance minus cell weight times the squared cell size. | Round 5, rule P. It replaces rule B of round 1. |
| D8 | Every block belongs to exactly one cell. Every wall is flat. Every cell is convex. | Round 5, rule P |
| D9 | Each site above and below the row 0 has the cell weight 0.5. | Round 1 |
| D10 | The island bottom is a lattice setting only. An island variation has no height setting. | Round 1 |
| D11 | Two registries hold the data, like the pillars: `aerolite:island_variation` and `aerolite:island_variation_list`. | Round 2 |
| D12 | An Empty variation holds its own biome source. A block outside the row 0 takes the biome source of the nearest cell of the row 0. | Round 2 |
| D13 | Four biome source types get a form: fixed biome, multi-noise preset, the End and checkerboard. Any other type stays as pasted JSON. | Round 2 |
| D14 | The palette gets the shape nodes island body, cell gauge, wall distance and site offset. | Round 3 |
| D15 | The palette gets the value nodes cell random, site distance, cell radius, rolled fill and rolled cell weight. | Round 3, and the decision of 2026-11-08 |
| D16 | The islands preview gets Solo, the hover readout, the color by variation and the pinned rolls. | Round 3 |
| D17 | The Islands screen and the preview panel both hold the list and the forms of a variation. | Round 4 |
| D18 | An Export button writes the design files. The owner puts them in a design folder. Data generation reads that folder and writes the shipped entries. | Round 4 |
| D19 | A carrier node on one climate output of the router gives the biome source its lattice. | Round 4 |

## 2. Readings of the session

The owner did not pick these. Each one follows from a decision or from the code. Strike a reading to change it.

| # | Reading | Follows from |
|---|---|---|
| R1 | The distortion of a variation shifts the position only inside the island body node. The walls of a cell stay flat. An island that reaches a wall ends flat on it. | D4, D7 |
| R2 | The weight of a list entry is a whole number from 0 to 16. Each list holds at least one entry with a positive weight. | The pillar variation list |
| R3 | The pick walks a list in its JSON sequence. A sub-list picks again with its own seeded value. | The pillar variation list |
| R4 | A list that names itself through its entries gives no island for that entry. The game logs one line per list. | D3 |
| R5 | The entries of 0.5.0 stay shipped under their ids. They are the type `aerolite:voronoi_islands`, the density functions `aerolite:islands/<dimension>` and the noise settings `aerolite:islands/<dimension>`. So a world of 0.5.0 keeps its terrain. | The save rules of the pillars |
| R6 | The new entries get new ids. See section 5.6. The preset `aerolite:islands` names the new entries. | R5 |
| R7 | The shipped data repeats the values of 0.5.0. Each dimension ships one list with Empty at weight 1 and one Basic variation at weight 9. | The owner designs the variations |
| R8 | The Basic variation holds the island body node alone. Its fill is 30% to 90%. Its distortion is 12, with scale 64 and 3 octaves. Its cell weight is 0.5 to 0.5. | `IslandSettings.DEFAULT` |
| R9 | The Basic variation and the Empty variation of a dimension hold the vanilla biome source of that dimension. | 0.5.0 |
| R10 | The Islands screen loses its dimension biome rows. The biome source form of each variation replaces them. The Aerolite Void screen keeps its rows. | D1 |
| R11 | A changed variation or list of the flow goes to the island pack under its own id. So each list that names the entry follows the change. | The island pack rule for density functions |
| R12 | Each variation and each list has an id field in its form. A new entry starts as `aerolite_world:island_<n>` or `aerolite_world:list_<n>`. A Duplicate button copies an entry under a new id. | D18 |
| R13 | A variation reads its name from `island_variation.<namespace>.<path>`. A list reads its name from `island_variation_list.<namespace>.<path>`. An entry without that translation shows its full id. | The pillar prefab names |
| R14 | The add row lists "New island", "New Empty" and "New list" first. Every variation and every list of the registries follow by name. Add appends the pick with weight 1. | D11, D17 |
| R15 | A cell of an Empty variation draws nothing in the preview. It takes no hover and no click. | The preview rule of 0.5.0 |
| R16 | Solo works on any row. Solo on a list row makes every cell pick inside that list. | D16 |
| R17 | The carrier sits on the temperature output of each shipped island router. | D19 |
| R18 | An island node outside the graph of a variation gives 0. The validator does not report it. | D14 |
| R19 | A design file wins over a data generation class with the same id. Data generation logs one line per replaced id. | D18 |
| R20 | The Export button writes the variations, the lists, the density function entries and the sidecars of the island pack. It writes no noise settings. | D18 |
| R21 | A sidecar is the JSON file with the canvas positions of the function nodes of one graph. The mod ships the sidecars of the design folder. A shipped graph then opens with the positions of the owner, not with the automatic layout. | D18 |
| R22 | The clipboard buttons Copy and Paste for one variation are not in this plan. | D18 |
| R23 | The owner checks the prototype of batch 0 before batch 1 starts. | The global prototype rule |
| R24 | The session linked two photographs of unequal polygon cells in chat. Batch 0 links no further photograph. | The global terrain rule |
| R25 | The center structures plan of 2026-11-12 stays apart. This plan builds the registry that its decision 1.1 B waits for. | `llm/plans/2026-11-12_center_structures.md` |
| R26 | The power rule measures distance with the Y axis scaled by cell size over cell height. So a neighbor above counts as far as a neighbor beside. With equal cell weights, the cells equal the cells of 0.5.0 only when the cell height equals the cell size. | D7, D9. The option text of round 5 said "Equal weights give the cells of 0.5.0". That line was wrong for a cell height that differs from the cell size. |
| R27 | A cell that does not hold its own site is a lost cell. It holds no island. A cell without space is also a lost cell. | D7 |
| R28 | The nearest cell of the row 0 in D12 is the row 0 cell with the least power rule value. | D7, D12 |

## 3. Terms

The last batch puts these terms in the glossary of `CLAUDE.md`. Use them exactly.

| Term | Meaning |
|---|---|
| Island variation | One entry of `aerolite:island_variation`: an island or Empty. It replaces the term island type. |
| Empty variation | An island variation with `empty` set. Its cells hold no island. |
| Island variation list | One entry of `aerolite:island_variation_list`: weighted entries. Each entry names an island variation or another island variation list. |
| Lattice settings | The cell size, the cell height, the island bottom and the jitter of one dimension. |
| Cell weight | The value of one cell from 0 to 1 that pushes its walls. |
| Power rule | The cell rule. A block takes the site with the least power rule value. |
| Power rule value | The squared scaled distance from a block to a site, minus the cell weight times the squared cell size. |
| Lost cell | A cell that does not hold its own site, or a cell without space. It holds no island. |
| Roll | One seeded value of one cell: its cell weight, its fill, or a cell random value. |
| Island function | The density function type `aerolite:island_variations`. It finds the cell of a block and computes the density function of its variation. |
| Island node | A density function type that reads the cell of the block. It works only under an island function. |
| Carrier | The pass-through density function type `aerolite:island_carrier`. It holds the island function for the biome source. |
| Island biome source | The biome source type `aerolite:island_biomes`. It gives each block the biome source of its variation. |
| Design folder | `src/datagen/resources/island_designs`. Data generation reads the designs of the owner from it. |

## 4. Facts to verify first

Each build subagent checks the facts of its batch in `build/moddev/artifacts/neoforge-<version>-sources.jar`. A fact that does not hold stops the batch. The subagent then reports the fact and changes nothing.

- F1. `RandomState` maps the router with its wiring visitor. The climate sampler holds the mapped climate functions. A custom density function class stays in them.
- F2. The wiring visitor replaces a `HolderHolder` by its value and a marker by its wrapped function in the sampler.
- F3. A cache wrapper of `NoiseChunk` computes its function directly when the context is not that noise chunk.
- F4. `TheEndBiomeSource` reads the erosion of the sampler. So a biome source can read a density function of the sampler.
- F5. A holder of a datapack registry has no value during the registry load. It has its value after the load.
- F6. NeoForge loads a registry of `DataPackRegistryEvent.NewRegistry` from `data/<namespace>/<registry namespace>/<registry path>/`.
- F7. The codecs: `minecraft:fixed` has `biome`. `minecraft:multi_noise` has `preset`. `minecraft:checkerboard` has `biomes` and `scale`. `minecraft:the_end` has no field.
- F8. `ChunkGeneratorStructureState` reads only the possible biomes of the biome source at its creation.

## 5. Design

### 5.1 Lattice and power rule

- The sites stay the sites of `VoronoiLattice`. The site of a cell does not depend on a cell weight.
- A cell of the row 0 has the rolled cell weight of its variation. Any other cell has the cell weight 0.5.
- `S` is the cell size. The Y scale is `S / cell height`. A scaled position has its Y times the Y scale.
- The power rule value of a block for cell i is `|P - Si|^2 - wi * S^2`. `P` and `Si` are the scaled positions of the block and the site.
- The cell of a block is the cell with the least power rule value. So every block has exactly one cell.
- For two cells i and j, `V = Sj - Si` and `h = |V|^2 / 2 + (wi - wj) * S^2 / 2`.
- The wall of cell i toward cell j is the plane `(P - Si) . V = h`. Each wall is flat, and each cell is a convex polyhedron.
- At the site distance `S`, weight 1 against weight 0 puts the wall on the lighter site. A nearer pair pushes the wall further. A farther pair pushes it less.
- A cell with `h <= 0` for one of its walls does not hold its own site. It is a lost cell. See R27.
- The gauge of cell i at `P` is the largest `((P - Si) . V) / h` over its walls. It is 0 at the site and 1 on the wall.
- In block space, the normal `N` of a wall is `V` with its Y times the Y scale.
- The wall distance of a block is the smallest `(h - (P - Si) . V) / |N|` over the walls, in blocks.
- The cell radius is the smallest `h / |N|` over the walls, in blocks.
- With every cell weight at 0.5 and the Y scale 1, each wall equals the wall of 0.5.0. So does each gauge and each cell radius.
- The search reach is fixed. A site can win only when its squared scaled distance is at most `S^2` above the least squared scaled distance.
- The build subagent derives the search reach and the wall reach from that bound. It states the rule in the Javadoc.
- The lattice keeps a bounded cache of sites and rolls by lattice box. The cache is an instance field. Each entry is a pure function of its key.
- `CellGeometry` clips each cell polyhedron with the wall planes in block space. A lost cell draws nothing.

### 5.2 Pick and rolls

- Each cell of the row 0 picks one island variation. The pick is a pure function of the lattice seed, the cell and the list.
- The pick takes one seeded value per list depth. It walks the entries in sequence by weight.
- A weight of 0 never picks.
- The cell weight roll and the fill roll each have their own salt. Each channel of the cell random node has its own salt.
- The rolled cell weight is uniform between Cell Weight Min and Cell Weight Max. The rolled fill is uniform between Fill Min and Fill Max.
- The pick does not read the site. So the cell weight of a cell does not depend on its neighbors.

### 5.3 Data

`data/<namespace>/aerolite/island_variation/<path>.json`, an island:

```json
{
  "density_function": "aerolite:island_variations/basic",
  "biome_source": { "type": "minecraft:fixed", "biome": "minecraft:plains" },
  "cell_weight": [0.5, 0.5],
  "fill": [0.3, 0.9],
  "distortion": 12,
  "distortion_scale": 64,
  "distortion_octaves": 3
}
```

An Empty variation:

```json
{
  "empty": true,
  "biome_source": { "type": "minecraft:fixed", "biome": "minecraft:the_void" },
  "cell_weight": [0.5, 0.5]
}
```

`data/<namespace>/aerolite/island_variation_list/<path>.json`:

```json
{
  "entries": [
    { "variation": "aerolite:overworld_empty", "weight": 1 },
    { "variation": "aerolite:overworld_basic", "weight": 9 },
    { "list": "aerolite:forest_group", "weight": 3 }
  ]
}
```

- `density_function` is a density function holder: an id or an inline tree.
- `biome_source` is a vanilla biome source object.
- `cell_weight` and `fill` are pairs of minimum and maximum. The codec sorts a pair and clamps each value to 0 and 1.
- A field without a value takes the default of R8. `cell_weight` defaults to 0.5 and 0.5.
- An entry of a list holds exactly one of `variation` and `list`.
- A list holds resource keys, not holders. So a list can name an entry of another pack.
- The two registries do not sync to the client.
- The bounds of the sliders are constants of the variation record. The cell weight runs from 0 to 1 in steps of 0.05. The fill runs from 0% to 100% in steps of 5%. The distortion bounds stay the bounds of `VoronoiIslands`.

### 5.4 Island function and island nodes

The island function, `aerolite:island_variations`:

```json
{
  "type": "aerolite:island_variations",
  "noise": "aerolite:islands",
  "cell_size": 256,
  "cell_height": 128,
  "layer_min_y": 0,
  "jitter": 0.8,
  "variations": "aerolite:overworld_islands"
}
```

- `variations` is the id of an island variation list.
- The island function resolves its list at its first use, not in its constructor. See F5.
- `mapAll` maps the density function of each variation that the list reaches. So the random state wires their noises.
- `compute` finds the cell of the block. A cell outside the row 0, an Empty cell and a lost cell give the lowest value.
- Else `compute` gives the value of the density function of the variation. That function gets an island context.
- The island context is a `DensityFunction.FunctionContext`. It holds the block position, the cell, the variation and the rolls.
- An island node reads the island context. With any other context it gives 0.
- The island function is a `SimpleFunction`. The vanilla interpolation wraps it in the router, as today.

The island nodes:

| Type id | Fields | Value |
|---|---|---|
| `aerolite:island_body` | none | `(rolled fill - gauge at the shifted position) * cell radius`. The shift is the distortion of the variation. |
| `aerolite:cell_gauge` | none | The gauge of the cell at the block. |
| `aerolite:wall_distance` | none | The wall distance of the block, in blocks. |
| `aerolite:site_offset` | `axis`: `x`, `y` or `z` | The offset of the block from the site along the axis, in blocks. |
| `aerolite:site_distance` | `plane`: `xyz` or `xz` | The distance of the block from the site, in blocks. |
| `aerolite:cell_radius` | none | The cell radius, in blocks. |
| `aerolite:cell_fill` | none | The rolled fill. |
| `aerolite:cell_weight` | none | The rolled cell weight. |
| `aerolite:cell_random` | `min`, `max`, `channel` | One seeded value of the cell between `min` and `max`. Each channel is independent. |

- The distortion noise of a variation seeds from the lattice seed and the distortion fields. It keeps the octave rule of 0.5.0.
- Take distortion 0, every cell weight at 0.5 and a cell height equal to the cell size. Then the island body equals `VoronoiIslands` in each cell of the row 0.
- A preview override replaces the pick and the rolls. It is a transient field of the island function. The codec does not write it.

### 5.5 Carrier and island biome source

```json
{ "type": "aerolite:island_carrier", "islands": "aerolite:island_variations/overworld", "argument": { "...": "the temperature function" } }
```

```json
{ "type": "aerolite:island_biomes", "variations": "aerolite:overworld_islands" }
```

- The carrier computes its argument. Its `islands` field is a density function. `mapAll` maps both fields.
- The island biome source searches the six climate functions of the sampler for an island function. It keeps the result per sampler object in an instance cache.
- The lookup of a quart position takes the block at the quart position. It asks the island function for the cell.
- A cell of the row 0 gives the biome source of its variation. Any other block gives the biome source of the row 0 cell with the least power rule value.
- The picked biome source then answers the lookup with the same sampler.
- A sampler without an island function gives the fallback biome: the first possible biome of the list, else the Plains. The source logs one line per sampler.
- The possible biomes are the possible biomes of every variation that the list reaches.
- The preview override of the island function reaches the biome source through the carrier. So Solo also changes the biomes of the preview.

### 5.6 Shipped data and old worlds

| Entry | Id |
|---|---|
| Island function entry of a dimension | density function `aerolite:island_variations/<dimension>` |
| Graph of the Basic variation | density function `aerolite:island_variations/basic` |
| Noise settings of a dimension | `aerolite:island_variations/<dimension>` |
| List of a dimension | `aerolite:<dimension>_islands` |
| Basic variation of a dimension | `aerolite:<dimension>_basic` |
| Empty variation of a dimension | `aerolite:<dimension>_empty` |

- `AeroliteIslandEntries` generates these entries. Its entries of 0.5.0 stay.
- Each new router is the router of 0.5.0 with two changes. The island function entry is its terrain. The carrier sits on its temperature output.
- The preset `aerolite:islands` gives each dimension the island biome source and the new noise settings.
- A world of 0.5.0 names the old noise settings in `level.dat`. It keeps its terrain and its vanilla biome source.
- An island pack of 0.5.x overrides the old entries. It still loads.
- `IslandGenerators` and `IslandSettings` stay for those worlds.

### 5.7 Editor model and island pack

- `FunctionNodeTypes` describes the island function, the nine island nodes and the carrier. A new field kind holds the id of an island variation list.
- `IslandGraphs` also holds the variations and the lists of the flow, each with its start JSON.
- The density function of a variation opens as a function graph. An inline tree first becomes an entry under the id of the variation.
- `GraphSettings.build` puts the edited variations and lists in place for the preview, like the edited density function entries.
- A problem of a variation or a list fails the build. The terrain then keeps the last good build.
- `IslandPack` writes each changed variation and each changed list to its registry folder. It keeps the start JSON of each under `aerolite_sidecar/start_json`.
- The restore reads both registry folders back into the graphs of the flow.
- The lattice sliders edit the first island function node under the router outputs of the dimension. A router without one shows the lattice sliders and the list off.
- The validator reports a router graph without a carrier over an island function on a climate output. The problem shows on the router box.
- The validator reports three list problems: a missing entry, no positive weight, and a list that names itself.

### 5.8 Islands screen

- The header Islands holds the dimension cycle button and the lattice sliders. The slider rows are Cell Size and Cell Height, then Island Bottom and Jitter.
- The list of the dimension follows: the add row, then one row per entry.
- A row holds the name, the weight slider, the arrow button and the minus button. The last positive weight of a list does not go below 1.
- The arrow of a variation opens the variation screen. The arrow of a list opens the list screen.
- The variation screen holds the id field, the Cell Weight Min and Max sliders and the Fill Min and Max sliders. The three distortion sliders and the biome source form follow. The density function id shows as text.
- The variation screen of an Empty variation holds the id field, the cell weight sliders and the biome source form.
- A minimum slider that passes its maximum slider pushes it along.
- The list screen holds the id field, the add row and the rows of the sub-list.
- Each of these screens has Done and Cancel, like the provider screen. Done writes to the copy of the Islands screen.
- The biome source form holds the Type dropdown and the rows of the type.
- Fixed biome shows the biome field with a search dropdown. Multi-noise preset shows a dropdown of the parameter list entries.
- The End shows no row. Checkerboard shows the biome lines with add and remove, and the Scale slider.
- Any other type shows "No editor for" and the type id. The draft keeps that biome source as it is.
- A pure logic draft class holds the form values, like `ProviderDraft`. The screen and the panel share it.
- The dimension biome rows leave the Islands screen. See R10.

### 5.9 Preview panel and preview

- The header Islands of the panel holds the lattice sliders, then the list as a tree. A list row opens its sub-list in place.
- Each variation row holds the Solo switch, the color square, the name, the weight slider and the remove button.
- The opened variation shows the id field, the range sliders and the distortion sliders. The biome source form and the Node Editor button follow.
- The Node Editor button opens the function graph of the variation.
- The add row and the Export button follow the list.
- The pins sit in the Preview section: "Pin cell weight" and "Pin fill", each a checkbox and a slider.
- Solo and the pins are preview overrides. They change no data, and the preview does not save them.
- A change of Solo or of a pin starts a full rebuild.
- With the color mode on, each cell draws its lines in the color of its variation. A fixed color table of `PreviewRenderer` gives the colors in list sequence. The hovered cell stays gold.
- The checkbox "Color by variation" sits under "Generate all chunks".
- The hovered cell shows a tooltip: the variation name, the rolled cell weight and the rolled fill.
- The preview draws each cell as its weighted polyhedron.
- Only classes in `us.drullk.aerolite.client.preview.imgui` reference ImGui types.

### 5.10 Export and design folder

- The Export button writes to `aerolite_export` in the game directory. It replaces the folder.
- The export holds each variation, each list and each edited density function entry of the flow, with its sidecar. The paths are the pack paths.
- A system toast names the folder and the file count. A failed write names the error.
- The owner copies the content of `aerolite_export/data` into `src/datagen/resources/island_designs`.
- Data generation reads each file of the design folder. It decodes each file with its codec and the registries of the run.
- A file that does not decode stops data generation. So does a design that names a missing entry. The error names the file.
- Data generation writes each design to `src/generated/resources` under its id, in the key sequence of the design.
- A design with the id of a generated entry replaces that entry. See R19.
- A sidecar of the design folder ships under `data/<namespace>/aerolite_sidecar/`. The editor reads it for an entry without positions.
- The production jar does not hold the design folder.

## 6. Batches

Each batch has one Opus build subagent and one Opus check subagent. The batches run in sequence. Each batch has its own branch, cut from the branch of the batch before it.

| Batch | Branch | Content | Needs |
|---|---|---|---|
| 0 | `claude/cells-prototype` | The prototype of the power rule cells. | The approval of this plan |
| 1 | `claude/weighted-lattice` | Section 5.1 and 5.2 in Java, and the weighted polyhedra of `CellGeometry`. | Batch 0 and the prototype check of the owner |
| 2 | `claude/island-variation-data` | Sections 5.3 to 5.6: the registries, the island function, the island nodes, the carrier, the island biome source, the shipped data. | Batch 1 |
| 3 | `claude/island-variation-graphs` | Section 5.7: the node types, the graphs of the flow, the preview build, the island pack. | Batch 2 |
| 4 | `claude/island-variation-screens` | Section 5.8: the Islands screen, the variation screen, the list screen, the biome source draft. | Batch 3 |
| 5 | `claude/island-variation-panel` | Section 5.9: the panel, Solo, the pins, the colors, the hover readout, the palette entries. | Batch 4 |
| 6 | `claude/island-designs` | Section 5.10: the Export button, the design folder, the shipped sidecars. | Batch 5 |
| 7 | `claude/island-variation-docs` | `CLAUDE.md`: the terms of section 3, one bullet per rule that the code cannot state, the open questions of section 8. | Batch 6 |

### Batch 0: prototype

- `prototypes/cells/cells.py`: the lattice of section 5.1 as a standalone program. `--check` runs the checks below. `--tables` prints the tables for the JUnit tests.
- `prototypes/cells/README.md`: the exact geometry, the commands and the tables.
- `prototypes/weighted_cells.frag`: the island body over the weighted cells as a signed distance function.
- `prototypes/shadertoy/weighted_cells.shadertoy.glsl`: the port for one Shadertoy "Image" tab, with no Common tab and no channels. A define at the top sets each cell weight range.
- Checks: the owner lookup equals a brute force lookup over a wide reach. The walls of three neighbor cells meet in one line. Every weight at 0.5 with a cell height equal to the cell size gives the plain Voronoi cells. The derived reaches miss no cell and no wall in a seeded sweep of weights.
- The checks print the share of lost cells of the row 0 for a seeded set of weight ranges and jitter values.
- `main` has no `prototypes/tools`. The subagent validates the GLSL with `glslangValidator` when the machine has it. Else its report says that the GLSL has no validation.
- Done: the checks pass, and the report links the Shadertoy file.

### Batch 1: weighted lattice

- `VoronoiLattice` takes the cell weight of a cell from a function that the caller gives. It also takes the Y scale.
- `VoronoiIslands` gives 0.5 for every cell and the Y scale 1. So its values do not change.
- New pure logic: the power rule value, the owner lookup, the lost cell test, the gauge and the cell radius. Also the wall distance, the two reaches and the site cache.
- `CellGeometry` builds each polyhedron from the wall planes of the power rule.
- JUnit: the tables of the prototype. The owner lookup against a brute force lookup. Each value against 0.5.0 at weight 0.5 and Y scale 1. The existing `VoronoiLatticeTest`, `VoronoiIslandsTest` and `CellGeometryTest` pass without a change of an expected value.
- JUnit: a timing test prints the time of 100000 density samples of `VoronoiIslands` before and after the batch. The report states both numbers.
- Done: the islands probe shows the same pictures as on `main`.

### Batch 2: data, island function, biomes

- The package `us.drullk.aerolite.world.island.variation` holds the variation record, the list record, the registry keys, the pick and the rolls.
- The package `us.drullk.aerolite.world.island.node` holds the island context, the island function, the island nodes and the carrier.
- `IslandBiomeSource` is in `us.drullk.aerolite.world.island`.
- The mod registers the two datapack registries, the density function types and the biome source type.
- `AeroliteIslandEntries` generates the entries of section 5.6. `runData` writes them. The lists keep their key sequence.
- The lang provider gives the names of the shipped variations and lists.
- JUnit: the codecs, the defaults and the clamps. The pick shares over a seeded sample, the sub-list pick and the list that names itself. Each island node against the lattice values. The island body against `VoronoiIslands`.
- GameTests: the Islands preset loads with the island biome source. A generated chunk holds the blocks of the island function. An Empty cell holds no block. The biome of a block is a biome of the source of its cell. A block above the row 0 takes the source of the nearest row 0 cell. No lava fills the void. The old noise settings `aerolite:islands/overworld` still fill a chunk.
- Done: a new Islands world generates. The islands probe passes with its new expected values.

### Batch 3: graphs and pack

- `FunctionNodeTypes`, `FieldKind`, `GraphJson` and `GraphValidator` take the new types and the new field kind.
- `IslandGraphs`, `GraphSettings`, `IslandPack` and `IslandGraphHolder` take the variations and the lists.
- The preview override of the island function gets its API here. The panel of batch 5 calls it.
- JUnit: `FunctionNodeTypesTest` covers the new ids. `IslandPackTest` covers the two registry folders, the start JSON and the restore. The validator tests cover the three list problems and the missing carrier.
- GameTest: a variation edit reaches the built router and changes the blocks of a chunk.
- Done: the node editor opens the graph of the Basic variation and shows the island body node.

### Batch 4: screens

- `IslandsPresetScreen` gets the list rows. New screens: the variation screen and the list screen. New pure logic: the biome source draft and the list edit rules.
- JUnit: the draft of each biome source type and the type change. The range slider rules, the weight rule of the last positive entry and the id rule.
- `IslandsProbe` captures the list rows, the variation screen, each biome source form, the Empty variation screen and the list screen. Its Javadoc lists each state.
- The probe adds a variation, changes its fill, creates the world and checks the island pack of the new world.
- Done: the report holds each new PNG.

### Batch 5: panel and preview

- `ImGuiPreviewPanel` gets the tree, the forms, Solo, the color squares, the pins and the Export button without its action. `PreviewRenderer` gets the colors and the tooltip data. `PreviewBuilder` passes the overrides.
- `ImGuiNodeCanvas` lists the new types in the palette.
- JUnit: the panel edit rules that are pure logic, and the color table lookup.
- `IslandsProbe` captures the tree, an opened variation, Solo on and the color mode. It also captures the hover readout, each pin and the panel at its narrowest width.
- Done: the report holds each new PNG.

### Batch 6: export and design folder

- The Export action, the design folder reader of data generation and the shipped sidecar reader.
- JUnit: the export file set as a pure function of the graphs. The design reader on a good folder, a bad file and a missing entry.
- `IslandsProbe` presses Export and checks the files.
- Done: an exported Basic variation, copied to the design folder, passes `runData` and gives the same generated file.

### Batch 7: documents

- `CLAUDE.md`: the terms of section 3 replace the terms island type and island settings. The Islands sections state the new rules. One fact per bullet.
- The bullets that this plan makes false leave `CLAUDE.md`.
- The section "Open questions" gets the items of section 8.

## 7. Rules for every subagent

- The model is Opus. The build subagent gets this file, the section of its batch and the path of the branch before it.
- The check subagent gets the diff of the batch and this file. It does not get the report of the build subagent.
- The check subagent commits its fixes on top of the batch branch.
- Each subagent works in `.claude/worktrees/<topic>` on its branch. It does not commit to `main`. It does not push.
- The owner applies a batch with `git cherry-pick -n claude/<branch before>..claude/<branch>`. The first range starts at `main`.
- A subagent proposes no island shape and no shape default. The Basic variation is the island of 0.5.0.
- A subagent copies and ports no code of another mod.
- Each new or changed class gets the Javadoc line of the AI attribution procedure. The session gives each subagent the transcript path.
- Each new package gets its `package-info.java` with the three nonnull defaults.
- Components follow the Beanification rules of `CLAUDE.md`. Worldgen objects per chunk take their components through the constructor.
- No mixin enters without a comment that tells why no API can do the task. This plan needs no new mixin.
- The owner set the test process rule on 2026-11-14. It applies from batch 2 on.
- The build subagent compiles the four source sets. It runs only the JUnit classes of the packages that it touches, with `./gradlew test --tests '<package>*'`.
- The check subagent runs the full check list once, after its fixes: the compile of the four source sets and `./gradlew test`.
- The check subagent runs `./gradlew runGameTestServer` when the batch touches `world/`, data generation or a GameTest class.
- The check subagent runs `./gradlew runData` when the batch touches data generation or a registry codec.
- The check subagent runs the islands probe with `-Pmute` when the batch touches `client/`, `devtools/` or `CellGeometry`.
- The check subagent runs the settings probe when the batch touches a class of the pillar flow.
- The session reads the results and runs targeted tests. It does not run the full check list again.
- Before each commit: grep the diff for the words of the global code rule. Clear each hit or rewrite it.
- The report of a batch states each check with its result and each PNG path. It also states each reading that the subagent made.
- A question that this plan does not answer stops the batch. The session asks the owner.

## 8. Open after this plan

- The time limit of the island function per chunk. Batch 1 and batch 2 report the measured times. The owner then picks a limit or accepts the times.
- The capacity of the site cache of the lattice.
- The lakes, the inner cells, the bridges and the center structures stay later work.
