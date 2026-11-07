# Aerolite

Aerolite is a NeoForge world preset. The world looks like broken reinforced concrete. Terrain islands are the concrete. Pillars are the rebar that holds them.

The global rules in `~/.claude/CLAUDE.md` apply. This file adds the rules for Aerolite.

## Glossary

| Term | Meaning |
|---|---|
| Preset | The Aerolite world preset. It replaces the generator of the Overworld, the Nether and the End. |
| Settings | The values that the player sets in the world creation menu. The settings holder keeps them until the server of the new world claims them. |
| Settings holder | The client component that holds the settings of the open world creation flow. |
| Pending settings | The settings that the world creation flow hands to the integrated server of the new world. |
| Own variation list | The variation list of a dimension. A key of `ShaftRegistries.DIMENSION_LISTS` uses its value there, for example `aerolite:aether_pillars`. Any other dimension uses `aerolite:<dimension namespace>/<dimension path>_pillars`. |
| Prefab | An entry of `aerolite:variation_list`. The add row copies its weights. |
| Pillar | A straight beam of blocks, at most 13x13 across, that runs along one axis. Its shape sets its cross section. |
| Horizontal pillar | A pillar that runs along X or Z. It is infinite. |
| Column | A pillar that runs along Y. It spans the pillar range and is sheared at both ends. Columns bind horizontal pillars. |
| Pillar range | The Y range that holds the pillars of a dimension. Its bounds are multiples of 16. |
| Junction | The 15x15x15 box where a column crosses a horizontal pillar. |
| Interior | The solid core of a pillar inside its outer layer. The Pillar shape has an 11x11 interior. |
| Outer layer | The one-block skin around the interior. It holds the pattern. |
| Maze grid | The outer layer blocks of a pillar, seen as a 2D grid that wraps around each ring. |
| Ruling | One ring of nodes at an odd block along the pillar. A chunk holds 8 rulings. |
| Node | A maze grid block on a ruling at an even position around the pillar. Each face has 7 nodes from corner to corner, 5 of them interior. |
| Corner node | A node on a pillar corner. It has edges around the ring only. It carves only when a path crosses it from one face to the other. |
| Edge | A maze grid block between two neighbor nodes. An edge in the tree is a maze path. An edge outside the tree is a maze wall. |
| Maze wall | A maze grid block that keeps its outer layer block. In a band, it is a wall block. |
| Maze path | A maze grid block with no outer layer block. The block under it is the maze path block. In a band, it is a groove. On a flush face, the maze path block takes the place of the outer layer block. |
| Wilson's algorithm | The spanning tree sampler. A loop-erased random walk steps by edge weight until it hits the tree. |
| Axis edge | An edge along the pillar axis. On a column the axis is Y. |
| Ring edge | An edge around the pillar. |
| Variation | One set of maze bias, decay scale, decay threshold, erosion threshold, pattern, shape and six block state providers. Each pillar and column picks one. |
| Block state provider | A vanilla `BlockStateProvider`. It gives the block at each position. Each block field of a variation holds one. |
| Variation blocks | The six block state providers of a variation. They are the interior, junction, maze wall, maze path, surface path and trusses blocks. |
| Junction block | The block of the bezel of a junction. It defaults to the interior block. |
| Surface path block | The block of the Surface field. It sits flush at the wall layer. It defaults to the maze path block. |
| Trusses block | The block of the trusses of the Bridge shape. It defaults to the interior block. |
| Variation list | A map of variation id to weight. Its key sequence is the pick sequence. |
| Pillar data | The placement and the weighted variations of one dimension. |
| Data map | A NeoForge data map. It attaches a value to a registry entry by id. |
| World pillar pack | The world data pack `aerolite_shafts`. It holds the generator settings of a world as pillar data. |
| Maze bias | The weight of axis edges in the maze sampler, 5% to 95%. Ring edges take the rest. 50% is uniform. |
| Segment | One chunk length of a pillar. On a column it is one chunk section. Tree segments and connector segments alternate along the pillar. |
| Tree segment | A segment whose nodes form one spanning tree. It has no crossing of its own. |
| Connector segment | A segment whose nodes and two virtual nodes form one spanning tree. |
| Virtual node | A stand-in for one neighbor tree during sampling. It touches every interior node of that tree on the facing ruling. |
| Crossing | A tree edge at a virtual node. It is the maze path between the two sampled parts. |
| Junction segment | The segment that holds a junction. It spans the chunks with the nearest surviving ruling on each side of the box, two or three chunks. Its graph drops the box rulings and adds the pillar patch. |
| Bezel | The 3-block frame of an exposed junction face. It is the junction block. |
| Patch | One of the two exposed faces of a junction, a 5x5 node grid inside the bezel. The pillar patch is part of the junction segment of the horizontal pillar. The column patch is part of the junction segment of the column. |
| Decay | Fractal Brownian simplex noise that removes maze walls or maze paths from a region of a pillar. Each variation sets its own decay scale and thresholds. |
| Erosion | Removal of pillar and junction box blocks where decay noise passes the erosion threshold. It works from the surface toward the core. |
| Shear | Noise that removes blocks from a column near one of its ends. |
| Pattern | The groove layout on the outer layer of a pillar. A variation holds one pattern type and its options. |
| Pattern type | Mazes, Bricks, Tiles, Scales or Chaos. The key `type` of the pattern JSON holds its id. |
| Maze type | Uniform, Serpentine, Ribs, Lines or Gilbert. It is an option of the pattern type Mazes. |
| Pattern strength | The pull of a maze type toward its pattern edges, 0% to 100%. Any other edge takes the factor 1 minus the strength. |
| Seam | The root rule of a Ribs maze: random per segment, seeded per segment, or one ring position per pillar. |
| Line spacing | The node columns per groove of a Lines maze, 1 or 2. The menu label is "Spacing". |
| Node spacing | The cell spacing of a Chaos pattern, in blocks. |
| Groove | A band block that becomes a maze path. |
| Region | One brick, tile or scale of a band pattern. A surface path of a band pattern stays inside one region. |
| Surface path | A path from a groove over the face nodes of one region, with zero to two corners. It is surface path blocks, or grooves with Cutting on. |
| Loop share | The share of the cycle edges of the groove graph that stays. Allow loops off gives a loop share of 0%. |
| Cutting | The option that carves each surface path as grooves. |
| Path chance | The chance that one region takes a surface path. A Chaos pattern rolls it once per wall face. |
| Shape | The cross section of the pillars and columns of a variation: the Pillar shape, the I-beam or the Bridge shape. |
| Pillar shape | The 13x13 square shape with an 11x11 interior. It is the shape of 0.3. |
| I-beam | The shape of two flanges, 13 wide and 4 thick, joined by a web 5 thick and 5 tall. |
| Bridge shape | The shape of two plates with trusses between them. A Bridge pillar or a Bridge column has this shape. The word bridge alone names an island bridge. |
| Plate | One of the two slabs of the Bridge shape, 13 wide and 3 thick. Each plate is its own ring. |
| Truss | One diagonal row of trusses blocks between the two plates of the Bridge shape. |
| Anchor | A truss block that takes the place of a block of the inner plate layer. |
| Web plane | The plane of the web of an I-beam column, X or Z. A Bridge column takes the plane of its trusses from it. |
| Hole | The 11x11 passage of a horizontal Bridge pillar through a junction box. |
| Ring | One closed loop of outer layer blocks around a cross section. The Pillar shape and the I-beam have one ring. The Bridge shape has one ring per plate. |
| Ring description | The outer surface of one cross section of a shape: its rings, nodes, corner kinds, floors and crossing candidates. `RingShape` states the contract. |
| Flush face | A ring face whose grooves write the maze path block at the outer block, with no carve. The inner face of a plate is a flush face. |
| Band | The unwrapped outer layer of one ring over an axis range, ring position by axis position. Each band block is wall, groove, surface path or box. |
| Segment surface | The surface of one segment: one band per ring and one patch surface per patch face. A pattern fills it, and the paint reads it. |
| Patch surface | The 15x15 cells of one exposed face of a junction box. |
| Pattern binding | The component that makes the surface pattern of one pattern settings class. `PatternBindings` collects the bindings. |
| Slot | One 16-block band of the pillar range. Each slot belongs to X pillars or to Z pillars. |
| Seed | The block at the tip of a walk. It grows the tree. |
| Tree | A random walker tree. It roots on a pillar and walks into the air. |
| Hue | The blockstate property of a tree block, 0 to 15. |
| Cell | One 3D Voronoi polyhedron of the island lattice. |
| Generating layer | The row 0 of the island lattice. Only its cells hold islands. |
| Island | The cell of the generating layer, scaled about its site by its fill fraction. |
| Distortion | The simplex noise shift of the position before the island lattice lookup. The cell boundary distortion setting is its largest shift, in blocks. |
| Surface floor | The positive band of the initial density at the bottom of an island dimension. It gives every column a preliminary surface level. |
| Island type | A composition of density functions in the editor. Planned for 0.6.0. |
| Bridge | A walkable path between the islands of two neighbor cells. Planned for 0.6.0. |
| Center structure | A vanilla structure whose start is at a cell center. Planned for 0.6.0. |

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
- Dimension mods: other dimensions for the dev runs. See [Dimension mods](#dimension-mods).

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
- Every pattern binding is a component in its own subpackage of `us.drullk.aerolite.world.shaft.binding`. Each pattern has one subpackage.
- The package tree `us.drullk.aerolite.world.shaft.binding` holds only the bindings, `PatternBinding`, `PatternContext` and the collector `PatternBindings`.
- No test class lives in the package tree `us.drullk.aerolite.world.shaft.binding`.
- `PatternBindings` gets the bindings through a `@Directory` list.
- The directory scan of that list initializes every class of the package tree during mod construction.
- Settings without a binding paint as the Uniform maze. `PatternBindings` logs one line per settings class.

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

### Dimension mods

- The dev runs load the dimension mods. The code does not reference them. The production jar does not hold them.
- Each dimension mod is in `localRuntime` with `transitive = false`.
- Each companion mod is on its own `localRuntime` line.
- The versions and the CurseForge file ids are in `gradle.properties`.
- The Aether: Modrinth maven, `maven.modrinth:aether`. Its jar holds Accessories, Cumulus Menus and Nitrogen Internals. Its companions are owo-lib and Accessories from the Wisp Forest maven.
- The Aether team publishes to GitHub Packages. GitHub Packages needs credentials, so the build uses the Modrinth maven.
- The Accessories copy in the Aether jar fails a mixin on the recompiled dev Minecraft. The newer Accessories on its own line replaces it.
- Applied Energistics 2: NeoForged maven, `org.appliedenergistics:appliedenergistics2`. Its companion is GuideME, `org.appliedenergistics:guideme`.
- The Bumblezone: Resourceful Bees maven, `com.telepathicgrunt:the_bumblezone`. Its jar holds Resourceful Lib and Athena.
- Gaia Dimension: CurseMaven, `curse.maven:gaia-dimension-302529`. It has no maven of its own. It has no companion.
- Voidscape: CurseMaven, `curse.maven:voidscape-251730`. It has no companion.
- The Tamaized maven jar of Voidscape does not relocate Beanification. Its bean context collides with the Aerolite bean context, so the build uses the CurseForge jar.
- Twilight Forest: Tama's Maven, `team-twilight:twilightforest`, classifier `universal`. It has no companion.

### Veil

- Use Veil for the ImGui panel.
- Use Veil for custom block and entity rendering, for example the tree block.
- Do not use Veil for the sky. Each dimension keeps its vanilla sky.

## Mixins

- Use APIs, events and capabilities first.
- Write a mixin only when no API, event or capability can do the task.
- Add a comment to each mixin. The comment tells why no API can do the task.
- The chunk status mixins add the pillars status, because no API adds a chunk status.
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
- The dev tools mod is a `javafml` mod. Only the client creates its mod class.
- The devtools source set has a compile-only dependency on the output of the main source set. The production jar does not hold the devtools classes.
- The dev tools mod requires the mod `aerolite` on the client. It loads after it.
- The system property `aerolite.devtools.settingsProbe` turns on the settings probe, `SettingsProbe`.
- The build passes no JVM option to the client run. Set the probe property through `JAVA_TOOL_OPTIONS`.
- Start the probe with `JAVA_TOOL_OPTIONS=-Daerolite.devtools.settingsProbe=true ./gradlew runClient -Pmute`.
- The probe drives the create world screen, the pillars screen, the dimension screen, the provider screens and the preview.
- The probe captures each pattern type and each provider screen. It also captures each provider tab of the panel and the panel at its narrowest width.
- The probe also captures an I-beam scene and a Bridge scene with columns.
- The Javadoc of `SettingsProbe` lists each captured state in sequence.
- The probe saves one PNG of the window front buffer per state under `screenshots/` of the game directory. Then it stops the game.
- With `-Pmute`, the PNG files land in `run-mute/screenshots/`.
- The system property `aerolite.devtools.islandsProbe` turns on the islands probe, `IslandsProbe`. It saves the Islands screen, creates an Islands world and saves a side view and a top view of the first island.
- Every run loads the test source set as part of the mod `aerolite`.
- A stale test class in the IDE output breaks the mod load. After a branch change, run Build > Rebuild Project in IntelliJ IDEA.

## Prototypes

The folder `prototypes/` at the repository root holds standalone prototypes, like the Relict project.

- A terrain shape is a signed distance function in `prototypes/<name>.frag`.
- Each `.frag` file has a port in `prototypes/shadertoy/<name>.shadertoy.glsl`. The port pastes into one Shadertoy "Image" tab with no channels.
- `prototypes/tools/` holds the GLSL validator.
- The maze is a standalone program in `prototypes/maze/`. Prototype a maze change there before you write any maze code in the mod. `prototypes/maze/README.md` lists the commands.
- The chunk paint of `junction.py` paints the junction scene chunk by chunk, like the mod. `--chunk-stats` counts the exposed interior blocks of each paint rule.
- `junction.py --merged-stats` is the merged junction segment test. `MergedJunctionTest` is its port.
- `prototypes/patterns/` holds the shared surface path module `surfacepaths.py` and the Bricks prototype `bricks.py`.
- `prototypes/tiles/` holds the Tiles prototype `tiles.py` and the Scales prototype `scales.py`.
- `prototypes/chaos/` holds the Chaos prototype `chaos.py`.
- `prototypes/shapes/` holds the three.js page of the shapes, their columns and the junction link. Open its `index.html` from the file system.
- `prototypes/ui/` holds the HTML mocks of the ImGui panel. `provider-fields-d.html` is option D, the provider editor of the panel.
- `tiles.py`, `scales.py` and `chaos.py` import `prototypes/patterns/surfacepaths.py` read-only. A pattern prototype does not change that module.
- The README of each prototype folder holds the exact geometry of its patterns or shapes.
- The Java package `us.drullk.aerolite.world.shaft.surface.path` is the port of `surfacepaths.py`.
- The port fixes the three gaps that the pattern prototypes found in `surfacepaths.py`.
- The segment sampler of the port handles several components per chunk.
- The groove graph of the port splits each edge that spans more than one chunk.
- The fit check of the port knows the orphan rule and the face width rule.
- Before you design or change an island shape, point the user at Earth photographs. See the global rule.

## Dimension

- The preset is "Aerolite Void". Its key is `aerolite:void`. Its lang key is `generator.aerolite.void`.
- The preset is in `#minecraft:normal`. It shows in the world type list without Alt.
- The Islands preset `aerolite:islands` is the second preset. See [Islands](#islands).
- The preset gives a vanilla noise generator to the Overworld, the Nether and the End. Each generator uses the Aerolite noise settings of its dimension.
- A dedicated server needs `level-type=aerolite:void` in `server.properties`.
- The old key `aerolite:aerolite` gives the vanilla default preset. The server logs "Failed to parse level-type aerolite:aerolite, defaulting to minecraft:normal".
- `DeferredRegister#addAlias` does not reach world presets. `RegisterEvent` fires only for the static registries of `BuiltInRegistries.REGISTRY`.
- `RegistryDataLoader` loads the world presets into a new registry at each load. So no alias applies to them.
- A saved world does not store the preset key.
- The Overworld uses the Aerolite dimension type `aerolite:overworld`.
- The Aerolite dimension type is a copy of the vanilla Overworld type with the effects id `aerolite:overworld`.
- The Nether and the End keep their vanilla dimension types.
- The Overworld keeps its sun, moon, day cycle and weather. The Overworld spans -64 to 320.
- The cloud height of a dimension type is the value of the data map `aerolite:cloud_height`.
- The cloud height slider of the Aerolite Void screen sets the cloud height of the holder settings.
- The hand-over gives that cloud height to each level of the type `aerolite:overworld` in the first session of a new world.
- The world pillar pack holds that cloud height as the `aerolite:cloud_height` value of `aerolite:overworld`. Later sessions read it from the data map.
- Only the dimension type `aerolite:overworld` ships a cloud height, Y -32.
- The packet carries the cloud height of the pillar data of the player's level. A level without a cloud height sends no packet.
- A packet carries the cloud height to the client on login, on respawn and on dimension change.
- The client effects of `aerolite:overworld` put the clouds at the cloud height of the last packet.
- Each dimension uses its own vanilla biome source. Overworld biomes in the Overworld, Nether biomes in the Nether, End biomes in the End.
- A biome that the player picks in the Aerolite Void screen replaces the biome source of its dimension with a fixed biome source.
- Each dimension has the same structure: pillars, islands, bridges and center structures.
- The generator of a new world carries no pillar settings.
- The Aerolite generator `aerolite:aerolite` stays registered for the worlds of 0.2. Its field `aerolite` carries their settings.
- The pillars step does not read the generator. It paints any level with pillar data, whatever its generator.
- The noise settings of each dimension make only air. The islands replace the void later.
- The void Overworld has a sea level of -64. A higher sea level makes the noise step fill the bottom with lava.
- The pillars are the chunk status `aerolite:shafts`, between the carvers and the features. Two mixins add the status and its step, because no API adds a chunk status.
- The features step waits for the pillars of its neighbor chunks. Features decorate the pillars.

## World creation menu

The player sets the pillar settings in the World tab of the create world screen. The world type does not matter.

### Settings

- The cloud height is one setting of the whole preset. It is not a pillar placement value.
- Each dimension has its own pillar settings: one pillar placement and one variation list.
- The pillar placement holds the minimum pillar spacing, the maximum pillar spacing, the column chance and the pillar range.
- Each variation holds its own decay scale, decay threshold, erosion threshold, pattern and shape.

| Setting | Scope | Default |
|---|---|---|
| Cell boundary distortion | Preset | 12 blocks |
| Voronoi cell size | Preset | 256 blocks across, 128 blocks tall |
| Island fill fraction, lower bound | Preset | 0.3 |
| Island fill fraction, upper bound | Preset | 0.9 |
| Island height band | Preset | Y 0 to 128 |
| Island type weights (landmass, biosphere, lake bowl, donut) | Preset | 8, 1, 1, 1 |
| Bridge chance | Preset | 0.5 |
| Allowed biome list | Preset | The vanilla biome list of the dimension |
| Cloud height | Preset | Y -32 |
| Minimum pillar spacing | Dimension | The placement entry of the dimension, else `aerolite:default` |
| Maximum pillar spacing | Dimension | The placement entry of the dimension, else `aerolite:default` |
| Column chance | Dimension | The placement entry of the dimension, else `aerolite:default` |
| Pillar range | Dimension | The placement entry of the dimension, else `aerolite:default`. Only the Nether ships a range of its own, Y 0 to Y 128. |
| Variation list | Dimension | The own variation list of the dimension, for example `aerolite:overworld_pillars`. The classes in `datagen/shaft/` hold the shipped lists. |
| Maze decay scale | Variation | 64 blocks |
| Maze decay threshold | Variation | 0.35 |
| Pillar erosion threshold | Variation | 0.5 |
| Junction block | Variation | The interior block of the variation |
| Pattern type | Variation | Mazes |
| Maze type | Variation | Uniform |
| Pattern strength | Variation | 0% |
| Seam | Variation | Random per segment |
| Line spacing | Variation | 1 |
| Brick size | Variation | Large |
| Tile size | Variation | Large |
| Tile stagger | Variation | None |
| Encased | Variation | Off |
| Paths over tails | Variation | Off |
| Chaos cells | Variation | Voronoi |
| Node spacing | Variation | 6 blocks |
| Jitter | Variation | 50% |
| Corner radius | Variation | 1 block |
| Allow loops | Variation | On |
| Loop share | Variation | 50% |
| Surface paths | Variation | Off |
| Cutting | Variation | Off |
| Path chance | Variation | 75% |
| Shape | Variation | The Pillar shape |
| Surface path block | Variation | The maze path block of the variation |
| Trusses block | Variation | The interior block of the variation |

- Each island picks its fill fraction at random between the two bounds.
- The island height band is the Y range of the island centers in the generating layer.
- The pillar spacing is the center distance of two neighbor horizontal pillars on one axis. It stays between the minimum and the maximum.
- The maze decay scale is the size of one decay region along a pillar.
- The pillar range of a placement is the field `min_y` and the exclusive field `max_y`.
- A placement without these fields spans the build height. So the pillars of an old save stay.
- The constructor of `ShaftPlacement` snaps each bound to the nearest multiple of 16.
- The top stays at least 64 blocks above the bottom. So each axis keeps at least one slot.
- The range `ShaftPlacement.LOWEST_Y` to `ShaftPlacement.HIGHEST_Y` spans the build height of every dimension.
- `ShaftLayout` clamps the range to the build limits of the level.
- A range outside the build limits holds no pillars.
- The dimension settings are a map from dimension to pillar placement and variation list.
- Every variation list holds at least one variation with a positive weight.
- A dimension without an entry in the map gets no pillars.
- The records in `src/main/java/us/drullk/aerolite/world/pattern/` hold the defaults and the bounds of the pattern options.
- Bricks, Tiles, Scales and Chaos share the options Allow loops, Loop share, Surface paths, Cutting and Path chance. `SurfacePathSettings` holds them.
- The maze type, the pattern strength, the seam and the line spacing are options of Mazes.
- Paths over tails is an option of Scales.
- The maze bias stays a field of the variation. A Lines maze does not use it.
- Only the Bridge shape uses the trusses block.
- A variation lists its blocks in this sequence: interior, junction, maze wall, maze path, surface path and trusses block.
- A variation without a junction block takes its interior block as its junction block.
- A variation without a surface path block takes its maze path block as its surface path block.
- A variation without a trusses block takes its interior block as its trusses block.
- A variation of a 0.3 save has no pattern, no shape, no surface path block and no trusses block. It loads with the defaults of the table.
- So the pillars of a 0.3 save stay the pillars of 0.3.
- A block field of a 0.3 save holds a bare block state. It loads as a simple provider.
- A save writes the layout of 0.2.0: the cloud height and the dimension map.
- A save with a dimension map loads that map. It ignores the top-level fields of 0.1.0.
- A 0.1.0 save holds the placement and the decay values at the top level, next to a map of variation lists.
- A 0.1.0 save loads with its top-level placement in every dimension of its map.
- In a 0.1.0 save, a variation without its own decay values takes the top-level decay values.
- A 0.1.0 save without a variation map loads the default dimensions with its top-level values.
- A 0.1.0 save without a variation map reads the shipped lists, so it parses only with registry ops.
- A variation with a decay threshold at or above its erosion threshold loads with the erosion threshold raised to the decay threshold plus 5%.
- The cell boundary distortion and the cloud height have sliders. The other preset settings have no slider and no storage yet.

### Settings holder

- The client component `ShaftSettingsHolder` holds the settings of the open world creation flow.
- A create world screen that the holder does not track starts a new flow.
- While the common config switch `applyDefaultPillars` is on, a new flow starts with the default pillars. See [Default pillars](#default-pillars).
- While that switch is off, a new flow starts with blank settings.
- Blank settings hold no dimension and the default cloud height.
- The pillars screen, the dimension screen and the preview read and write the holder. No screen reads or writes the generator.
- The title screen and the world list end the flow. When one of them opens, the holder resets and clears the pending settings.
- Cancel in the create world screen returns to one of those screens. So a cancelled flow leaves blank settings and no pending settings.

### Default pillars

- The default pillars of a world are the result of the Defaults button on blank settings.
- The common config switch `applyDefaultPillars` gives each new world the default pillars. It is on by default.
- The switch applies to each world type.
- In a new flow, the pillars screen and the preview show the default pillars from the start.
- The player can change or remove the default pillars. A player who removes every dimension gets blank settings, so the world gets no pillars.
- The Defaults button stays. It adds the dimensions that the player removed or that a data pack change added.
- The recreate action of the world list also starts a new flow. So a recreated world also starts with the default pillars.
- On a dedicated server, `DedicatedDefaultPillars` offers the default pillars to a new world at `ServerAboutToStartEvent`.
- That offer holds each level stem of the world without an Aerolite generator. Its cloud height is the data map value of `aerolite:overworld`.
- An old world keeps its pillars. The switch acts only on a new world.

### Hand-over to the server

- `PendingShaftSettings` is a common component. The client offers settings to it. The server claims them.
- The flow opens the screen "Preparing world data" when it creates the world. That screen hands the holder settings to the pending settings.
- Blank settings are not handed over. So a world created with blank settings gets no pillars and no world pillar pack.
- The hand-over happens before the integrated server starts.
- The server claims the pending settings at `ServerAboutToStartEvent`, before it loads the levels.
- The server claims them only for a new world. A world is new while its Overworld data is not initialized.
- The server posts the Overworld load event before it sets the spawn and the initialized flag. It posts the other level load events after.
- So the server claims once at its start, not at each level load.
- An old world drops the pending settings. It claims nothing.
- The server stop clears the pending and the claimed settings.
- A dedicated server gets its offer from `DedicatedDefaultPillars`. With the switch off, its pending settings stay empty.
- The GameTest server gets no offer.
- The level binding uses the claimed settings. See [Level binding](#level-binding).

### World tab row

- The World tab has the row "Amazing Pillars" under the Bonus Chest row.
- The row holds the label "Amazing Pillars" and the button Settings, from left to right.
- The row sits one switch row under the Bonus Chest row, 4 units below the Bonus Chest switch.
- The label starts at the left edge of the Bonus Chest label. The Settings button ends at the right edge of the Bonus Chest switch.
- The Settings button is 20 high and fits its text.
- `WorldTabRow` adds the widgets in `ScreenEvent.Init.Post` of the create world screen.
- `WorldTabRow` places the widgets on each `ScreenEvent.Render.Pre` from the position of the Bonus Chest label and switch.
- The row shows only while the screen holds the Bonus Chest label. The vanilla tab manager adds that label only for the World tab.
- The vanilla grid does not hold the row. So the vanilla rows keep their vanilla positions, and no mixin is necessary.
- The Customize button of the preset opens the Aerolite Void screen. See [Aerolite Void](#aerolite-void).
- Settings opens the pillars screen with the holder.
- The row holds no Defaults button. The Defaults button is in the header of the pillars screen.
- The own variation list of the Overworld is `aerolite:overworld_pillars`.
- The own variation list of the Nether is `aerolite:nether_pillars`.
- The own variation list of the End is `aerolite:end_pillars`.
- The own variation list of each modded dimension of `ShaftRegistries.DIMENSION_LISTS` is its value there, for example `aerolite:aether_pillars`.
- The own variation list of any other dimension is `aerolite:<dimension namespace>/<dimension path>_pillars`, for example `aerolite:mypack/ruins_pillars`.
- A dimension that the Defaults button or the Add button adds takes the placement entry under its id, else `aerolite:default`.

### Aerolite Void

- `RegisterPresetEditorsEvent` gives the preset `aerolite:void` its customize screen, the Aerolite Void screen.
- The Aerolite Void screen has the title "Aerolite Void". The footer holds Done and Cancel.
- The screen edits a copy of the biome choice of each preset dimension and a copy of the cloud height of the holder settings.
- Done applies the biome choices to the dimensions of the world creation state, like the vanilla single biome screen.
- Done writes the cloud height to the holder settings. Cancel and Escape drop both copies.
- The Aerolite Void screen is the only screen that writes the generators. It writes only their biome sources.
- The list holds one row per preset dimension: the Overworld, the Nether and the End, in that sequence.
- A dimension row holds the dimension name on the left, then the biome name or "Vanilla biomes".
- The Biome button and the Reset button sit at the right end of the row, in that sequence. Each button is 20 high and fits its text.
- The narration of each row button is its text and the dimension name.
- The Biome button opens the vanilla single biome screen for that dimension.
- The single biome screen preselects the first biome of the dimension's current biome source.
- The single biome screen reads that source from the Overworld of its creation context. So the Aerolite Void screen gives it a context whose Overworld is the edited dimension.
- Done in the single biome screen gives the dimension a fixed biome source of the picked biome. Cancel keeps the choice.
- A dimension keeps its vanilla biome source until the player picks a biome. The Overworld and the Nether use a multi-noise source. The End uses the End source.
- The Reset button gives the dimension the biome source of the preset again. It is off while the dimension has its vanilla biome source.
- A reset rebuilds the list and keeps the scroll position.
- A rebuilt dimension keeps its dimension type and the noise settings of its noise generator.
- A dimension without a noise generator takes the noise settings of the preset.
- The component `VoidPresetBiomes` builds the dimensions. It takes the preset key, so the Islands screen uses it too. A GameTest checks it.
- Below the rows, the header Clouds holds the cloud height slider.
- The cloud height slider runs from Y -64 to Y 320 in steps of 8. Y 100 is not a step.
- A change of the world type gives the dimensions of the new preset. So it drops the biome choices.

### Dimension candidates

- The dimensions of the world are the level stems of the data packs and the dimensions of the world type.
- The worldgen load context of the creation context holds no level stem registry.
- So the data pack stems come from `WorldCreationContext#datapackDimensions`.
- A stem of the world type replaces a data pack stem with the same key.
- The add row, the add combo of the panel and the preview dimension list show every dimension of the world.
- So data pack dimensions such as `twilightforest:twilight_forest` and `aether:the_aether` show.
- The Overworld, the Nether and the End come first. The others follow in the sort sequence of `ResourceLocation`: path first, then namespace.
- A vanilla dimension reads its name from `createWorld.customize.aerolite.dimension.<path>`.
- A dimension other than the three vanilla dimensions reads its name from `dimension.<namespace>.<path>`, for example `dimension.twilightforest.twilight_forest`.
- A dimension without that translation shows its id path as its name.

### Pillars screen

- The pillars screen has the title "Amazing Pillars" and a footer.
- The pillars screen edits a copy of the holder settings. Done writes the copy to the holder. Cancel and Escape drop it.
- Its list holds the add row first, then one row per configured dimension.
- The add row holds the dimension dropdown, the list dropdown and the Add button, in that sequence.
- The dimension dropdown lists every dimension of the world that has no dimension settings.
- The list dropdown lists "No prefab" first, then every prefab by its name.
- A prefab reads its name from `pillar_variations.<namespace>.<path>`, for example `pillar_variations.aerolite.overworld_pillars`.
- A prefab without that translation shows its full id as its name, for example `mypack:ruins`.
- The shipped prefabs read "Overworld Pillars", "Nether Pillars" and "End Pillars".
- The lists follow the sort sequence of `ResourceLocation`: path first, then namespace.
- The list dropdown reads the variation lists from the worldgen registries of the creation context.
- When the chosen dimension has its own variation list, the list dropdown preselects that list. Else it preselects "No prefab".
- A pick in the dimension dropdown preselects the list again.
- The tooltip of the list dropdown is "Prefab Pillars to add in %s". The %s is the name of the dimension that the dimension dropdown shows.
- When no dimension is left to add, the %s of the tooltip is the word "Dimension".
- A pick in the dimension dropdown changes the tooltip of the list dropdown.
- An open dropdown list hides the tooltips of the screen.
- A click on a dropdown opens its list. A pick closes the list.
- Add with a list adds the chosen dimension with the weights of that list.
- Add with "No prefab" adds the chosen dimension with one stone variation, `aerolite:stone`, with weight 1.
- When no dimension is left to add, both dropdowns and the Add button are off. The dimension dropdown reads "All dimensions configured".
- A dimension row holds the dimension name on the left. An arrow button and a minus button sit at the right end, in that sequence.
- The arrow button is 20x20. It draws the server list sprite `server_list/join`, and `server_list/join_highlighted` on hover.
- The arrow sprite draws at its own size of 32 pixels, centered on the arrow.
- The arrow button opens the dimension screen. Its narration is "Configure" and the dimension name.
- The minus button is 20x20. It removes the dimension and its settings.
- An add or a remove of a dimension rebuilds the list and keeps the scroll position.
- After a remove, the dimension dropdown shows the removed dimension.
- A return from the dimension screen or the preview rebuilds the list.
- The header keeps its centered title.
- A Copy button, a Paste button and a Defaults button sit at the right end of the header row, in that sequence.
- Each header button is 20 high and fits its text.
- A gap of 4 separates the header buttons.
- The Defaults button ends 8 units from the right window edge.
- The tooltip of Copy is "Copy to Clipboard". The tooltip of Paste is "Paste from Clipboard".
- The tooltip of Defaults is "Replace empty settings with Amazing Pillar defaults".
- Defaults is always on.
- Defaults adds every dimension of the world that has its own variation list and no dimension settings, with that list.
- Defaults keeps each configured dimension as it is.
- Defaults keeps the cloud height.
- Defaults acts on the copy of the settings of the pillars screen. It rebuilds the list and keeps the scroll position.
- Copy writes the settings to the system clipboard as one line of JSON. Only the settings codec knows the fields.
- Copy and Paste use registry ops over the worldgen registries of the creation context.
- Paste parses the clipboard at once. A good parse replaces the settings and rebuilds the list.
- A bad parse shows a system toast with the title "Paste failed" and the parse error. The settings stay.
- The dimension screen has no Copy button and no Paste button for the whole settings.

### Dimension screen

- The dimension screen edits a copy of the settings.
- Its title is "Amazing Pillars: " and the dimension name. The title shows below the tab bar.
- A tab bar at the top holds two tabs: Pillar Placement and Pillar Variations.
- The footer holds Done and Cancel.
- Done writes the edited settings back to the pillars screen. Cancel and Escape drop them.
- The tab Pillar Placement holds the column chance slider, then the two spacing sliders in one row, then the two range sliders in one row.
- The tab Pillar Variations holds the rows of each variation, then the Add Variation button.
- The rows of a variation are, from top to bottom: the slider rows, the pattern rows and the shape row. The heading "Blocks", one row per block field and the button row follow.
- The slider rows are the weight and maze bias row, then the decay scale row. The decay threshold and erosion threshold row follows.
- A Lines maze has no maze bias slider. Its weight slider takes the left half of the row.
- The pattern rows and the shape row hold two widgets per row. A lone widget takes the left half of its row.
- The first pattern row holds the Pattern combo and the first option of the pattern type.
- A Mazes pattern puts the Type combo in the first row.
- A Serpentine maze and a Gilbert maze add a row with the Strength slider.
- A Ribs maze adds a row with the Strength slider and the Seam combo.
- A Lines maze adds a row with the Spacing combo. A Uniform maze adds no row.
- A Bricks pattern puts the Size combo in the first row.
- A Tiles pattern puts the Size combo in the first row. A row with the Stagger combo and the Encased checkbox follows.
- A Scales pattern has the Pattern combo alone in the first row.
- A Chaos pattern puts the Cells combo in the first row. A row with the Node Spacing and Jitter sliders follows, then a row with the Corner Radius slider.
- Bricks, Tiles, Scales and Chaos then add the path rows: Allow loops and Loop Share, then Surface paths and Cutting, then Path Chance.
- Scales puts the Paths over tails checkbox right of Path Chance.
- The shape row holds the Shape combo.
- Loop Share is off while Allow loops is off.
- Cutting, Path Chance and Paths over tails are off while Surface paths is off.
- A checkbox that is off draws at 50% opacity. A slider that is off keeps the vanilla off look.
- A change of the pattern type, the maze type, the tile size or the shape rebuilds the variation rows. The rebuild waits for the next tick and keeps the scroll position.
- A new pattern type takes the defaults of that type.
- The Strength, Jitter, Loop Share and Path Chance sliders run from 0% to 100% in steps of 5%.
- The Node Spacing slider runs from 6 to 12 blocks in steps of 1. `ChaosSettings` holds the bounds.
- The Corner Radius slider runs from 0 to 3 blocks in steps of 1. `ChaosSettings` holds the bounds.
- The Stagger combo of small tiles lists None, X and Y. The Stagger combo of large tiles lists None and Y.
- While Encased is on, the X entry is off. Its tooltip is "Encased tiles and large tiles do not stagger along X".
- A check of Encased turns an X stagger into no stagger.
- The Seam combo shows the short name of the seam. Its tooltip and the tooltip of each entry show the full name.
- The button row holds the Copy button (export), the Paste button (import) and the Remove button, in that sequence.
- The column chance slider runs from 0% to 100% in steps of 5%.
- The spacing sliders run from 32 to 1024 blocks in steps of 16.
- The range sliders read "Pillar Bottom" and "Pillar Top". They run over the build limits of the dimension in steps of 16.
- A range slider that comes within 64 blocks of the other pushes it along. `YLimits` holds the steps.
- A dimension that the world does not hold has no range sliders. Neither has a dimension of 64 blocks or less.
- A spacing slider that passes the other spacing slider pushes it along.
- The weight slider runs from 0 to 16. The last positive weight of a dimension does not go below 1.
- The maze bias slider runs from 5% to 95% in steps of 5%.
- The maze bias label says "ringwise" below 50%, "uniform" at 50% and "lengthwise" above 50%.
- The decay scale slider steps through the powers of two from 32 to 1024.
- The decay threshold slider runs from 0% to 100% in steps of 5%. 100% is a threshold of 1.
- The erosion threshold slider runs from 5% to 100% in steps of 5%.
- The erosion threshold of a variation stays at least 5% above its decay threshold. A slider that passes the other pushes it along, so the decay threshold tops out at 95%.
- The block field rows hold the maze wall, maze path, interior, junction, surface path and trusses blocks, in that sequence.
- Only a variation of the Bridge shape shows the trusses block row.
- The block labels are "Wall", "Path", "Interior", "Junction", "Surface" and "Trusses".
- A block field row holds the label, the value and the Edit button, from left to right.
- A simple provider shows its block field as the value. A block field has no tooltip.
- Any other provider shows its summary in gray as the value, for example "Weighted: 3 stone_bricks, 1 andesite".
- A summary wider than the value shows the full text as its tooltip.
- `ProviderText` makes the type name and the summary of each provider type.
- The Edit button is 20 high and fits its text. Its narration is "Edit" and the field label.
- Edit pushes the provider screen of the field. See [Provider screen](#provider-screen).
- The rows of each variation draw inside one box: a white rectangle at 25% opacity that pads the rows by 4 on each side. An empty row separates two variations.
- A block field reads a block id or a block state string, such as `oxidized_copper` or `oak_log[axis=x]`.
- Bad text gives the block field a red border. The field keeps its last good block.
- While a block field has focus and text, a dropdown lists up to 8 blocks whose id or name holds the text. A click or Enter picks one.
- The dropdown shows the item icon and the name of each block. It lists no air block.
- A dimension with one variation has its Remove button off.
- Export writes one variation to the system clipboard as one line of JSON.
- The JSON of one variation has the fields of a variation entry. See [Registries](#registries).
- The JSON of one variation holds no weight. Only the variation codec knows the fields.
- The variation codec writes every field.
- Import parses the clipboard at once as one variation.
- A good import replaces the variation and keeps the weight of its row. It rebuilds the tab.
- An import ignores a weight in the clipboard text.
- A bad import shows a system toast with the title "Import failed" and the parse error. The variation stays.
- The tooltip of Export is "Export this variation to the clipboard". The tooltip of Import is "Import this variation from the clipboard".
- The Add Variation button appends a copy of the first variation.
- A dimension without a variation gets `aerolite:stone` with weight 1 from Add Variation.
- An add or a remove of a variation rebuilds the tab and keeps the scroll position.
- The rebuild of the variation rows waits for the next tick of the dimension screen.
- While the rebuild waits, the variation rows ignore input.
- The dimension screen and the panel have no cloud height slider. The cloud height stays a field of the holder settings.

### Provider screen

- The provider screen edits the provider of one block field, like the vanilla single biome screen.
- Its title is the field label, a colon and the title of the dimension screen, for example "Wall: Amazing Pillars: Overworld".
- Its list holds the Type dropdown, then the editor rows of the chosen type.
- The footer holds Done and Cancel.
- The screen edits a draft of the provider. `ProviderDraft` holds the values of every type.
- Done writes the built provider to the edited settings of the dimension screen. Done then rebuilds the variation rows.
- Cancel and Escape drop the draft.
- Done is off while the draft builds no provider. Its tooltip then shows the reason.
- The return to the dimension screen keeps the selected tab and the scroll position.
- The Type dropdown lists Simple state, Weighted, Noise threshold, Noise, Dual noise, Rotated block and Randomized int, in that sequence.
- These are the provider types of Minecraft 1.21.1 that the editors know. `ProviderType` holds them.
- The tooltip of the Type dropdown and of each entry is the type id, for example `minecraft:weighted_state_provider`.
- A provider of any other type adds the entry Other. That entry is off.
- The type Other shows "No editor for" and the type id. The draft keeps that provider as it is.
- Simple state shows the State block field.
- Weighted shows the heading Entries, one line per entry, then the button "+ Entry".
- An entry line holds the block field, the weight field and a minus button. The list keeps at least one entry.
- Noise shows the seed field, the noise fields and the States list.
- Noise threshold shows the seed field, the noise fields, the Threshold field and the High Chance field. The Default block field, the Low States list and the High States list follow.
- Dual noise shows the seed field, the noise fields and the Variety Min and Variety Max sliders. The slow noise fields and the States list follow.
- The noise fields are the Octave field, the Amplitudes field and the Scale field.
- A state list holds a title line, one line per state with a minus button, and the button "+ State". The list keeps at least one state.
- The Variety Min slider and the Variety Max slider push each other along. `ProviderDraft` holds the variety bounds.
- Rotated block shows the Block field. A block without the property `axis` shows red text.
- Randomized int shows the Source block field, the Property dropdown, the Values dropdown and the value sliders.
- The Values dropdown picks Constant or Uniform. Constant shows the Value slider. Uniform shows the Min and Max sliders.
- The Min slider and the Max slider push each other along. The value sliders span the range of the chosen integer property.
- A number field reads a whole number, a finite number or a comma list of numbers.
- Bad text gives a number field a red border. The value keeps the last good parse.
- A type change carries the distinct states of the old type over to the new type.
- Simple state takes the first carried state.
- Weighted takes each carried state as one entry with weight 1.
- Noise and Dual noise take the carried states as their states. A change between Noise and Dual noise keeps the states.
- Noise threshold takes the first carried state as its default state and the second as its low state. The other carried states become its high states.
- Rotated block takes the first carried block with the property `axis`, else the first carried block.
- Randomized int takes the first carried state with an integer property as its source, else the first carried state.
- The noise values stay through a type change. So they carry over between the noise types.
- A Randomized int source of another provider type shows its type id as read-only text. Its property and its values then show as text.
- Values of another int provider type show their type id as text.
- A change of the row set rebuilds the rows on the next tick. The rebuild keeps the scroll position.

### Preview

- The footer of the pillars screen has a Preview button between Done and Cancel.
- Each footer button of the pillars screen is half the vanilla default button width.
- The Preview button shows only when ImGui loaded.
- The Preview button opens the preview screen with a copy of the settings.
- Done in the preview sends the copy back to the pillars screen. Cancel drops the copy.
- The preview shows the pillars of a square region of chunks around the region center.
- The region center is a world X and Z position in blocks. It starts at the origin.
- The region is the square of `2 * radius + 1` chunks around the chunk that holds the region center.
- The preview paints the pillars with the pillar step code into scratch proto chunks. The vanilla block renderer bakes the blocks.
- An ImGui panel covers the right edge of the window. The game view fills the part left of the panel.
- A splitter on the left edge of the panel sets the panel width, from 240 to 600 ImGui units.
- The panel edits the settings of the menu screens, with the same slider steps and the same rules.
- The panel shows the Preview section, then one collapsing header per configured dimension, then the add row.
- Each dimension header starts open.
- The id of a dimension header does not change in a rebuild. So the header keeps its open state after an add or a remove.
- Each dimension header holds two groups that start open: Pillar Placement and Pillar Variations.
- The group Pillar Placement holds the column chance slider, the two spacing sliders and the two range sliders.
- The group Pillar Variations holds the variation rows and the Add Variation button.
- Each variation row of the panel shows the sliders in the menu sequence: weight, maze bias, decay scale, decay threshold, erosion threshold.
- A Lines maze shows no maze bias slider in the panel.
- The combo Pattern follows the sliders. The widgets of the pattern follow it at a half indent, in the sequence of the dimension screen.
- Allow loops has Loop Share one indent under it. Surface paths, Cutting, Path Chance and Paths over tails follow with no extra indent.
- A pattern widget that is off draws in the ImGui disabled style. The rules of the dimension screen turn it off.
- The combo Shape follows the pattern widgets. The tab bar of the block fields follows the combo Shape.
- The Export button, the Import button and the Remove button follow on one line.
- Export and Import in the panel act like Export and Import of the dimension screen, with the same tooltips.
- The panel applies a variation removal after its row loop. So each row of the frame reads its own index.
- Each ImGui end call of the panel runs in a `finally` block. So an exception leaves no ImGui window open.
- A good import in the panel replaces the variation in the preview copy and starts a rebuild.
- A radio button left of each dimension name selects the previewed dimension.
- The radio buttons and the dimension dropdown of the Preview section always show the same dimension.
- A small Remove button sits at the right end of each dimension header in the panel.
- After the dimensions, the panel has a dimension combo, a list combo and an Add button.
- The list combo lists "No prefab" and every prefab by its name. It follows the rules of the list dropdown of the pillars screen.
- The list combo shows the tooltip of the list dropdown on hover, with the same dimension rule.
- When no dimension is left to add, both combos and the Add button are off. The dimension combo reads "All dimensions configured".
- The tab bar of a variation box holds the tab Blocks first. One tab per open field follows, in the sequence of opening.
- The tab Blocks leads the tab bar. The scroll of the tab bar never hides it.
- The tab Blocks holds one line per shown field: the label, the arrow button and the field.
- A simple provider shows its text field and its dropdown. A block field has no tooltip.
- Any other provider shows its one-line summary. The type name draws in the off text color, the summary in the text color.
- The tooltip of a summary names the type id.
- The arrow of a field opens the tab of the field, or selects that tab when it is open. The arrow never closes a tab.
- A click on a summary also opens or selects the tab of the field.
- The tooltip of the arrow is "Open the tab of" and the field label.
- A new tab opens at the right end. An open tab keeps its place.
- The cross on a field tab is the only close. A middle click closes no tab.
- A closed selected tab gives the selection to the tab on its left.
- A tab bar too narrow for its tabs scrolls. Each tab title stays whole.
- A field tab holds the provider editor of the field: the type combo, then the widgets of the type.
- The panel editor has the editor rows and the type change carry-over of the provider screen.
- The panel editor has no Done button. A provider that builds and differs from the field replaces the field at once.
- A value that builds no provider stays in the draft. The build error then shows in red under the editor.
- A provider that changes outside the editor, for example through an import, replaces the draft.
- `ProviderTabs` keys the tabs of a variation box by its dimension and its variation index.
- A removal of a variation drops its tabs. The tabs of each later variation move down one index.
- A removal of a dimension drops the tabs of its variations.
- The tab of a field that the variation no longer shows closes. The Trusses tab closes after a change away from the Bridge shape.
- A summary frame too narrow for the type name and the summary drops the type name. The frame clips the text.
- The type combo of an editor too narrow for the text "Type: " shows the type name alone.
- The label column of an editor takes at most half the line. A wider label takes its own line, and its field starts the next line.
- The settings probe captures each provider tab and the tab Blocks at the narrowest panel width. See [Dev tools](#dev-tools).
- Each variation of the panel draws inside one box: the default ImGui blue at 25% opacity, padded by the frame padding, behind the widgets.
- The tab Blocks shows the block fields in the sequence of the dimension screen, from top to bottom.
- The dropdown of a panel block field is a popup over the panel. It moves no other widget.
- The popup shows at most eight blocks with their item icons. It scrolls when the space below the field is too short.
- A click on a block in the popup sets the field and the variation.
- The Preview section of the panel has a dimension dropdown. It lists every dimension of the world being created, whatever its generator.
- The Overworld, the Nether and the End come first. The preview starts on the first dimension of the list.
- The dimension type gives the build height and the sky of the preview. The generator gives the biome source.
- The noise settings of a noise generator serve only the climate sampler of the biome source.
- A generator without noise settings samples biomes with the vanilla dummy noise settings, `NoiseGeneratorSettings.dummy()`.
- The climate of the dummy noise settings is zero everywhere. So a multi-noise biome source shows one biome.
- A biome source can fail without a running server. The Twilight Forest biome source reads the Overworld seed of the server.
- After the first failure of a biome source, the preview scene shows its fallback biome. The fallback is the first possible biome of the source, else the Plains.
- The preview logs the failure once per scene.
- A previewed dimension without a variations list shows the empty region: the sky, the grid and the outline.
- A removed dimension stays in the preview and shows the empty region.
- The Preview section of the panel has a seed field. The field starts with the seed text of the world creation screen.
- An empty seed text of the world creation screen gives a random seed. The field shows its decimal number.
- The seed field parses like the seed field of the world creation screen. A number is the seed. Other text hashes.
- A change of the seed field text starts a rebuild. An empty field keeps the last seed.
- The Reseed button picks a new random seed. The field shows its decimal number.
- Done in the preview writes the seed text to the seed field of the world creation screen. Cancel drops it.
- A Copy button, a Paste button and a Defaults button sit on one line after the seed row, in that sequence.
- The panel buttons Copy, Paste and Defaults have the tooltips of the header buttons.
- Copy in the panel writes the preview copy of the settings to the clipboard.
- Paste in the panel replaces the preview copy and starts a rebuild. A bad parse shows the toast of the pillars screen.
- Defaults in the panel follows the rule of the Defaults button of the pillars screen.
- Defaults in the panel acts on the preview copy of the settings and starts a rebuild.
- The toast draws left of the panel, because the window reports the reduced width.
- The radius slider runs from 2 to 32 chunks in steps of 1.
- The radius starts at the render distance option of the client.
- A change of the settings, the seed, the dimension or the radius starts a full rebuild on a worker thread after 300 ms.
- A full rebuild shows the scene chunk by chunk, ring by ring from the center chunk outward. In a ring, the nearest chunk comes first.
- The region shifts when the region center crosses a chunk border. A shift starts at once, with no wait.
- A shift keeps the painted and baked data of each chunk that stays in the region. It drops the chunks that leave.
- The painted chunks hold one ring of margin around the region. The margin is not baked.
- A chunk bakes when its neighbors in the margin are painted. So the bake of a chunk does not depend on the region position.
- A shift paints the chunks that enter the margin and bakes the chunks that enter the region, nearest chunk first. It bakes no kept chunk again.
- A chunk also bakes each border face that an opaque block of the next chunk hides. So a shift that drops the next chunk shows no hole.
- A hidden face bakes with the next chunk read as air, so its shading fits the exposed state.
- A progress bar shows the painted chunks and the baked sections of the newest rebuild or shift.
- The seeded pillar layout gives the world positions of the pillars. So a pan shows other parts of the same world.
- The Preview section ends with the center row: the text Center, an X field, a Z field and a Reset button.
- The X field and the Z field hold the region center in whole world blocks.
- A center field applies its value on Enter or when it loses focus.
- A center field without focus shows the current region center. So it follows a key move.
- Reset puts the region center on the origin.
- Each edge of the region box draws opaque. An edge with any pixel in front of a block draws 75% translucent.
- The preview has no fog.
- A drag in the game view turns the camera around the region center. The scroll wheel zooms.
- A view gizmo sits in the top right corner of the game view, left of the panel.
- A drag on the view gizmo turns the camera. A click on a face, an edge or a corner turns the camera to look at it.
- The camera pitch stays between -89 and 89 degrees. A top view and a bottom view keep a small tilt.
- The camera orbits the region center. Between shifts, the region box can sit up to 8 blocks off the view center.
- The movement keys of the controls move the region center along the camera yaw, like creative flight without pitch.
- With the default controls, W moves forward, S moves back, A moves left and D moves right.
- Space and Shift do nothing. The region center does not move up or down.
- The pan uses the real frame time.
- Ctrl makes the pan sprint while it is held.
- A second press of one movement key within the double press time also sprints, until that key is released.
- The constants at the top of `RegionPan.java` set the walk speed, the sprint speed and the double press time.
- A middle drag grabs the ground. The ground point under the cursor at the drag start follows the cursor.
- The ground is the horizontal plane through the region center.
- A cursor ray that misses the ground or hits it behind the camera moves nothing in that frame.
- The movement keys and the middle drag are off while a panel field takes text input or a widget of the screen has focus.
- Escape still closes the screen. Space still presses a focused button.
- A left click or a middle click in the game view clears the widget focus.
- The panel, its block field popups and the view gizmo draw at one constant font scale.
- The panel scale does not follow the GUI scale option.
- The constant `FONT_SCALE` in `ImGuiPreviewPanel` sets the panel scale.
- imgui-mc sets the ImGui main font scale from the GUI scale before each frame.
- The panel sets the main font scale to `FONT_SCALE` while it draws and restores it after.
- The panel draws with its own font, a copy of the imgui-mc default font.
- FreeType rasterizes the panel font as one-bit glyphs with mono hinting.
- The imgui-java binding cannot give one font its own loader. So the panel font sets FreeType as the loader of the whole atlas.
- The other fonts of the atlas keep their antialiased glyphs.
- The imgui-mc renderer for 1.21.1 bakes each font once at its load size. It scales the glyphs to the drawn size.
- The panel font loads at its drawn size times the framebuffer scale. So each glyph texel covers one framebuffer pixel.
- A change of the framebuffer scale rebuilds the font atlas.
- `ImGuiPanelFont` adds the panel font on each font event of imgui-mc.
- Each popup icon renders at the framebuffer pixel size of its row, rounded up to a multiple of 16.
- A change of that pixel size renders the icon atlas again.

## Data

The pillar data lives in datapack registries and data maps. See the glossary for the terms.

### Registries

- Three datapack registries hold the pillar data. They do not sync to the client.
- `aerolite:shaft_variation` holds the variations. The shipped entries are `aerolite:stone`, `aerolite:tuff_bricks` and the others in the classes in `datagen/shaft/`.
- A variation entry holds `bias`, `decay_scale`, `decay_threshold`, `erosion_threshold`, `pattern`, `shape`, `interior`, `junction`, `wall`, `path`, `surface` and `trusses`. It holds no weight.
- The variation codec writes the fields in that sequence. `ShaftVariation.KEYS` holds the sequence.
- The variation codec writes every field.
- An entry without `junction` takes its interior block. An entry without `trusses` takes its interior block.
- An entry without `surface` takes its path block.
- An entry without `pattern` takes the Uniform maze. An entry without `shape` takes the Pillar shape.
- The `pattern` object holds the key `type` first. Its value is the pattern type id, for example `aerolite:mazes`.
- The fields of the pattern type follow the key `type` in their codec sequence. A pattern field without a value takes its default.
- The maze type of a Mazes pattern has the key `maze`, because the key `type` holds the pattern type.
- The value of `shape` is `pillar`, `i_beam` or `bridge`.
- Each block field reads a bare block state of 0.3 or a provider object. The codec writes the provider object.
- `StateProviders.CODEC` is the codec of each block field.
- Two variations are equal when their other fields are equal and each pair of providers is the same.
- A simple provider compares by its state. Any other provider compares by its JSON from the provider codec.
- A provider whose JSON does not encode compares by identity.
- Each shipped variation takes its interior block as its junction block and its trusses block.
- Each shipped variation takes its maze path block as its surface path block. It has the Uniform maze and the Pillar shape.
- The variation codec clamps the bias to the slider range.
- The variation codec raises an erosion threshold at or below the decay threshold to the decay threshold plus 5%.
- The pattern codecs clamp each number option to its slider range.
- A Tiles pattern with an X stagger and large tiles loads with no stagger. So does a Tiles pattern with an X stagger and Encased on.
- `aerolite:shaft_placement` holds the placements. A dimension takes the entry under its own dimension id.
- A dimension without its own placement entry takes `aerolite:default`.
- The placements of the vanilla dimensions are in the namespace `minecraft`, for example `data/minecraft/aerolite/shaft_placement/overworld.json`.
- The placement of a modded dimension is in the namespace of that dimension, for example `data/aether/aerolite/shaft_placement/the_aether.json`.
- Data generation writes only the namespaces `aerolite`, `minecraft` and the namespace of each key of `ShaftRegistries.DIMENSION_LISTS`.
- `aerolite:variation_list` holds `aerolite:overworld_pillars`, `aerolite:nether_pillars` and `aerolite:end_pillars`. Each entry is a variation list.
- A variation or a variation list that names a block or a variation of another mod needs a `neoforge:mod_loaded` condition.
- `requiredMod` and `modVariations` of the class of a dimension in `datagen/shaft/` name those entries.
- Each class in `datagen/shaft/` that implements `DimensionShaftEntries` holds the placement, the list and the owned variations of one dimension.
- `AeroliteShaftEntries` collects those classes through a `@Directory` list. A new class needs no other registration.
- A list can name a variation of another dimension by its key, for example `OverworldShaftEntries.MOSSY_MAZE`. Only the owner registers that variation.
- The required mod of a list does not reach a variation of another dimension.
- A game without that mod skips those entries. The world still loads.
- The GameTest server does not load the dimension mods. So an entry without its condition stops that server at the registry load.
- The menu copies from the variation lists. Worldgen reads them only for a generator without the field `aerolite`.
- A change to a shipped variation list changes the pillars of each world whose generator has no field `aerolite`.
- The JSON key sequence of a variation list is the pick sequence. A tool that sorts the keys changes the pillars.
- Data generation writes the variation lists with their key sequence. The other generated JSON has sorted keys.

### Data maps

- The data map `aerolite:shaft_variations` is on the level stem registry. Its value is a variation list.
- Its file is `data/<namespace>/data_maps/dimension/shaft_variations.json`. The keys are dimension ids.
- It ships without values. So no dimension gets pillars from the shipped data alone.
- The world creation screen applies data maps to a level stem registry without the dimensions of the new world.
- So `DimensionShaftValues` reads the files of `aerolite:shaft_variations` by dimension id, with the merge rules of NeoForge.
- `DimensionShaftValues` skips a tag entry, because a tag cannot resolve without the dimensions.
- The data map `aerolite:cloud_height` is on the dimension type registry. Its value is a Y level.

### Level binding

- The level load resolves the pillar data of each server level. A data attachment of the level holds it. The level does not save it.
- The pillars step and the cloud height packet read the attachment.
- The binding resolves once per level load. A reload of the data packs does not change it.
- The project timed a mixin field on the server level against the attachment. Per chunk, the difference stayed below the noise.
- So the attachment holds the pillar data. The project rule allows a mixin only when no API can do the task.
- A data map value for the dimension wins over the pending settings and the generator settings.
- Without a data map value, a level takes the claimed pending settings when they list its dimension.
- The server claims pending settings only for a new world. See [Hand-over to the server](#hand-over-to-the-server).
- Claimed pending settings give their cloud height to each level of the dimension type `aerolite:overworld`, listed or not.
- Without a data map value and without pending settings for it, a level takes the generator settings for its dimension. Its generator must be the Aerolite generator, and the settings must list the dimension.
- A server with claimed pending settings writes the world pillar pack from them at the first level load.
- That pack holds each listed dimension without a data map value, and the cloud height of the pending settings.
- A new world gets vanilla noise generators. So only a world of 0.2 takes generator settings.
- A level that takes the generator settings and has the dimension type `aerolite:overworld` takes the cloud height of the generator settings.
- A generator without the field `aerolite` takes the default settings. They come from the shipped variation lists and placements.
- A 0.1.0 save without a variation map reads the default lists from the registries of the save.

### World pillar pack

- A level that takes the generator settings writes the world pillar pack to `<world>/datapacks/aerolite_shafts/`.
- The level load writes the pack once. A world with a folder `datapacks/aerolite_shafts` gets no new pack.
- The game enables the new pack on the next world load. From then on, the data maps give the pillar data.
- The writer puts the partial pack in `<world>/aerolite_shafts.tmp`. Then it moves the pack into the data pack folder.
- The pack holds a value for each dimension whose Aerolite generator lists it and that has no data map value.
- `pack.mcmeta` holds the data pack format of the game.
- `data/aerolite/data_maps/dimension/shaft_variations.json` holds the variation list of each of those dimensions.
- `data/<dimension namespace>/aerolite/shaft_placement/<dimension path>.json` holds the placement of each of those dimensions.
- A variation equal to a shipped entry refers to that entry. Each dimension list refers to one entry at most once.
- Equality uses the provider comparison. See [Registries](#registries).
- Any other variation gets the entry `aerolite_world:<dimension namespace>/<dimension path>/<index>`. The index is its position in the list.
- `data/aerolite/data_maps/dimension_type/cloud_height.json` holds the cloud height of the Overworld generator for `aerolite:overworld`.

## Pillars

### Placement

- Along X, a seeded sequence of X positions exists. At each position, one Z-aligned horizontal pillar runs at one seeded height.
- Along Z, a seeded sequence of Z positions exists. At each position, one X-aligned horizontal pillar runs at one seeded height.
- A horizontal pillar keeps one height for its full length.
- Pillars have no elbows. A pillar never turns and never steps.
- An X pillar and a Z pillar never intersect.
- Every seeded choice of the pillars uses a random that mixes each coordinate into the seed.
- The vanilla Xoroshiro positional random puts the coordinate hash into the seed unmixed. Its first values correlate between neighbor coordinates.
- Each axis is a jittered grid. The cell size is the mean of the minimum and the maximum spacing. Each cell holds one pillar at a seeded place.
- A margin of half the minimum spacing in each cell keeps each neighbor distance between the minimum and the maximum.
- The smallest minimum spacing of 32 keeps at least 16 blocks between two pillars on the same axis.
- The pillar range is cut into slots of 16 blocks. The bottom slot and the top slot hold no horizontal pillar.
- The seed shuffles which of the other slots belong to X pillars and which to Z pillars.
- A horizontal pillar picks one slot of its axis. Inside the slot it picks an offset of 1 or 2, so at least one block of air separates it from a pillar in the next slot.
- A column can stand where an X pillar and a Z pillar cross in plan view. The column chance setting decides which crossings get one.
- A crossing gets no column unless at least one slot lies between the slots of its two pillars.
- Each column has a top end and a bottom end.
- The top end is uniform from 16 blocks above the highest junction box to the top of the pillar range. The bottom end is uniform from the bottom of the pillar range to 16 blocks below the lowest junction box.
- Those 16 blocks hold the shortest shear band and the 8-block margin.
- Shear thins each column toward its two ends. No column ends in a flat cut.
- Near a column end, a noise threshold removes blocks from the column. The threshold rises toward the end, so fewer blocks remain the closer they are to it.
- Each column end picks its shear band height at random, from 8 to 64 blocks.
- The shear band shrinks so at least 8 blocks of full column stay between the band and the outermost junction box.
- Shear does not touch a junction box.
- Where a column crosses a horizontal pillar, a junction joins them. The junction is a 15x15x15 box, centered on the crossing.
- The box is the interior block of its variation.
- The bezel of each exposed face is the junction block of its variation.
- The patch area inside the bezel is the maze wall block over the interior block.
- Every horizontal pillar and every column picks one variation by weight. A weight of 0 never picks.
- The pick walks the variation list in its sequence. The same seed, weights and sequence give the same pillars.
- A horizontal pillar picks with a random at its axis and cell. A column picks with a random at the cells of its two pillars.
- A junction box takes the variation of its horizontal pillar. So do its two patches and the maze path blocks on them.
- A dimension without pillar data gets no pillars. The noise settings of the dimension still make its terrain.
- `ShaftLayoutTest` holds a fingerprint of the default pillars of one seed. A change that moves a pillar fails it.
- Pillars pierce islands. Each solid block of the finished pillar replaces the terrain block at its position.
- The pillar paint writes only solid blocks. A block of the chunk stays where the finished pillar is air.
- The pillar paint computes the finished pillar in a buffer. Only the flush reads the chunk.
- The block tag `aerolite:pillars_cannot_replace` holds the blocks that the pillar paint keeps.
- That tag holds `#minecraft:wither_immune` and the Nether portal.
- So it holds each vanilla block with a negative destroy time, and reinforced deepslate.
- The flush reads a block of the chunk only when the palette of its section can hold a block of the tag.

### Cross section

- The Pillar shape is 13x13 outside. Its interior is 11x11 and solid.
- The interior uses the interior block of the variation.
- The outer layer is one block thick. It holds the pattern.
- The ring description of the shape gives the cross section and the outer blocks of each pillar and column.
- The paint samples each provider with a random of the world seed and the block position. So two chunks that paint one block give it the same block.
- A simple provider takes no random.
- The preview paints with the same providers and the same randoms.

### Maze

- The maze is one spanning tree per ring along the whole pillar. It has no loops.
- The rules of this section describe the Uniform maze on the Pillar shape. [Maze types](#maze-types), [Shapes](#shapes) and [Junction link](#junction-link) add to them.
- A ruling sits on every odd block along the pillar. On a ruling, nodes sit on even blocks around the pillar, so the four corners are nodes. Edges sit between neighbor nodes. Every other maze grid block is a maze wall.
- A corner node has no edge along the axis, so no path runs along a corner.
- After sampling, a corner node of degree one is dropped with its edge. So a two-block margin runs along every corner, with a notch where a path crosses the corner.
- Wilson's algorithm samples each tree from a seeded root. It samples the weighted spanning tree distribution exactly.
- In that distribution, the chance of a tree is proportional to the product of its edge weights.
- Every axis edge has the weight of the maze bias. Every ring edge has the weight of 1 minus the maze bias.
- At each step, the walk takes one edge of its node in proportion to the edge weight.
- An edge between two rulings is an axis edge. An edge between two nodes of one ruling is a ring edge.
- An edge to a virtual node is an axis edge. A crossing onto a patch is an axis edge.
- A patch edge along the pillar axis is an axis edge. A patch edge across the pillar axis is a ring edge.
- A maze bias above 50% favors maze paths along the pillar. A maze bias below 50% favors maze paths around the pillar.
- Each horizontal pillar and each column samples with the maze bias of its variation. A junction segment uses the maze bias of its own pillar.
- A horizontal pillar is cut into segments, aligned to chunks. Segments with an even index are tree segments. Segments with an odd index are connector segments.
- Each segment is a pure function of the world seed, the pillar and the segment index. The chunk generation sequence does not change the result.
- A connector segment samples its tree on its own nodes plus two virtual nodes. A tree edge at a virtual node becomes a crossing to that neighbor tree segment.
- The union of all segments is one tree. A chunk boundary gets several crossings, so no wall ring marks it.
- A chunk needs at most two segments: its own, which can be a junction segment, and the connector segment before it along the axis.
- A column is cut into segments of one chunk section, like a horizontal pillar. A column segment is a pure function of the world seed, the column and the section index.
- At an end of the pillar range, a segment gets no virtual node on the outer side.
- A junction cuts the maze grid of both pillars. The one-block rim around each pillar attachment sits on even positions, so it is wall.
- A junction exposes two patches, the faces that no pillar covers. The seed picks one patch for the horizontal pillar. The other patch serves the column.
- The bezel holds the face offsets 5, 6 and 7 in both face directions. The patch area inside it is 9x9.
- The patch nodes sit at the face offsets -4, -2, 0, 2 and 4. Patch edges sit between neighbor patch nodes.
- `Ring.PATCH_NODES` sets the patch size. The bezel thickness derives from it and `Ring.BOX`.
- A junction segment replaces the chunks that hold the nearest surviving ruling on each side of the box. Its graph is those rulings minus the box rulings, plus the pillar patch nodes, plus a crossing from each patch border node that faces the pillar, without the patch corner nodes, onto the nearest ruling. One tree spans all of it, so no part strands.
- The junction segment keeps the chunk alternation. An end chunk with an odd index gives it a virtual node on that side.
- When the junction segments of two boxes share a chunk, one junction segment spans both boxes. It holds one pillar patch per box.
- The column patch is part of a column junction segment. Crossings run from its border nodes above and below, without the patch corner nodes, onto the nearest ruling.
- In the graph, each of the 3 border nodes between the corners of one patch side has one crossing edge. The sampled tree holds a subset of those edges.
- On the Pillar shape, a patch corner node gets no crossing. It keeps its two patch edges. No patch corner node is dropped.
- A crossing runs along the pillar outer layer up to the box. Then it notches through the bezel at the face offsets 7, 6 and 5, onto a border node at offset 4.
- A border node and its ring node sit at the same offset across the pillar axis. So the notch runs straight.
- Each notch block is a maze path. The block one in is the maze path block.
- Each ring of a pillar is one tree over its full length. The maze has no loops.
- No tree touches another pillar. The mismatched junction of a Bridge column is the one exception. See [Junction link](#junction-link).
- A maze wall keeps its outer layer block. That block is the maze wall block, at the 13x13 extent.
- A maze path has no outer layer block. The block under it, at the 11x11 extent, is the maze path block.
- A maze path at a chunk edge can have its maze path block in the next chunk. Each chunk writes the blocks of a maze path that lie in it.
- The maze grid of a chunk holds the maze grid blocks up to two blocks outside the chunk. So both chunks decay one maze path the same way.
- Decay is one fractal Brownian simplex noise over the pillar. Far negative values keep only maze walls. Far positive values keep only maze paths. Central values keep both.
- The decay threshold t sets the regimes. Noise below -t keeps only maze walls. Noise above t keeps only maze paths.
- A maze wall or maze path block that decay removes leaves no pillar block. A removed path exposes the interior block.
- Decay acts on the whole 15x15 face of each patch. The bezel joins the maze grid of the patch as maze wall.
- Decay removes a bezel block where the noise passes the decay threshold, like a maze wall. The interior block beneath it shows.
- A removed patch maze wall exposes the interior block, like a removed pillar maze wall.
- A removed patch maze path also removes its maze path block. The block one further in becomes the maze path block.
- A notch block decays like a maze path.
- Erosion starts where the absolute decay noise passes the erosion threshold, at either end of the noise.
- The reach is the absolute noise, normalized from the erosion threshold to 1.
- Each block has an erosion resistance from 0 to 1. Erosion removes a block when the reach exceeds its resistance.
- In the Pillar shape, the resistance rises over 5 layers from the outer layer inward. The 3x3 core has a resistance of 1, so it never vanishes.
- In a junction box, the box resistance rises over 6 layers from the box face inward. The 3x3x3 core has a resistance of 1.
- A box block takes the largest of the box resistance and the resistance of each pillar whose cross section holds it. So erosion never cuts a pillar at a junction.
- Erosion samples the noise at each block, so one side of a pillar can erode deeper than the other.
- Erosion runs after decay and before shear. An eroded block leaves no pillar block.
- The prototype renders the segment layouts and one junction from both sides. The user picked the connector layout and the junction segment from its images.
- The prototype keeps Origin Shift and its start trees for comparison. The mod uses only Wilson's algorithm.

### Maze types

- The maze types follow `prototypes/maze/README.md`. `MazeTypes` holds the pattern edges of each type.
- A maze type draws its pattern edges from the pattern random of the segment. That random takes no value from the segment random.
- Below 100% strength, Wilson's algorithm samples one weighted spanning tree per segment.
- The weight of an edge is its class weight times its pattern factor.
- The class weight of an axis edge is the maze bias. A ring edge has the class weight 1 minus the maze bias.
- A pattern edge has the pattern factor 1. Any other edge has the pattern factor 1 minus the pattern strength.
- At 100% strength, the pattern edges are the tree edges.
- A Uniform maze, or a pattern strength of 0%, samples the maze of 0.3.
- The maze types keep the tree segments, the connector segments and the junction patches of [Maze](#maze).
- A node carves only when an edge holds it.
- Serpentine: each ruling is one ring path with one gap. The gap position varies per ruling.
- Serpentine: one axis edge next to the gap joins a ruling to the next ruling.
- A Serpentine connector segment replays the gap of the facing ruling of the neighbor before it. The pattern random of that neighbor gives the gap. The connector turns next to it.
- Ribs: the breadth-first tree of the segment graph from a root.
- The seam picks the Ribs root. The root is random per segment, seeded per segment on the first ruling, or one ring position per pillar.
- Lines: straight axis grooves on every node column at line spacing 1. At line spacing 2, the grooves sit on every second node column.
- Lines has no maze bias and no pattern strength. Lines is a forest by design.
- At line spacing 2, the four faces of the Pillar shape get 3, 2, 3 and 2 grooves.
- In a Lines maze, a crossing onto a groove node makes its patch line across the axis a groove.
- Gilbert: the generalized Hilbert curve around the ring, with its seam at ring position 0.
- At 100% strength, a union-find pass joins the Gilbert pieces with the fewest extra edges, in a seeded sequence.
- Then each virtual node joins by one edge, and each crossing group by one crossing. So the pillar is one tree.
- `MergedJunctionTest` puts two boxes 31 to 35 blocks apart, so one junction segment holds both. Each maze type keeps each pillar one tree.

### Patterns

- A pattern fills the segment surface of each segment. `SurfaceCarver` turns the band blocks into world blocks.
- A wall block keeps the maze wall block. A groove is a maze path.
- A surface path block is the surface path block at the outer block.
- A groove on a flush face writes the maze path block at the outer block, with no carve.
- Bricks, Tiles and Scales share the band pattern code in `BandPattern`. Chaos has its own code in the package `world.shaft.pattern.chaos`.
- Each pattern follows the README of its prototype. The Javadoc of each pattern class states its rules.
- A rule on the face columns 0 to 12 applies to each face between two corners. A face of another width keeps the period from its first column.
- A surface path has zero to two corners: an I, an L, an S or a U.
- A U needs legs that do not touch. A surface path is never 2 wide.
- A surface path adds no plus crossing beyond the plus crossings of the base pattern.
- A surface path leaves no orphan wall block. An orphan wall block has grooves on all four sides.
- A cut path that turns inside a small tile can leave an orphan wall block. Small tiles allow it. Large tiles keep the orphan rule.
- A surface path never runs along a corner.
- With Cutting off, a surface path is surface path blocks flush at the wall layer.
- With Cutting on, a surface path is carved as grooves. It then joins the groove graph.
- Each region of a band pattern rolls the path chance once.
- A Chaos pattern rolls the path chance once per wall face. Each face takes one surface path at most.
- A Chaos surface path keeps a wall strip of 2 or more blocks on each side. Each part of the face beside it keeps a 2x2 wall square.
- A Chaos face without a candidate can take a straight path with a 1-block strip on each side. A cut path never takes that fallback.
- The loop share rations the cycles of the groove graph segment by segment. The segments are tree segments and connector segments, like the maze.
- Each segment samples a spanning forest with Wilson's algorithm. The other edges of the segment are its cycle edges.
- A segment keeps the loop share of its cycle edges. Each other cycle edge loses one block.
- At 0%, the grooves of each segment form a forest. At 100%, every groove stays.
- A cut surface path counts as part of the groove graph, so its edges can be cycle edges.
- The rationing never removes the end groove of a surface path or a Scales tail.
- The rationing draws do not depend on the share. So the removal at one share holds the removal at each higher share.
- Each pattern segment is a pure function of the world seed, the pillar and the chunk index.
- A band pattern builds each scene with a margin of chunks on each side. So a chunk is the same in every scene that holds it.
- The last regions of a chunk draw their surface paths first, from their own random. The other regions then fit around them.
- Chaos computes blocks of chunks with a window margin and crops the segments from them.
- A column or a short pillar builds one scene for all its chunks. `BandPattern` and `ChaosPattern` hold the length limit.
- A junction box makes its rows box blocks on every ring, except the patch area.
- The pattern continues onto the 9x9 patch area inside the bezel. The bezel and the box edges cut the grooves.
- The patch crossings of the maze do not apply to the other pattern types.
- The patch belongs to the segment that holds every chunk of its box.
- On a Bridge ring, Chaos paints no patch area.
- On a Bridge ring, Bricks paints only the outer columns of the patch area.
- A surface path block is no carve, so it decays like a maze wall. A flush maze path block decays like a maze wall too.
- `PatternCache` keeps the surface path draws and the finished chunk bands of the band patterns. A neighbor chunk reads them again.
- `ChaosScenes` keeps the recent Chaos bands. The chunks of one computed block share one computation.
- Each cache entry is a pure function of its key. The key holds the options, a fingerprint of the pillar random, the ring, the chunk and the boxes in reach.
- Each cache drops its least recently used entry above its capacity. The capacity is a constant in the cache class.
- A cache computes an absent value outside its lock. So two threads can compute the same value.
- Bricks: bed joints run around the ring. Head joints run along the axis inside one course. A head joint never sits on a corner.
- Small bricks are 5x3. Neighbor faces swap the course phase. The pillar random picks the face pair that starts on phase 0.
- Jumbled bricks have three courses per chunk of 4, 4 and 5 rows in random sequence. Each face has two head joints at random even columns.
- A jumbled head joint never shares a ring position with a head joint in the course above or below.
- Large bricks are 11x7. One course has its head joints at face column 2, the next at face column 10, on every face.
- Tiles: axis grooves run along the pillar. Ring grooves run around it. An axis groove never sits on a corner.
- Small tiles are 3x3, with axis grooves at face columns 2, 6 and 10.
- Large tiles are 7x7, with axis grooves at face columns 2 and 10 and a 3-wide corner strip.
- Stagger X gives small tiles a running bond. Stagger Y alternates the ring groove phase from tile column to tile column.
- The pillar random picks the tile columns of phase 0 of stagger Y.
- Encased keeps the corner strip solid. No ring groove crosses it.
- Scales: small tiles with stagger X and one tail block per tile.
- A tail stays wall and caps the axis groove of the next course. The pillar random picks the tail direction.
- With Paths over tails on, a cut path can carve a tail. That tail has path on all four sides, the one accepted exception to the plus rule.
- Chaos: a 2D jittered grid of cells on the unwrapped band. The grid wraps around the ring.
- The Chaos cells around the ring are the ring length over the node spacing, rounded half up. The cell width is the ring length over that count.
- Each Chaos block takes the label of the nearest grid point. A block is a groove where its 2x2 window holds two labels.
- The corner radius chamfers the Chaos stones at each T junction.
- The Chaos paint caps the jitter per node spacing, so every stone stays at least 3 blocks across. `ChaosParams.jitterCap` holds the caps.
- Voronoi cells repair each plus crossing into two T junctions.
- Delaunay cells give the dual of the stones. Each stone gets a groove ring at its center. A straight groove joins the centers of two neighbor stones.
- Chaos gives the corners no special role. A stone can span a corner, and a groove can run along one.

### Shapes

- The shapes follow `prototypes/shapes/README.md`. The ring descriptions are in the package `world.shaft.shape`.
- `ShapeRings` gives the ring description of a shape and a web plane.
- Columns take the shape of their variation, like horizontal pillars.
- An I-beam has flanges 13 wide and 4 thick and a web 5 thick and 5 tall.
- One I-beam ring of 64 blocks follows the H outline over every surface. It has 32 nodes.
- The I-beam ring holds the hidden block of each concave corner.
- The outer corners of an I-beam are corner nodes. The inner flange corners are ring edges.
- A concave corner of an I-beam is an edge. Its groove removes the hidden block and leaves an L-shaped floor of two blocks.
- A horizontal I-beam pillar has a vertical web.
- Each column draws a web plane, X or Z, as its last seeded draw. So the draw changes no earlier draw of the column.
- An I-beam column puts its web in its web plane.
- The Bridge shape has two plates, 3 thick and 13 wide, with 7 truss rows between them.
- Each plate is its own ring of 28 blocks and its own tree.
- The outer face of a plate carries the pattern as grooves. The inner face is a flush face, 11 wide.
- The trusses stand one block in from the plate sides, two blocks wide per row, with a period of 8. `BridgeRing` holds the truss layers.
- The trusses carry no pattern. They are the trusses block.
- Each anchor takes the place of an inner plate layer block. An anchor wins over the flush pattern.
- The paint writes the trusses after the patterns. An anchor leaves the maze grid, so decay keeps it.
- A Bridge column takes the plane of its trusses from its web plane.
- Every shape pair gets the 15x15x15 junction box with its 3-block bezel.
- A horizontal Bridge pillar passes through each junction box in an 11x11 hole along its axis.
- The Bridge blocks stay in the hole. The box wins where they coincide. So the passage is 7 wide and 7 tall.
- The box wins over a truss outside the hole.
- The plates of a horizontal Bridge pillar run uncut through the box rows.
- A Bridge column gets no hole. The solid box cuts it.
- The ring description gives the erosion resistance of each block of its cross section.
- Each plate keeps its middle layer at resistance 1. The web keeps its middle layer at resistance 1.
- A flange is 4 thick, so it keeps both middle layers at resistance 1.
- A truss block and an anchor have the resistance `ErosionResistance.TRUSS`.
- In the hole, each box block takes the resistance of the Bridge shape.
- Columns of every shape keep the shear bands. Shear also removes truss blocks.

### Junction link

- The Pillar shape keeps the junction rules of [Maze](#maze).
- The solid box cuts an I-beam like the Pillar shape.
- A flange face toward the patch gives 3 crossings, like a face of the Pillar shape.
- Toward the flange edges, only the two flange edge nodes take crossings, onto the patch corner nodes. The web nodes take none.
- A crossing onto a patch corner node is an allowed exception for the I-beam and the Bridge shape.
- The two plates of a horizontal Bridge pillar are two trees. They run uncut through the hole.
- The patch of a horizontal Bridge pillar takes exactly one crossing, from a side corner node of one plate. The other plate takes none.
- The random of the box picks that plate once per junction. The tree takes the crossing from one side of the box.
- Every column has one X junction and one Z junction.
- At the matched junction of a Bridge column, the plate sides face the patches. Both plates join the column patch through their side corner nodes.
- At the mismatched junction of a Bridge column, the plate outer faces face the patches.
- There, the near plate joins the column patch through its 3 middle nodes, like a face of the Pillar shape.
- There, the far plate joins the patch of the horizontal pillar the same way. Its crossings land on the top and bottom border sides of that patch.
- Every plate piece of a Bridge column joins the patch it faces, from below and from above, at both junctions.
- The crossings onto the patch of the other pillar join one virtual node per box. That node stands for the tree of the other pillar, so no such crossing closes a loop.
- So the tree of a Bridge column touches the tree of the horizontal pillar at the mismatched junction. The owner accepts this one exception.
- The Bridge column, its patches and the touched horizontal pillar form one tree. The untouched plate of a horizontal Bridge pillar stays its own tree.
- `ShapeMazeTest` checks the junction link against the results of the shapes prototype.

## Walker Trees

- A tree roots on the outer layer of a pillar and walks outward into the air, like a chorus plant.
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

- The Islands preset is "Aerolite Islands". Its key is `aerolite:islands`. Its lang key is `generator.aerolite.islands`. It is in `#minecraft:normal`.
- The Islands preset gives a vanilla noise generator to the Overworld, the Nether and the End. Each generator uses the island noise settings of its dimension, `aerolite:islands/<dimension>`.
- The Overworld of the Islands preset uses the dimension type `aerolite:overworld`. The Nether and the End keep their vanilla dimension types.
- Each dimension keeps its vanilla biome source and the vanilla climate functions of its noise router.
- The End router carries the vanilla End islands function as its erosion. The End biome source reads the erosion.
- The final density of each dimension is the island density function of that dimension, `aerolite:islands/<dimension>`, inside `minecraft:interpolated`.
- The initial density without jaggedness is the island density function over the surface floor, not interpolated. The vanilla surface rules read it.
- The surface floor is a Y gradient from 1 at the bottom of the dimension to -1 four blocks up. `AeroliteIslandEntries.FLOOR_HEIGHT` holds the height.
- The vanilla surface rules lerp the preliminary surface level of the four 16-block grid corners around a block. A corner without a level has `Integer.MAX_VALUE`.
- So without the floor, the island edge band gets no surface. With the floor, every corner has a level.
- The island density function type is `aerolite:voronoi_islands`. `VoronoiIslands` is its class. `VoronoiLattice` holds the lattice math.
- The fields are `noise`, `cell_size`, `cell_height`, `layer_min_y`, `jitter`, `fill_min`, `fill_max`, `empty_chance`, `distortion`, `distortion_scale` and `distortion_octaves`. `AeroliteIslandEntries` holds the shipped values.
- The lattice puts one site in each box of `cell_size` by `cell_height` by `cell_size` blocks. The row 0 of boxes starts at `layer_min_y`.
- A cell is the 3D Voronoi polyhedron of its site. Only the row 0 holds islands. The rows above and below shape the cells of the row 0.
- An island is its cell scaled about its site by the fill fraction of the cell. The fill fraction is a seeded value between `fill_min` and `fill_max`.
- A cell of the row 0 holds no island when its seeded value is below `empty_chance`.
- The density is positive inside an island. Near the island surface its unit is about one block.
- The field `noise` is the seed channel. The random state wires it with the world seed. The lattice seed derives from three values of the wired noise.
- A lattice with an unwired noise has the seed 0. The JUnit tests use it.
- The search reach and the wall reach of the lattice derive from the box sizes and the jitter. `VoronoiLattice` states the rule.
- The distortion shifts the position before the lattice lookup. Fractal simplex noise with one channel per axis gives the shift.
- `distortion` is the largest shift in blocks. `distortion_scale` is the wavelength of the first octave in blocks. `distortion_octaves` is the octave count.
- Each further octave halves the amplitude and the wavelength. The octave amplitudes sum to the distortion.
- The warp noise seeds from the lattice seed. A distortion of 0 shifts nothing.
- The sea level of each island dimension is at or below the lowest Y of lava. So no lava fills the void. `IslandGameTests` checks it.
- The surface rules are the vanilla rules of each dimension. So an island top gets the vanilla surface of its biome.
- The pillars coexist with the islands. The pillar step replaces island blocks.
- The Islands preset has the customize screen "Aerolite Islands", `IslandsPresetScreen`. It is the Aerolite Void screen with the header Islands and the cell boundary distortion slider above the dimension rows.
- The distortion slider runs from 0 to 32 blocks in steps of 2. `VoronoiIslands` holds the bounds.
- The slider starts at the distortion of the island density function of the first preset dimension that has one.
- Done writes the distortion through `IslandGenerators`. A dimension whose island density function has the distortion stays as it is.
- Any other dimension with an island density function gets direct generator settings. Only its two terrain functions hold the new island density function. The other router functions keep their registry references.
- The world saves the direct generator settings in `level.dat`.
- The Islands screen has no preview.
- Island types, inner cells, cell merging, island bridges and center structures are 0.6.0 work.
- The island types of 0.6.0 are compositions of density functions in a node editor with a preview. The output node is the noise router with its 15 inputs.
- `llm/handoffs/0.5.0-islands.md` on `claude/islands-handoff` holds the earlier island decisions. The scope reset of 2026-11-06 supersedes its island types, shapes and merge rules.
- The prototypes under `prototypes/` of the island shapes stay as reference.

## Config

Use three config files:

- Common config: `applyDefaultPillars`. See [Default pillars](#default-pillars).
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
- The JUnit tests also cover the patterns and the ring descriptions.
- Put all tests in `src/test`.
- The GameTest server run loads the test source set as part of the mod.
- GameTests use the template `aerolite:empty`, a 1x1x1 structure in the test resources.
- The JUnit tests in `world/shaft/surface/path/` check the band, the band checks and the groove graph. They also check the spanning forest, the loop rationing and the surface paths.
- `TestPatterns` holds the synthetic patterns of those tests.
- The JUnit tests of Bricks, Tiles and Scales run the `--check` modes of their prototypes on the real patterns. `PatternChecks` holds the checks.
- The Chaos JUnit tests run the checks of the Chaos prototype. They also check the Chaos segments through the surface model.
- The maze JUnit tests check each maze type, the merged junction segment and the two-ring maze. They also check the junction link of each shape.
- The shape JUnit tests compare the I-beam ring and the Bridge rings with the tables of the shapes prototype.
- `PillarRingTest` compares the Pillar ring description with the tables of `Ring`.
- The settings JUnit tests check the pattern settings JSON, the provider codec and comparison, and the variation JSON.
- The client JUnit tests check the pattern edit rules, the provider drafts, the provider text and the panel tabs.
- GameTests check the pattern bindings in the bean context and the blocks that the paint writes for each pattern.
- `IslandGameTests` checks the Islands preset, the seed wiring of the island function, the surface floor, the distortion edit and the blocks of a generated island chunk.
- The island JUnit tests check the lattice and the island density.
- The shape GameTests check the cross sections, the trusses, the hole and the column web plane. They also check erosion and the one crossing of a horizontal Bridge pillar.

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

When a new question appears, add it here and ask the user before you implement it.

- The capacity of `PatternCache` and of `ChaosScenes`. The owner has not picked the cache sizes.
- The patch area of the patterns on a Bridge ring. Chaos paints no patch area there. Bricks paints only the outer columns there. The owner has not picked the rule.
