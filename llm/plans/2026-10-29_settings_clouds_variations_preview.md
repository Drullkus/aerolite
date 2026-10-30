# Plan: settings list, cloud height, shaft variations, preview mode

Date: 2026-10-29. Status: revision 4. Wave 1 done. C2 dispatched with weighted Wilson. Five work packages: A, B, C1, C2, D.

## Decisions

| Topic | Decision |
|---|---|
| Scroll widget | `ContainerObjectSelectionList`, one entry per row, section titles as entries. |
| Cloud height | Overworld only. Default Y -32. Slider from -64 to 320 in steps of 8. Own section "Clouds". |
| Cloud path to client | The Overworld gets an Aerolite dimension type with the effects id `aerolite:overworld`. The Nether and the End keep their vanilla types. A packet on login, respawn and dimension change carries the cloud height from the generator settings to the client. |
| Variation | One record: weight, maze bias, interior block, maze wall block, maze path block. |
| Variation lists | `AeroliteSettings` holds a map from dimension key to a list of variations. All three generators share one record. |
| Unlisted dimension | New boolean `shafts_in_unlisted_dimensions`, default false. False: the shaft step paints nothing in a dimension that is not in the map, and the noise settings of that dimension generate its terrain. True: that dimension uses the Overworld list. |
| Old saves | The mod is not released. The codec reads the old block fields only when that is trivial. Otherwise it drops them. |
| Pick | Every horizontal shaft and every column picks its own variation with a seeded random. A junction box takes the variation of its horizontal shaft. |
| Overworld defaults | Stone (stone, stone bricks, cobblestone) weight 7. Trial Chamber (tuff, tuff bricks, oxidized copper) weight 1. |
| Nether default | Netherrack, nether bricks, red nether bricks. Weight 1. |
| End default | End stone, end stone bricks, purpur block. Weight 1. |
| Maze bias | One slider per variation, 5% to 95% in steps of 5%. 50% is uniform. Below 50% favors ringwise paths. Above 50% favors lengthwise paths. |
| Bias sampler | Degree-weighted. Every axis edge has the weight p and every ring edge the weight 1-p. The walker picks one edge by weight. At 50% this is the uniform sampler. Chosen after the prototype comparison on 2026-10-29. |
| Sampler | Weighted Wilson's algorithm replaces Origin Shift in the mod. The loop-erased random walk steps by edge weight. The sample is exact at every slider value. No start tree, no step count. Chosen after the start tree comparison on 2026-10-29: the breadth-first start of Origin Shift left ribs under a strong bias. |
| Bias approval | The prototype renders the biased mazes first. The Java wiring waits for the owner's approval of the images. |
| Variation GUI | A sub-heading per dimension. Per variation: a weight slider 0 to 16, the bias slider, three block fields, a Remove button. An Add button per dimension. A dimension keeps at least one variation. |
| Block field | A text field. It parses a blockstate string such as `oxidized_copper` or `stone_bricks[...]`. While typing, a dropdown lists blocks whose id or name contains the text, with the item icon. |
| Preview | A Preview button in the preset screen opens a preview screen. World creation only. |
| Preview dimension | A dropdown in the panel picks the previewed dimension: Overworld, Nether or End. The scene uses the build height, the biome source, the variations and the dimension effects of that dimension. |
| Preview scene | Real blocks. The shaft step runs on a scratch region with the current settings. The vanilla block renderer bakes the blocks. |
| Preview region | A radius in chunks around the region center, as the render distance works in game. A radius slider from 2 to 32 chunks in steps of 1. Its start value is the render distance option of the client. |
| Preview seed | The seed of the world creation screen. An empty seed field gives a random seed that the panel shows. A Reseed button picks a new one. |
| Rebuild | Live. Every change starts a debounced rebuild on a worker thread. The panel shows a progress bar. |
| Backdrop | The sky and the fog of the previewed dimension, from its dimension effects and the biome at the region center. A floor grid at the bottom build limit. A thin outline on the region box. |
| Panel | An ImGui window pinned to the right strip of the window. A splitter on its left edge, 240 to 600 ImGui units, start 360. The width persists in the client config. |
| Panel content | Every section: placement, surface, clouds, variations with block fields and icons, seed, dimension, radius. |
| Framebuffer | A mixin on `Window` reports the width minus the panel width while the preview is open. Minecraft resizes its main target to it. |
| imgui-mc | Optional dependency. Without it the Preview button is hidden. |
| Client config | One value now, the panel width. The other client config values of the spec come with their features. |
| Dispatch | Opus agents in worktrees. Wave 1: A, B and C1 in parallel. Wave 2: C2 after the prototype approval, from main with A and B applied. Wave 3: D from main with C2 applied. |
| Merging | The owner applies each branch to main with its own cherry-pick command. No integration branch. |

## Findings that shape the work

- Veil 4.5.2 does not host ImGui. It calls the separate mod imgui-mc (`foundry.imguimc:imguimc-neoforge-1.21.1`, maven `https://maven.ryanhcode.dev/releases/`). `VeilRenderSystem.hasImGui()` tells if it is present.
- imgui-mc 2.0.0 shows a black window on this Mac from the first frame, with or without the Aerolite preview code: the frame reaches the back buffer and never the front buffer. imgui-mc 2.0.5 presents correctly. The build uses 2.0.5. Found on 2026-10-29 after package D.
- A client check that only looks for exceptions and a timeout cannot see a black window. A pixel read of the front buffer can.
- imgui-mc multiplies the ImGui font scale by the Minecraft GUI scale on every frame. It takes the display size from `glfwGetWindowSize` and the framebuffer scale from the main render target width divided by the window width. A reduced main target therefore shrinks the ImGui framebuffer scale. Package D corrects the scale in the ImGui pre-render hook.
- In 1.21.1 the client reads the cloud height from `DimensionSpecialEffects#getCloudHeight`, found by the `effects` id of the dimension type. The client has no generator settings. Hence the packet.
- Minecraft blits the main target with the width that `Window#getWidth` reports. With a reduced width the right strip of the window keeps stale pixels. The opaque ImGui panel covers that strip.
- The vanilla `FogRenderer` and `DimensionSpecialEffects` give the sky type, the fog color and the fog distances per dimension. The preview reuses their rules at the preview camera.

## Points for the owner to confirm with the plan

1. The radius slider runs from 2 to 32 chunks, the range of the vanilla render distance option.
2. With `shafts_in_unlisted_dimensions` false, the Aerolite generator still runs the noise step of that dimension. Only the shaft step is skipped.

## Package A: scrollable settings list

Branch `claude/settings-list`. Files: `AerolitePresetScreen`, new `SettingsList` in `us.drullk.aerolite.client`.

- `SettingsList extends ContainerObjectSelectionList<SettingsList.Entry>`. Row width 310, item height 24.
- `HeaderEntry` holds one `StringWidget`. `RowEntry` holds one widget of width 310 or two widgets of width 151 with a gap of 8.
- `addHeader(Component)` and `addRow(AbstractWidget...)` add entries.
- The screen puts the list into the contents of its `HeaderAndFooterLayout`. The footer keeps Done and Cancel.
- Check: compile, `./gradlew test`, `./gradlew runClient -Pmute`, open the screen, scroll with the wheel and with drag.

Hand-off: `git cherry-pick -n main..claude/settings-list`.

## Package B: cloud height

Branch `claude/cloud-height`. It starts from main in parallel with A. Its section uses the current `section()` helper. The owner resolves the screen conflict when B lands after A, or the C2 agent converts the section into list entries.

Data:
- `AeroliteSettings` gains `int cloudHeight`. Codec `Codec.intRange(-64, 320)`, field `cloud_height`, default -32. Add `withCloudHeight`.
- `AeroliteSettingsTest` covers the range and the default.

Screen:
- Section "Clouds", key `createWorld.customize.aerolite.clouds`. Slider "Cloud Height: %s", key `createWorld.customize.aerolite.cloud_height`, 48 steps of 8 from -64.

Dimension type:
- `AeroliteWorldgen` gains `ResourceKey<DimensionType> OVERWORLD_TYPE` = `aerolite:overworld`.
- `AeroliteWorldgenEntries` registers it in `Registries.DIMENSION_TYPE`: a copy of the vanilla overworld type with `effects = aerolite:overworld`. The preset Overworld stem uses it. Run `./gradlew runData` and commit `src/generated`.

Client:
- `AeroliteDimensionEffects extends DimensionSpecialEffects.OverworldEffects`. `getCloudHeight()` returns the value of a client component `CloudHeightHolder`. The default is `AeroliteSettings.DEFAULT.cloudHeight()`.
- `AeroliteClient` registers it in `RegisterDimensionSpecialEffectsEvent` under `aerolite:overworld`.

Network:
- `CloudHeightPayload(int cloudHeight)` implements `CustomPacketPayload`, id `aerolite:cloud_height`, `StreamCodec` with `ByteBufCodecs.VAR_INT`.
- A component `CloudHeightSync` registers the payload in `RegisterPayloadHandlersEvent` with `playToClient`. The client handler sets the holder.
- The same component listens to `PlayerLoggedInEvent`, `PlayerRespawnEvent` and `PlayerChangedDimensionEvent`. When the generator of the player level is an `AeroliteChunkGenerator`, it sends the payload to that player.

Spec: update the project `CLAUDE.md`. The Dimension section states the Overworld dimension type and its effects id. The settings table gains "Cloud height, -32". The slider list gains the cloud slider and its section.

Check: compile, test, runData, runClient -Pmute, create an Aerolite world, read the log for errors. The owner checks the cloud height by eye.

Hand-off: `git cherry-pick -n main..claude/cloud-height`.

## Package C1: maze bias prototype

Branch `claude/maze-bias-prototype`. Files under `prototypes/maze/` only. No Java.

- `originshift.py` gains `--bias P`, default 0.5. The Origin Shift step picks the axis class with chance P, else the ring class, then a uniform neighbor in the class. An empty class falls back to the other class. The breadth-first start tree does not change.
- `junction.py` gains the same `--bias P`, so the junction and column scenes render with it.
- Image names carry the bias: `band_<layout>_<sampler>_bias<P>.png`, `closeup/` likewise, `column_band_bias<P>.png`, `junction_*_bias<P>.png`.
- `--stats` gains `--bias P`. It prints the dead-end fraction, the corner pass fraction and the crossing counts per step count at that bias. The agent runs it at 0.5 and confirms that Origin Shift at `STEPS_PER_NODE` still matches Wilson. It runs it at 0.1 and 0.9 and reports the numbers.
- `README.md` documents the option, the images and the commands below.
- The agent renders the images for P in 0.1, 0.25, 0.5, 0.75, 0.9 and commits them.

Commands for the owner, from the repository root:

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.1
```

```bash
python3 prototypes/maze/originshift.py --seed 1 --chunks 24 --bias 0.9
```

```bash
python3 prototypes/maze/junction.py --seed 1 --cx 203 --cy 70 --bias 0.9
```

```bash
python3 prototypes/maze/junction.py --column-band --bias 0.9
```

```bash
python3 prototypes/maze/originshift.py --stats --bias 0.9
```

Check: every command above runs. Every image lands in `prototypes/maze/images/`.

Hand-off: `git cherry-pick -n main..claude/maze-bias-prototype`. The owner looks at the images and approves the sampler rule, or changes it. C2 waits for that.

## Package C2: shaft variations and maze bias in Java

Branch `claude/shaft-variations`. It starts from main after A and B are applied and after the C1 approval.

Data:
- New record `ShaftVariation(int weight, float bias, BlockState interior, BlockState wall, BlockState path)` in `us.drullk.aerolite.world`. Codec: `weight` non-negative int, `bias` float 0 to 1 default 0.5, three `BlockState.CODEC` fields.
- `AeroliteSettings` drops `interiorBlock`, `wallBlock` and `pathBlock`. It gains `Map<ResourceKey<Level>, List<ShaftVariation>> variations`, field `variations`, codec `Codec.unboundedMap(ResourceKey.codec(Registries.DIMENSION), ShaftVariation.CODEC.listOf())`. Every list has at least one variation with a positive weight.
- `AeroliteSettings` gains `boolean shaftsInUnlistedDimensions`, field `shafts_in_unlisted_dimensions`, default false.
- Defaults per the decisions table. The constants of the default variations live in `ShaftVariation`.
- `variations(ResourceKey<Level>)` returns the list of the key. For a key that is not in the map it returns the Overworld list when the boolean is true, else an empty list. An empty list means no shafts.
- Methods to add, remove and replace a variation return a new record.
- The old block fields: read them only when the codec change is trivial. Otherwise drop them.

Pick:
- `ShaftLayout` takes the `ResourceKey<Level>` instead of the dimension string. It gains `variation(Shaft)` and `variation(Column)`. The pick uses the `MixedRandomFactory` at the shaft axis and cell, or at the two cells of the column, and the weights.
- `AeroliteChunkGenerator#buildShafts` returns at once when the variation list of the level is empty.
- `ChunkPaint` uses the blocks of the variation of each shaft and column. A junction box, its patches and the path blocks on its patches use the variation of the horizontal shaft of that junction.

Sampler, as approved from the prototypes:
- `MazeGraph` records the class of each edge. `addEdge(a, b, boolean axis)`.
- A virtual node edge is an axis edge. A patch crossing is an axis edge. A patch edge along the shaft axis is an axis edge. A patch edge across it is a ring edge. On a column, lengthwise means along Y.
- `sampleTree(RandomSource, float bias)` is weighted Wilson's algorithm. An axis edge has the weight bias, a ring edge the weight 1 - bias. The loop-erased random walk from each node not yet in the tree steps by edge weight until it hits the tree. The root is a random node.
- Origin Shift, the breadth-first start tree and `STEPS_PER_NODE` leave the mod. The prototype keeps them for comparison.
- The bias codec clamps to 0.05 to 0.95. The slider runs from 5% to 95% in steps of 5%.
- `ShaftMaze` and `ShaftMazes` carry the bias. `ShaftLayout` passes the bias of the variation of each shaft and column.

Screen:
- Section "Shaft Variations", key `createWorld.customize.aerolite.shaft_variations`, as `SettingsList` entries. The agent also moves the Clouds section of B into list entries when the owner has not done it.
- A sub-heading per dimension with the dimension name. A row for the boolean as a cycle button, "Shafts in Unlisted Dimensions: Off".
- Per variation: a row with the weight slider "Weight: %s" (0 to 16) and the bias slider "Maze Bias: %s" with the suffix "(ringwise)", "(uniform)" or "(lengthwise)". A row with three `BlockField`s for interior, wall and path. A Remove button, disabled when the list has one variation. An Add button per dimension appends a copy of the first variation.
- `BlockField extends EditBox`. It parses with `BlockStateParser`. A bad text gives a red border and keeps the old state. On focus with text, a dropdown lists up to 8 blocks whose id or name contains the text, each with the item icon from `GuiGraphics#renderItem` and the name. A click or Enter picks one. The screen draws the open dropdown after the list, so it covers the rows below.

Spec: update the project `CLAUDE.md`. Glossary gains "Variation" and "Maze bias" and replaces the "Origin Shift" row with "Wilson's algorithm". The Maze bullets on Origin Shift, its start tree and its step count become bullets on weighted Wilson. The settings table replaces the three block rows with the variation lists, their defaults and the boolean. Remove the sentence about placeholder blocks. The Shafts section states the pick rule, the unlisted dimension rule and the class-first sampler. The slider list gains the section.

Tests:
- `ShaftMazeTest` and `ColumnMazeTest` run `TreeCheck` at bias 0.05, 0.5 and 0.95.
- A test for the weighted pick: deterministic for one seed, and a weight of 0 never picks.
- `AeroliteSettingsTest` covers the map codec, the boolean and the fallback list.
- `ShaftGameTests` check the blocks of the picked variation.

Check: compile, test, runData, runGameTestServer, runClient -Pmute.

Hand-off: `git cherry-pick -n main..claude/shaft-variations`.

## Package D: preview mode

Branch `claude/preview`. It starts from main after C2 is applied.

Dependency:
- `build.gradle`: `compileOnly` and `localRuntime` for `foundry.imguimc:imguimc-neoforge-1.21.1` at the version in `gradle.properties`, 2.0.5 or later. Add `includeGroup 'foundry.imguimc'` to the maven that serves it, with a comment. Run the dependency check with `--refresh-dependencies`.
- `neoforge.mods.toml`: optional dependency on `imguimc`, side CLIENT.
- Every ImGui class sits behind `VeilRenderSystem.hasImGui()`. The Preview button shows only when it is true.

Client config:
- A `ModConfigSpec` of type CLIENT with `panelWidth`, 240 to 600, default 360. Register it through a component.

Preview screen:
- `PreviewScreen extends Screen`. The preset screen opens it with a copy of the settings, the seed and a callback. Done sends the settings back. Cancel discards. The preset screen rebuilds its widgets from the returned settings.
- The seed comes from the seed field of `CreateWorldScreen`. An empty field gives a random seed. The panel shows the seed and has a Reseed button.
- The previewed dimension starts as the Overworld. The dropdown switches it. A switch rebuilds the scene.

Panel:
- `PreviewPanel` listens to the imgui-mc pre-render event through `ImGuiMCEvents.INSTANCE`. It draws only while a `PreviewScreen` is open.
- The window sits at the right edge: position (window width minus panel width, 0), size (panel width, window height). Flags: no move, no collapse, no title bar, opaque background. A splitter on the left edge drags the width between 240 and 600 ImGui units and writes the client config on release.
- Content in collapsing headers: Shaft Placement, Shaft Surface, Clouds, Shaft Variations, Preview. Each slider snaps to the same steps as the vanilla screen. The variation headers hold the boolean, weight, bias, three block fields with icon dropdowns, Remove and Add. The Preview header holds the dimension dropdown, the seed, Reseed, the radius slider from 2 to 32 chunks and a progress bar.
- Icons: a component `ItemIconAtlas` renders item icons on demand into a `TextureTarget` atlas with the vanilla item renderer. The panel shows a cell through `ImGuiMC.getColorTexture` and UVs.
- Framebuffer scale: in the pre-render hook the panel sets `io.setDisplayFramebufferScale` to the GLFW framebuffer size divided by the GLFW window size. imgui-mc derives it from the reduced main target otherwise.

Framebuffer width:
- A component `PreviewViewport` on the client holds `active` and the panel width in framebuffer pixels. The panel width in ImGui units times the framebuffer scale gives the pixels.
- `WindowMixin` in the main mixin set. `getWidth`, `getScreenWidth` and `getGuiScaledWidth` return the value minus the panel width in their unit while the viewport is active. The comment states that no API changes the reported window size. The mixin reaches the component through the Beanification context lookup.
- The screen calls `Minecraft#resizeDisplay` on open, on close and after a splitter drag.
- Verify on this Mac with the external display and without it: the panel is sharp and unsquished, the Minecraft GUI centers in the left part, the mouse hits the right widgets in both parts.

Builder:
- `PreviewBuilder` runs on one worker thread. A change bumps a generation counter and restarts after a debounce of 300 ms.
- For a radius R it builds `ProtoChunk`s for the chunks from -R to R on both axes, with the build height, the biome registry and the biome source of the previewed dimension from the world creation context. It makes a `ShaftLayout` for that dimension key and the seed and calls `ShaftPainter#paint` on every chunk. `ShaftPainter` and `ShaftMazes` come through constructor injection from `AeroliteClient`.
- It bakes every 16x16x16 section that holds blocks with `BlockRenderDispatcher#renderBatched` into a `BufferBuilder` over a `PreviewBlockGetter` with light 15 and the biome of the dimension at that position. Solid and cutout layers. The result is a list of `MeshData` per section.
- The render thread uploads `VertexBuffer`s when a generation completes and frees the old ones. The progress bar shows baked sections over total sections.

Renderer:
- Orbit camera around the region center at the middle of the build height. Drag rotates. Scroll zooms. Projection with the aspect of the reduced width.
- Sky: the `DimensionSpecialEffects` of the dimension type gives the sky type. NORMAL draws the sky color of the biome at the region center. END draws the end sky as the vanilla sky renderer does. NONE draws the fog color.
- Fog: the color and the distances follow the terrain fog rules of the vanilla `FogRenderer` for that dimension at the preview camera, with the radius as the render distance. The Nether gets its thick fog, the End its dark fog.
- Draw a grid at the bottom build limit and the region outline with `RenderType.lines()`.
- Draw the sections with the vanilla solid and cutout shaders, the block atlas on unit 0 and a white 16x16 texture as the lightmap on unit 2, because no level supplies one. Depth test on.

Spec: update the project `CLAUDE.md`. Dependencies gains imgui-mc. World creation menu gains the Preview button, the dimension dropdown, the radius slider and the panel. Config gains the panel width. Mixins gains the `Window` mixin and its reason.

Check: dependency check, compile, test, runClient -Pmute, open the preset screen, press Preview, switch the dimension, read the log. The owner verifies the panel on both displays.

Hand-off: `git cherry-pick -n main..claude/preview`.

## Rules every agent follows

- Global and project `CLAUDE.md` rules. STE writing. No production-process words in code.
- Every created or changed class gets the transcript Javadoc line with the path from `python3 llm/tools/claude_transcript_to_md.py --print-path`.
- Beanification: components for logic and state, constructor injection for per-chunk objects, no static state.
- `-Pmute` on every client run. Run every check of the package before reporting done.
- Each agent works in its worktree under `.claude/worktrees/<topic>` and commits to its branch. It removes the worktree when done and keeps the branch.
