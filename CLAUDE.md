# Aerolite

Aerolite is a NeoForge world preset. The world looks like broken reinforced concrete. Terrain islands are the concrete. Shafts are the rebar that holds them.

The global rules in `~/.claude/CLAUDE.md` apply. This file adds the rules for Aerolite.

## Glossary

| Term | Meaning |
|---|---|
| Preset | The Aerolite world preset. It replaces the generator of the Overworld, the Nether and the End. |
| Settings | The values that the player sets in the world creation menu. They live in the generator settings of the preset. |
| Shaft | A straight tube of blocks, 13x13 across, that runs along one axis. |
| Horizontal shaft | A shaft that runs along X or Z. It is infinite. |
| Column | A shaft that runs along Y. It spans the build height and is sheared at both ends. Columns bind horizontal shafts. |
| Junction | The 15x15x15 box where a column crosses a horizontal shaft. |
| Interior | The 11x11 solid core of a shaft. |
| Outer layer | The one-block skin around the interior. It holds the maze. |
| Maze grid | The outer layer blocks of a shaft, seen as a 2D grid that wraps around the four faces. |
| Ruling | One ring of nodes at an odd block along the shaft. A chunk holds 8 rulings. |
| Node | A maze grid block on a ruling at an even position around the shaft. Each face has 7 nodes from corner to corner, 5 of them interior. |
| Corner node | A node on a shaft corner. It has edges around the ring only. It carves only when a path crosses it from one face to the other. |
| Edge | A maze grid block between two neighbor nodes. An edge in the tree is a maze path. An edge outside the tree is a maze wall. |
| Maze wall | A maze grid block that keeps its outer layer block. |
| Maze path | A maze grid block with no outer layer block. The block under it is the maze path block. |
| Wilson's algorithm | The spanning tree sampler. A loop-erased random walk steps by edge weight until it hits the tree. |
| Axis edge | An edge along the shaft axis. On a column the axis is Y. |
| Ring edge | An edge around the shaft. |
| Variation | One set of weight, maze bias, interior block, maze wall block and maze path block. Each shaft and column picks one. |
| Maze bias | The weight of axis edges in the maze sampler, 5% to 95%. Ring edges take the rest. 50% is uniform. |
| Segment | One chunk length of a shaft. On a column it is one chunk section. Tree segments and connector segments alternate along the shaft. |
| Tree segment | A segment whose nodes form one spanning tree. It has no crossing of its own. |
| Connector segment | A segment whose nodes and two virtual nodes form one spanning tree. |
| Virtual node | A stand-in for one neighbor tree during sampling. It touches every interior node of that tree on the facing ruling. |
| Crossing | A tree edge at a virtual node. It is the maze path between the two sampled parts. |
| Junction segment | The segment that holds a junction. It spans the chunks with the nearest surviving ruling on each side of the box, two or three chunks. Its graph drops the box rulings and adds the shaft patch. |
| Patch | One of the two exposed faces of a junction, a 7x7 node grid. The shaft patch is part of the junction segment of the horizontal shaft. The column patch is part of the junction segment of the column. |
| Decay | Fractal Brownian simplex noise that removes maze walls or maze paths from a region of a shaft. |
| Erosion | Removal of shaft and junction box blocks where decay noise passes the erosion threshold. It works from the surface toward the core. |
| Shear | Noise that removes blocks from a column near one of its ends. |
| Slot | One 16-block band of the build height. Each slot belongs to X shafts or to Z shafts. |
| Seed | The block at the tip of a walk. It grows the tree. |
| Tree | A random walker tree. It roots on a shaft and walks into the air. |
| Hue | The blockstate property of a tree block, 0 to 15. |
| Cell | One 3D Voronoi polyhedron. |
| Generating layer | The one horizontal layer of cells that produces islands. |
| Island | The terrain in one cell of the generating layer. |
| Island type | Landmass, biosphere, lake bowl or donut. |
| Bridge | A walkable path between the islands of two neighbor cells. |
| Center structure | A vanilla structure whose start is at a cell center. |

## Project identity

| Item | Value |
|---|---|
| Mod ID | `aerolite` |
| Display name | Aerolite |
| Base package and maven group | `us.drullk.aerolite` |
| Language | Java only. Do not write Kotlin. |
| License | All Rights Reserved |
| Minecraft | 1.21.1 |
| Loader | NeoForge, with ModDevGradle. The version is in `gradle.properties`. |

The source still contains the MDK template (`com.example.examplemod`, mod ID `examplemod`). Replace the template with the values above.

## Dependencies

- Beanification: dependency injection. See [Dependency injection](#dependency-injection).
- Veil: the ImGui panel and custom block and entity rendering. See [Veil](#veil).
- imgui-mc: hosts ImGui for Veil. It is optional. See [imgui-mc](#imgui-mc).
- WorldEdit: an editor for the dev runs. See [WorldEdit](#worldedit).

### Dependency injection

Use Beanification (`tamaized:beanification`). Source: <https://github.com/Tamaized/Beanification/tree/1.21>.

Build setup:

- Use the latest `1.6.x` version from the Tamaized maven (`https://maven.tamaized.com/releases/`). Version 1.6 is for Minecraft 1.21.1.
- Do not use version 1.9 or later. Version 1.9 is for a later Minecraft.
- Do not use the `1.21.1-*` versions. They are old builds.
- Shade Beanification into the production jar with the Gradle Shadow plugin.
- Relocate `tamaized.beanification` to `us.drullk.aerolite.beanification`.
- Enable the `tamaized.beanification` Gradle plugin, so that `@Configurable` works.
- Keep `org.apache.groovy:groovy` on the buildscript classpath. The plugin needs it on Gradle 9.
- Add the `tests` and `test-sources` artifacts to the test source set.

Rules:

- Call `BeanContext.init(MODID)` in a static block of the `@Mod` class.
- Prefer a `@Component` to a static method. Put logic and state in components.
- Do not write a static field that holds state.
- Constants, codecs, registry keys and mixin members stay static.
- Make each component a public class with a public constructor. Beanification creates components through `Class.getConstructors()`.
- Write event listeners as instance methods of a component. A `@PostConstruct` method adds them to the bus.
- Give a component that only the client uses `dist = Dist.CLIENT`.
- A record cannot hold an `@Autowired` field. Put the logic that needs a component in the class that holds the component.
- Code that runs before `BeanContext.init` cannot use a component. The chunk status mixins run in the vanilla bootstrap.
- Declare each compat feature as an interface.
- Give each interface a `@Bean` static method that checks `ModList` and returns one implementation.
- Return a no-op implementation when the mod is absent.
- Do not annotate an optional-mod implementation with `@Component`. Create it only in the `@Bean` method.
- Use `@Configurable` and `@Autowired` fields in blocks, items and block entities.
- Use `@Configurable` and `@Autowired` fields in other long-lived objects that the game creates, such as the chunk generator.
- An object that worldgen creates per chunk or per segment takes its components through its constructor.
- `BeanContext.injectInto` is synchronized and scans the mod annotations on each call. So a `@Configurable` object costs a global lock at each construction.
- Do not use an `@Autowired` field in a constructor. Injection occurs at the end of the constructor.
- Use constructor injection when a constructor needs a bean.
- In JUnit tests, extend with `MockitoFixer` and `MockitoRunner` from `tamaized.beanification.junit`. Use Mockito `@Mock` fields.
- A JUnit test of pure logic creates real components with `new`.
- GameTest methods are static. A GameTest class gets its components through static `@Autowired` fields.
- Do not use `BeanContextJunitExtension` or `@MockBean`. JUnit runs in the game's module layer, and that extension is in a split package.

### WorldEdit

- The dev runs load WorldEdit. The code does not reference it. The production jar does not hold it.
- Get WorldEdit from the EngineHub maven. The version is in `gradle.properties`.
- EngineHub publishes one NeoForge artifact for all 1.21 patches, `worldedit-neoforge-mc1.21`.
- The WorldEdit mod jar does not hold the WorldEdit core.
- Put the mod jar in `localRuntime` with `transitive = false`.
- Put `worldedit-core` in `additionalRuntimeClasspath`. Then the game layer loads the core as a library.
- Exclude Guava, Gson, fastutil and Log4j from the core. Minecraft pins their versions, and the WorldEdit versions fail to resolve.

### imgui-mc

- imgui-mc (mod ID `imguimc`) hosts ImGui. Veil 4.5 does not host ImGui by itself.
- The preview and the F12 panel need imgui-mc.
- imgui-mc is an optional client dependency in `neoforge.mods.toml`.
- The build has imgui-mc in `compileOnly` and in `localRuntime` with `transitive = false`. The version is in `gradle.properties`.
- The mod jar of imgui-mc holds the ImGui binding and its natives as jar-in-jar libraries.
- Only classes in `us.drullk.aerolite.client.preview.imgui` reference ImGui or imgui-mc types.
- The bean method of `PreviewPanel` creates the ImGui panel only when imgui-mc is loaded. Else it returns a no-op panel.

### Veil

- Use Veil for the ImGui panel.
- Use Veil for custom block and entity rendering, for example the tree block.
- Do not use Veil for the sky. Each dimension keeps its vanilla sky.

## Mixins

- Use APIs, events and capabilities first.
- Write a mixin only when no API, event or capability can do the task.
- Add a comment to each mixin. The comment tells why no API can do the task.
- The chunk status mixins add the shafts status, because no API adds a chunk status.
- `WindowMixin` is a client mixin on `Window`. No API changes the window size that the game reports.
- While the preview is open, `WindowMixin` reports the window width without the panel width.
- `WindowMixin` reduces `getWidth` in framebuffer pixels, `getScreenWidth` in screen coordinates and `getGuiScaledWidth` in GUI units.
- A mixin gets no bean injection. `WindowMixin` calls `BeanContext.inject` after it finds an open preview screen.

## Package annotations

Put a `package-info.java` file in every package. Annotate each package with all three nonnull defaults:

```java
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package us.drullk.aerolite.example;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
```

Mark nullable values with `@Nullable`.

## Commands

| Task | Command |
|---|---|
| Compile | `./gradlew compileJava` |
| Start the client | `./gradlew runClient -Pmute` |
| Start the dedicated server | `./gradlew runServer` |
| Run GameTests | `./gradlew runGameTestServer` |
| Run JUnit tests | `./gradlew test` |
| Run data generation | `./gradlew runData` |
| Check dependency resolution | `./gradlew dependencies --configuration compileClasspath --no-configuration-cache --refresh-dependencies` |
| Check runtime dependency resolution | `./gradlew dependencies --configuration runtimeClasspath --no-configuration-cache --refresh-dependencies` |

In the dependency check output, search for `FAILED`. A `FAILED` line is a missing repository or a missing version.

Use `--no-configuration-cache` with the `dependencies` task. The configuration cache does not support that task with this build.

When no GameTests exist, `runGameTestServer` stops with `No test functions were given!`. This is expected.

## Source layout

- Production code is in `src/main`.
- Data generation code is in `src/datagen`. The `data` run uses it. It is not in the production jar.
- Tests (GameTests and JUnit) are in `src/test`. They are not in the production jar.
- Data generation writes to `src/generated/resources`. Commit the output.
- Override of the global rule: data generation also writes blockstates and block models. The tree block has 16 hue variants.
- Write textures by hand.

Data generation writes:

- Worldgen JSON: the preset, the noise settings, the density functions, the structure sets and the biome tags.
- Lang, loot tables, tags and recipes.
- Blockstates and block models.

## Dev tools

- The dev tools mod is in the `devtools` source set, `src/devtools`. Its mod ID is `aerolitedevtools`.
- The runs load it. The production jar does not hold it.
- It holds the mixin that keeps the game window from taking the desktop focus.
- `-Pmute` gives the client run its own game directory, `run-mute`.
- `-Pmute` sets the master volume to zero, turns the early loading window off and keeps the window focus off.

## Prototypes

The folder `prototypes/` at the repository root holds standalone prototypes, like the Relict project.

- A terrain shape is a signed distance function in `prototypes/<name>.frag`.
- Each `.frag` file has a port in `prototypes/shadertoy/<name>.shadertoy.glsl`. The port pastes into one Shadertoy "Image" tab with no channels.
- `prototypes/tools/` holds the GLSL validator.
- The maze is a standalone program in `prototypes/maze/`. Prototype a maze change there before you write any maze code in the mod. `prototypes/maze/README.md` lists the commands.
- Before you design or change an island shape, point the user at Earth photographs. See the global rule.

## Dimension

- The preset is in `#minecraft:normal`. It shows in the world type list without Alt.
- The preset gives the Aerolite generator to the Overworld, the Nether and the End.
- The Overworld uses the Aerolite dimension type `aerolite:overworld`.
- The Aerolite dimension type is a copy of the vanilla Overworld type with the effects id `aerolite:overworld`.
- The Nether and the End keep their vanilla dimension types.
- The Overworld keeps its sun, moon, day cycle and weather. The Overworld spans -64 to 320.
- The cloud height is a setting. The default is Y -32.
- The cloud height applies to the Overworld only.
- A packet carries the cloud height to the client on login, on respawn and on dimension change.
- The client effects of `aerolite:overworld` put the clouds at the cloud height of the last packet.
- Each dimension uses its own vanilla biome list. Overworld biomes in the Overworld, Nether biomes in the Nether, End biomes in the End.
- Each dimension has the same structure: shafts, islands, bridges and center structures.
- Each dimension uses the Aerolite generator, a subclass of the vanilla noise-based chunk generator. It carries the settings.
- The noise settings of each dimension make only air. The islands replace the void later.
- The void Overworld has a sea level of -64. A higher sea level makes the noise step fill the bottom with lava.
- The shafts are the chunk status `aerolite:shafts`, between the carvers and the features. Two mixins add the status and its step, because no API adds a chunk status.
- The features step waits for the shafts of its neighbor chunks. Features decorate the shafts.

## World creation menu

The player tweaks the settings in the world creation menu, like the superflat customize screen. The settings are:

| Setting | Default |
|---|---|
| Voronoi cell size | About 128 blocks |
| Island fill fraction, lower bound | 0.3 |
| Island fill fraction, upper bound | 0.9 |
| Island height band | Y 0 to 128 |
| Island type weights (landmass, biosphere, lake bowl, donut) | 8, 1, 1, 1 |
| Minimum shaft spacing | 128 blocks |
| Maximum shaft spacing | 384 blocks |
| Column chance | 0.25 |
| Maze decay scale | 256 blocks |
| Maze decay threshold | 0.5 |
| Shaft erosion threshold | 0.7 |
| Overworld variations | Stone (stone, stone bricks, cobblestone), weight 7, maze bias 50%. Trial chamber (tuff, tuff bricks, oxidized copper), weight 1, maze bias 50%. |
| Nether variations | Netherrack, nether bricks, red nether bricks. Weight 1, maze bias 50%. |
| End variations | End stone, end stone bricks, purpur block. Weight 1, maze bias 50%. |
| Shafts in unlisted dimensions | Off |
| Bridge chance | 0.5 |
| Allowed biome list | The vanilla biome list of the dimension |
| Cloud height | Y -32 |

- Each island picks its fill fraction at random between the two bounds.
- The island height band is the Y range of the island centers in the generating layer.
- The shaft spacing is the center distance of two neighbor horizontal shafts on one axis. It stays between the minimum and the maximum.
- The maze decay scale is the size of one decay region along a shaft.
- The variations are a map from dimension to a list of variations. Every list holds at least one variation with a positive weight.
- A variation lists its blocks in the sequence interior block, maze wall block, maze path block.
- The customize button opens the Aerolite screen when the preset is active. The screen edits every Aerolite dimension.
- The screen has seven sliders: the column chance, the minimum shaft spacing, the maximum shaft spacing, the maze decay scale, the maze decay threshold, the shaft erosion threshold and the cloud height.
- Each variation adds two sliders: the weight and the maze bias.
- The column chance slider runs from 0% to 100% in steps of 5%.
- The spacing sliders run from 32 to 1024 blocks in steps of 16.
- A spacing slider that passes the other spacing slider pushes it along.
- The decay scale slider steps through the powers of two from 32 to 1024.
- The decay threshold slider runs from 0% to 100% in steps of 5%. 100% is a threshold of 1.
- The erosion threshold slider runs from 5% to 100% in steps of 5%.
- The erosion threshold stays at least 5% above the decay threshold. A slider that passes the other pushes it along, so the decay threshold tops out at 95%.
- A save with a decay threshold at or above its erosion threshold loads with the erosion threshold raised to the decay threshold plus 5%.
- The cloud height slider is in the section Clouds, after the section Shaft Surface.
- The cloud height slider runs from Y -64 to Y 320 in steps of 8.
- The section Shaft Variations comes after the section Clouds.
- Its first row is a cycle button for the setting "Shafts in Unlisted Dimensions".
- Then each dimension has a header: Overworld, The Nether and The End.
- Each variation has a row with a weight slider and a maze bias slider.
- The weight slider runs from 0 to 16. The last positive weight of a dimension does not go below 1.
- The maze bias slider runs from 5% to 95% in steps of 5%.
- The maze bias label says "ringwise" below 50%, "uniform" at 50% and "lengthwise" above 50%.
- Each variation has a row with three block fields: the interior block, the maze wall block and the maze path block.
- A block field reads a block id or a block state string, such as `oxidized_copper` or `oak_log[axis=x]`.
- Bad text gives the block field a red border. The field keeps its last good block.
- While a block field has focus and text, a dropdown lists up to 8 blocks whose id or name holds the text. A click or Enter picks one.
- The dropdown shows the item icon and the name of each block. It lists no air block.
- Each variation has a Remove button. A dimension with one variation has its Remove button off.
- An Add button after the rows of each dimension appends a copy of the first variation.
- An add or a remove rebuilds the list and keeps the scroll position.
- The other settings have no slider yet. They live in the generator settings with their defaults.

### Preview

- The footer of the Aerolite screen has a Preview button between Done and Cancel.
- The Preview button shows only when ImGui loaded.
- The Preview button opens the preview screen with a copy of the settings.
- Done in the preview sends the copy back to the Aerolite screen. Cancel drops the copy.
- The preview shows the shafts of a square region of chunks around the origin.
- The preview paints the shafts with the shaft step code into scratch proto chunks. The vanilla block renderer bakes the blocks.
- An ImGui panel covers the right edge of the window. The game view fills the part left of the panel.
- A splitter on the left edge of the panel sets the panel width, from 240 to 600 ImGui units.
- The panel has the sections of the Aerolite screen, with the same slider steps and the same rules.
- A block field in the panel lists up to 8 matching blocks with their item icons.
- The Preview section of the panel has a dimension dropdown: Overworld, Nether, End. The Overworld comes first.
- The dimension gives the build height, the biome source, the variations, the sky and the fog of the preview.
- The seed is the seed text of the world creation screen. An empty text gives a random seed.
- The panel shows the seed. The Reseed button picks a new random seed.
- The radius slider runs from 2 to 32 chunks in steps of 1.
- The radius starts at the render distance option of the client.
- Every change starts a rebuild on a worker thread after 300 ms. A progress bar shows the baked sections.
- The fog follows the terrain fog of the dimension, with the radius as the render distance. The fog grows from the region center.
- A drag in the game view turns the camera around the region center. The scroll wheel zooms.

## Shafts

### Placement

- Along X, a seeded sequence of X positions exists. At each position, one Z-aligned horizontal shaft runs at one seeded height.
- Along Z, a seeded sequence of Z positions exists. At each position, one X-aligned horizontal shaft runs at one seeded height.
- A horizontal shaft keeps one height for its full length.
- Shafts have no elbows. A shaft never turns and never steps.
- An X shaft and a Z shaft never intersect.
- Every seeded choice of the shafts uses a random that mixes each coordinate into the seed.
- The vanilla Xoroshiro positional random puts the coordinate hash into the seed unmixed. Its first values correlate between neighbor coordinates.
- Each axis is a jittered grid. The cell size is the mean of the minimum and the maximum spacing. Each cell holds one shaft at a seeded place.
- A margin of half the minimum spacing in each cell keeps each neighbor distance between the minimum and the maximum.
- The smallest minimum spacing of 32 keeps at least 16 blocks between two shafts on the same axis.
- The build height is cut into slots of 16 blocks. The bottom slot and the top slot hold no horizontal shaft.
- The seed shuffles which of the other slots belong to X shafts and which to Z shafts.
- A horizontal shaft picks one slot of its axis. Inside the slot it picks an offset of 1 or 2, so at least one block of air separates it from a shaft in the next slot.
- A column can stand where an X shaft and a Z shaft cross in plan view. The column chance setting decides which crossings get one.
- A crossing gets no column unless at least one slot lies between the slots of its two shafts.
- Each column has a top end and a bottom end.
- The top end is uniform from 16 blocks above the highest junction box to the top build limit. The bottom end is uniform from the bottom build limit to 16 blocks below the lowest junction box.
- Those 16 blocks hold the shortest shear band and the 8-block margin.
- Shear thins each column toward its two ends. No column ends in a flat cut.
- Near a column end, a noise threshold removes blocks from the column. The threshold rises toward the end, so fewer blocks remain the closer they are to it.
- Each column end picks its shear band height at random, from 8 to 64 blocks.
- The shear band shrinks so at least 8 blocks of full column stay between the band and the outermost junction box.
- Shear does not touch a junction box.
- Where a column crosses a horizontal shaft, a junction joins them. The junction is a 15x15x15 box of the shaft interior block, centered on the crossing.
- Every horizontal shaft and every column picks one variation by weight. A weight of 0 never picks.
- A horizontal shaft picks with a random at its axis and cell. A column picks with a random at the cells of its two shafts.
- A junction box takes the variation of its horizontal shaft. So do its two patches and the maze path blocks on them.
- A dimension that is not in the variation map uses the Overworld list when "Shafts in Unlisted Dimensions" is on.
- When that setting is off, such a dimension gets no shafts. The noise settings of the dimension still make its terrain.
- Shafts pierce islands. Where a shaft and an island overlap, the shaft replaces the terrain.

### Cross section

- The outside is 13x13. The interior is 11x11 and solid. The interior uses the shaft interior block.
- The outer layer is one block thick. It holds the maze.

### Maze

- The maze is one spanning tree of the maze grid along the whole shaft. It has no loops.
- A ruling sits on every odd block along the shaft. On a ruling, nodes sit on even blocks around the shaft, so the four corners are nodes. Edges sit between neighbor nodes. Every other maze grid block is a maze wall.
- A corner node has no edge along the axis, so no path runs along a corner.
- After sampling, a corner node of degree one is dropped with its edge. So a two-block margin runs along every corner, with a notch where a path crosses the corner.
- Wilson's algorithm samples each tree from a seeded root. It samples the weighted spanning tree distribution exactly.
- In that distribution, the chance of a tree is proportional to the product of its edge weights.
- Every axis edge has the weight of the maze bias. Every ring edge has the weight of 1 minus the maze bias.
- At each step, the walk takes one edge of its node in proportion to the edge weight.
- An edge between two rulings is an axis edge. An edge between two nodes of one ruling is a ring edge.
- An edge to a virtual node is an axis edge. A crossing onto a patch is an axis edge.
- A patch edge along the shaft axis is an axis edge. A patch edge across the shaft axis is a ring edge.
- A maze bias above 50% favors maze paths along the shaft. A maze bias below 50% favors maze paths around the shaft.
- Each horizontal shaft and each column samples with the maze bias of its variation. A junction segment uses the maze bias of its own shaft.
- A horizontal shaft is cut into segments, aligned to chunks. Segments with an even index are tree segments. Segments with an odd index are connector segments.
- Each segment is a pure function of the world seed, the shaft and the segment index. The chunk generation sequence does not change the result.
- A connector segment samples its tree on its own nodes plus two virtual nodes. A tree edge at a virtual node becomes a crossing to that neighbor tree segment.
- The union of all segments is one tree. A chunk boundary gets several crossings, so no wall ring marks it.
- A chunk needs at most two segments: its own, which can be a junction segment, and the connector segment before it along the axis.
- A column is cut into segments of one chunk section, like a horizontal shaft. A column segment is a pure function of the world seed, the column and the section index.
- At a build limit, a segment gets no virtual node on the outer side.
- A junction cuts the maze grid of both shafts. The one-block rim around each shaft attachment sits on even positions, so it is wall.
- A junction exposes two patches, the faces that no shaft covers. The seed picks one patch for the horizontal shaft. The other patch serves the column.
- A junction segment replaces the chunks that hold the nearest surviving ruling on each side of the box. Its graph is those rulings minus the box rulings, plus the shaft patch nodes, plus a crossing from each interior patch border node onto the nearest ruling. One tree spans all of it, so no part strands.
- The junction segment keeps the chunk alternation. An end chunk with an odd index gives it a virtual node on that side.
- When the junction segments of two boxes share a chunk, one junction segment spans both boxes. It holds one shaft patch per box.
- The column patch is part of a column junction segment. Crossings run from its interior border nodes onto the nearest ruling above and below.
- A crossing onto a patch steps up one block, from the shaft outer layer onto the junction face.
- Each shaft is one tree over its full length. No tree touches another shaft. The maze has no loops.
- A maze wall keeps its outer layer block. That block is the maze wall block, at the 13x13 extent.
- A maze path has no outer layer block. The block under it, at the 11x11 extent, is the maze path block.
- Decay is one fractal Brownian simplex noise over the shaft. Far negative values keep only maze walls. Far positive values keep only maze paths. Central values keep both.
- The decay threshold t sets the regimes. Noise below -t keeps only maze walls. Noise above t keeps only maze paths.
- A maze wall or maze path block that decay removes becomes air. A removed path exposes the interior block.
- Decay acts on the 13x13 inside of each patch. On a patch, the box block that decay exposes becomes the maze path block.
- Erosion starts where the absolute decay noise passes the erosion threshold, at either end of the noise.
- The reach is the absolute noise, normalized from the erosion threshold to 1.
- Each block has an erosion resistance from 0 to 1. Erosion removes a block when the reach exceeds its resistance.
- In a shaft, the resistance rises over 5 layers from the outer layer inward. The 3x3 core has a resistance of 1, so it never vanishes.
- In a junction box, the box resistance rises over 6 layers from the box face inward. The 3x3x3 core has a resistance of 1.
- A box block takes the largest of the box resistance and the resistance of each shaft whose cross section holds it. So erosion never cuts a shaft at a junction.
- Erosion samples the noise at each block, so one side of a shaft can erode deeper than the other.
- Erosion runs after decay and before shear. An eroded block becomes air.
- The rim of a patch face stays wall. Decay does not touch it.
- The prototype renders the segment layouts and one junction from both sides. The user picked the connector layout and the junction segment from its images.
- The prototype keeps Origin Shift and its start trees for comparison. The mod uses only Wilson's algorithm.

## Walker Trees

- A tree roots on the outer layer of a shaft and walks outward into the air, like a chorus plant.
- The tree block is one block with the property `hue`, 0 to 15.
- The hue of a block is the walk step count from the root, wrapped to 16. One tree cycles through the full hue circle as it grows.
- At each step the walker continues in one direction. With a chance of 10 percent it also spawns a second branch.
- At each step the walk matures with a chance of its length divided by 32. A walk of 32 steps is always mature.
- A seed block sits at the tip of each walk, like a chorus flower. On a random tick it grows one step and leaves a tree block behind.
- A mature seed stops. A player breaks the seed and places it again to restart growth.
- The tree block has a block item. A placed tree block does not grow.
- World generation places mature trees.
- The game rule `walkerTreeGrowChance` is the percent chance that a seed grows on a random tick. The default is 20. Zero stops growth.
- Veil renders the tree block.

## Islands

### Voronoi

- Cells are 3D Voronoi polyhedra. The mean distance between cell centers is the cell size setting.
- Only the generating layer produces islands. The cells above and below it produce nothing.
- The cells above and below still shape the islands. Their faces push the island top up into hills and the island bottom down into beards.
- Each cell in the generating layer gets one island, one island type and one biome from the allowed biome list.
- The Voronoi cell gives the primitive solid of the island. Softening, warping and noise come after.
- The biome drives the shape of the island. Plains are flat. Mountains are tall, triangular and jagged.

### Density functions

- Register Aerolite density function types in Java: the Voronoi, the island primitive and the warp.
- Reference them from the noise settings JSON that data generation writes.
- Vanilla density functions do the rest.

### Island types

**Landmass.** The biome terrain on the Voronoi primitive.

**Donut.** A torus in any planar orientation, made of landmass terrain. The hole radius is a random fraction of the outer radius, from one quarter to two thirds.

**Biosphere.** Terrain inside a glass dome. Each biosphere is one permutation of dome shape, glass color and biome:

- Dome shapes: a sphere, a polyhedron, or a donut in horizontal orientation.
- A polyhedron is one of the five Platonic solids, or the Voronoi cell of the island.
- The glass shell is one block thick.
- A biome tag names the biomes that get tinted glass. Default members: the cave biomes, the Pale Garden, the Nether biomes and the End biomes. The Pale Garden does not exist in 1.21.1. Keep the entry for later versions.
- Every other biome picks from a weighted list: clear glass has weight 32, each of the 16 stained glasses has weight 1.
- The biome keeps its terrain characteristics inside the dome.
- A cave biome hangs terrain from the dome ceiling. It is not a glass top over a stone bottom.

**Lake bowl.** A bowl that holds water. Each lake bowl is one permutation of shape and orientation:

- Shapes: a polyhedron or a half blob. A polyhedron is one of the five Platonic solids, or the Voronoi cell of the island.
- Orientation: from flat to a tilt of 30 degrees.
- The water never spills. The water surface stays below the lowest point of the rim.

### Bridges

- A bridge joins the islands of two cells that share a face in the generating layer.
- Only a seeded subset of neighbor pairs gets a bridge. The bridge chance setting sets the chance.
- A bridge is a straight deck from island edge to island edge. Each bridge picks its shape and its material at random.

Shape:

- 50 percent: 3 blocks wide, no rail.
- 50 percent: 5 blocks wide, with a rail on both edges.
- The deck is lower slabs of the material. Under a rail block, a full block of the material replaces the slab.

Material, by dimension:

| Dimension | Chance | Deck | Rail |
|---|---|---|---|
| Overworld | 95% | Planks, weighted by wood | Fence of the same wood |
| Overworld | 4% | Cobblestone | Cobblestone wall |
| Overworld | 1% | Smooth stone | Stone brick wall |
| Nether | 80% | Crimson or warped planks, equal weight | Fence of the same wood |
| Nether | 15% | Nether bricks | Nether brick fence |
| Nether | 5% | Red nether bricks | Red nether brick wall |
| End | 95% | End stone, with end stone brick slabs for the deck | End stone brick wall |
| End | 5% | Purpur | End stone brick wall |

Overworld wood weights:

| Wood | Weight |
|---|---|
| Oak | 64 |
| Spruce, dark oak, mangrove | 16 |
| Birch, acacia | 8 |
| Jungle, cherry, bamboo | 4 |

### Center structures

- Every vanilla structure set of each dimension gets an Aerolite placement.
- The placement puts the structure start at a cell center, on the island. No structure generates in the air.
- An underground structure starts at the cell center in XZ, at a depth below the island surface. The depth is clamped so that the start stays inside the island solid.

## Config

Use two config files:

- Server config (synced to clients): one switch per structure set that turns center placement on or off.
- Client config: the ImGui panel (visible on start, panel width) and the tree block render (hue saturation, hue brightness, a switch for the Veil render path).
- The client config holds only the panel width now. The other client values come with their features.
- The panel width is in ImGui units, from 240 to 600. The default is 360.
- The splitter of the preview panel writes the panel width on release.

Tree growth is not a config value. It is the game rule `walkerTreeGrowChance`.

## ImGui panel

- The panel shows the tree block render settings of the client config and edits them live.
- F12 toggles the panel. The key mapping is `key.aerolite.toggle_panel`.

## Sounds

Aerolite adds no sounds. Blocks use vanilla sound types.

## Tests

- Write NeoForge GameTests for world generation results. Run them with `./gradlew runGameTestServer`.
- Write JUnit tests for pure logic: the Voronoi, the signed distance functions, the maze segments and the walker.
- Put all tests in `src/test`.
- The GameTest server run loads the test source set as part of the mod.
- GameTests use the template `aerolite:empty`, a 1x1x1 structure in the test resources.

## AI attribution procedure

The repository owner cites AI-generated work, so every session Claude works in must follow these steps.

1. Transcripts live at `llm/transcripts/transcript_{start date, UTC}_{HH-MM, UTC}_{session uuid}.md` and are generated only by the owner, never on Claude's own initiative.
2. Generate one with `python3 llm/tools/claude_transcript_to_md.py` from the repo root; it picks the most recent session for this project and names the file itself.
3. A session may span several commits, so re-run the same command whenever the owner asks for an update and commit the changed transcript alongside the code; Git tracks its history.
4. Every class Claude creates or modifies gets a class-level Javadoc line `<p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript-....md}.` using the repo-relative path (a modified class that already cites an earlier transcript keeps that line and gains one for the new session).
5. Find the path to cite before the transcript exists with `python3 llm/tools/claude_transcript_to_md.py --print-path`.
6. The desktop app sometimes resumes a conversation under a new session uuid; the tool detects this, renames the earlier transcript with `git mv`, and rewrites references in the project's text files, so check its output and stage the renamed file.
7. Do not add the owner's email, token usage, request IDs or Claude's reasoning to anything committed; the tool already strips them from transcripts.

## Transcript tool usage

- `python3 llm/tools/claude_transcript_to_md.py --list` lists the sessions recorded for this project with their uuid, start and end time, and opening prompt.
- `python3 llm/tools/claude_transcript_to_md.py` exports the latest session to `llm/transcripts/`.
- `--session <uuid prefix>` selects a specific session; `--file <path.jsonl>` exports a transcript file directly.
- `--print-path` prints the repo-relative output path without writing anything.
- `--include-results` embeds truncated tool output; `--max-chars N` changes the truncation limit (0 = unlimited); `-o <file>` or `-o -` overrides the destination.
- Sessions are read from `~/.claude/projects/<project path with slashes replaced by dashes>/*.jsonl`; the desktop app's `local_…` session handle is not a file name.

## Open questions

None at this time. When a new question appears, add it here and ask the user before you implement it.
