# Aerolite

Aerolite is a NeoForge world preset. The world looks like broken reinforced concrete. Terrain islands are the concrete. Shafts are the rebar that holds them.

The global rules in `~/.claude/CLAUDE.md` apply. This file adds the rules for Aerolite.

## Glossary

| Term | Meaning |
|---|---|
| Preset | The Aerolite world preset. It replaces the generator of the Overworld, the Nether and the End. |
| Settings | The values that the player sets in the world creation menu. The settings holder keeps them until the server of the new world claims them. |
| Settings holder | The client component that holds the settings of the open world creation flow. |
| Pending settings | The settings that the world creation flow hands to the integrated server of the new world. |
| Own variation list | The variation list `aerolite:<dimension path>` of a dimension. The Nether and the End use `aerolite:nether` and `aerolite:end`. |
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
| Variation | One set of maze bias, decay scale, decay threshold, erosion threshold, interior block, maze wall block and maze path block. Each shaft and column picks one. |
| Variation list | A map of variation id to weight. Its key sequence is the pick sequence. |
| Shaft data | The placement and the weighted variations of one dimension. |
| Data map | A NeoForge data map. It attaches a value to a registry entry by id. |
| World shaft pack | The world data pack `aerolite_shafts`. It holds the generator settings of a world as shaft data. |
| Maze bias | The weight of axis edges in the maze sampler, 5% to 95%. Ring edges take the rest. 50% is uniform. |
| Segment | One chunk length of a shaft. On a column it is one chunk section. Tree segments and connector segments alternate along the shaft. |
| Tree segment | A segment whose nodes form one spanning tree. It has no crossing of its own. |
| Connector segment | A segment whose nodes and two virtual nodes form one spanning tree. |
| Virtual node | A stand-in for one neighbor tree during sampling. It touches every interior node of that tree on the facing ruling. |
| Crossing | A tree edge at a virtual node. It is the maze path between the two sampled parts. |
| Junction segment | The segment that holds a junction. It spans the chunks with the nearest surviving ruling on each side of the box, two or three chunks. Its graph drops the box rulings and adds the shaft patch. |
| Patch | One of the two exposed faces of a junction, a 7x7 node grid. The shaft patch is part of the junction segment of the horizontal shaft. The column patch is part of the junction segment of the column. |
| Decay | Fractal Brownian simplex noise that removes maze walls or maze paths from a region of a shaft. Each variation sets its own decay scale and thresholds. |
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
- The chunk paint of `junction.py` paints the junction scene chunk by chunk, like the mod. `--chunk-stats` counts the exposed interior blocks of each paint rule.
- Before you design or change an island shape, point the user at Earth photographs. See the global rule.

## Dimension

- The preset is "Aerolite Void". Its key is `aerolite:void`. Its lang key is `generator.aerolite.void`.
- The preset is in `#minecraft:normal`. It shows in the world type list without Alt.
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
- The world shaft pack holds that cloud height as the `aerolite:cloud_height` value of `aerolite:overworld`. Later sessions read it from the data map.
- Only the dimension type `aerolite:overworld` ships a cloud height, Y -32.
- The packet carries the cloud height of the shaft data of the player's level. A level without a cloud height sends no packet.
- A packet carries the cloud height to the client on login, on respawn and on dimension change.
- The client effects of `aerolite:overworld` put the clouds at the cloud height of the last packet.
- Each dimension uses its own vanilla biome source. Overworld biomes in the Overworld, Nether biomes in the Nether, End biomes in the End.
- A biome that the player picks in the Aerolite Void screen replaces the biome source of its dimension with a fixed biome source.
- Each dimension has the same structure: shafts, islands, bridges and center structures.
- The generator of a new world carries no shaft settings.
- The Aerolite generator `aerolite:aerolite` stays registered for the worlds of 0.2. Its field `aerolite` carries their settings.
- The shafts step does not read the generator. It paints any level with shaft data, whatever its generator.
- The noise settings of each dimension make only air. The islands replace the void later.
- The void Overworld has a sea level of -64. A higher sea level makes the noise step fill the bottom with lava.
- The shafts are the chunk status `aerolite:shafts`, between the carvers and the features. Two mixins add the status and its step, because no API adds a chunk status.
- The features step waits for the shafts of its neighbor chunks. Features decorate the shafts.

## World creation menu

The player sets the shaft settings in the World tab of the create world screen. The world type does not matter.

### Settings

- The cloud height is one setting of the whole preset. It is not a shaft placement value.
- Each dimension has its own shaft settings: one shaft placement and one variation list.
- The shaft placement holds the minimum shaft spacing, the maximum shaft spacing and the column chance.
- Each variation holds its own decay scale, decay threshold and erosion threshold.

| Setting | Scope | Default |
|---|---|---|
| Voronoi cell size | Preset | About 128 blocks |
| Island fill fraction, lower bound | Preset | 0.3 |
| Island fill fraction, upper bound | Preset | 0.9 |
| Island height band | Preset | Y 0 to 128 |
| Island type weights (landmass, biosphere, lake bowl, donut) | Preset | 8, 1, 1, 1 |
| Bridge chance | Preset | 0.5 |
| Allowed biome list | Preset | The vanilla biome list of the dimension |
| Cloud height | Preset | Y -32 |
| Minimum shaft spacing | Dimension | The placement entry of the dimension, else `aerolite:default` |
| Maximum shaft spacing | Dimension | The placement entry of the dimension, else `aerolite:default` |
| Column chance | Dimension | The placement entry of the dimension, else `aerolite:default` |
| Variation list | Dimension | The own variation list of the dimension. `AeroliteShaftEntries.java` writes the shipped lists. |
| Maze decay scale | Variation | 64 blocks |
| Maze decay threshold | Variation | 0.35 |
| Shaft erosion threshold | Variation | 0.5 |

- Each island picks its fill fraction at random between the two bounds.
- The island height band is the Y range of the island centers in the generating layer.
- The shaft spacing is the center distance of two neighbor horizontal shafts on one axis. It stays between the minimum and the maximum.
- The maze decay scale is the size of one decay region along a shaft.
- The dimension settings are a map from dimension to shaft placement and variation list.
- Every variation list holds at least one variation with a positive weight.
- A dimension without an entry in the map gets no shafts.
- A variation lists its blocks in the sequence interior block, maze wall block, maze path block.
- A save writes the layout of 0.2.0: the cloud height and the dimension map.
- A save with a dimension map loads that map. It ignores the top-level fields of 0.1.0.
- A 0.1.0 save holds the placement and the decay values at the top level, next to a map of variation lists.
- A 0.1.0 save loads with its top-level placement in every dimension of its map.
- In a 0.1.0 save, a variation without its own decay values takes the top-level decay values.
- A 0.1.0 save without a variation map loads the default dimensions with its top-level values.
- A 0.1.0 save without a variation map reads the shipped lists, so it parses only with registry ops.
- A variation with a decay threshold at or above its erosion threshold loads with the erosion threshold raised to the decay threshold plus 5%.
- The other preset settings have no slider and no storage yet.

### Settings holder

- The client component `ShaftSettingsHolder` holds the settings of the open world creation flow.
- A create world screen that the holder does not track starts a new flow. The holder then holds blank settings.
- Blank settings hold no dimension and the default cloud height.
- The shafts screen, the dimension screen and the preview read and write the holder. No screen reads or writes the generator.
- The title screen and the world list end the flow. When one of them opens, the holder resets and clears the pending settings.
- Cancel in the create world screen returns to one of those screens. So a cancelled flow leaves blank settings and no pending settings.

### Hand-over to the server

- `PendingShaftSettings` is a common component. The client offers settings to it. The server claims them.
- The flow opens the screen "Preparing world data" when it creates the world. That screen hands the holder settings to the pending settings.
- Blank settings are not handed over. So a world created with blank settings gets no shafts and no world shaft pack.
- The hand-over happens before the integrated server starts.
- The server claims the pending settings at `ServerAboutToStartEvent`, before it loads the levels.
- The server claims them only for a new world. A world is new while its Overworld data is not initialized.
- The server posts the Overworld load event before it sets the spawn and the initialized flag. It posts the other level load events after.
- So the server claims once at its start, not at each level load.
- An old world drops the pending settings. It claims nothing.
- The server stop clears the pending and the claimed settings.
- A dedicated server gets no offer. Its pending settings stay empty.
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
- Settings opens the shafts screen with the holder.
- The row holds no Defaults button. The Defaults button is in the header of the shafts screen.
- The own variation list of a dimension is `aerolite:<dimension path>`.
- The Nether and the End use `aerolite:nether` and `aerolite:end` as their own lists.
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
- The component `VoidPresetBiomes` builds the dimensions. A GameTest checks it.
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
- A dimension other than the three vanilla dimensions shows its id path as its name.

### Shafts screen

- The shafts screen has the title "Amazing Pillars" and a footer.
- The shafts screen edits a copy of the holder settings. Done writes the copy to the holder. Cancel and Escape drop it.
- Its list holds the add row first, then one row per configured dimension.
- The add row holds the dimension dropdown, the list dropdown and the Add button, in that sequence.
- The dimension dropdown lists every dimension of the world that has no dimension settings.
- The list dropdown lists "Blank" first, then every entry of `aerolite:variation_list` by its id path.
- The lists follow the sort sequence of `ResourceLocation`: path first, then namespace.
- The list dropdown reads the variation lists from the worldgen registries of the creation context.
- When the chosen dimension has its own variation list, the list dropdown preselects that list. Else it preselects "Blank".
- A pick in the dimension dropdown preselects the list again.
- A click on a dropdown opens its list. A pick closes the list.
- Add with a list adds the chosen dimension with the weights of that list.
- Add with "Blank" adds the chosen dimension with one stone variation, `aerolite:stone`, with weight 1.
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
- Defaults acts on the copy of the settings of the shafts screen. It rebuilds the list and keeps the scroll position.
- Copy writes the settings to the system clipboard as one line of JSON. Only the settings codec knows the fields.
- Copy and Paste use registry ops over the worldgen registries of the creation context.
- Paste parses the clipboard at once. A good parse replaces the settings and rebuilds the list.
- A bad parse shows a system toast with the title "Paste failed" and the parse error. The settings stay.
- The dimension screen has no Copy button and no Paste button for the whole settings.

### Dimension screen

- The dimension screen edits a copy of the settings.
- Its title is "Amazing Pillars: " and the dimension name. The title shows below the tab bar.
- A tab bar at the top holds two tabs: Shaft Placement and Shaft Variations.
- The footer holds Done and Cancel.
- Done writes the edited settings back to the shafts screen. Cancel and Escape drop them.
- The tab Shaft Placement holds the column chance slider, then the two spacing sliders in one row.
- The tab Shaft Variations holds the rows of each variation, then the Add Variation button.
- The rows of a variation are, from top to bottom: the weight slider and the maze bias slider, the decay scale slider, the decay threshold slider and the erosion threshold slider, the label row, the three block fields, and the button row.
- The button row holds the Copy button (export), the Paste button (import) and the Remove button, in that sequence.
- The column chance slider runs from 0% to 100% in steps of 5%.
- The spacing sliders run from 32 to 1024 blocks in steps of 16.
- A spacing slider that passes the other spacing slider pushes it along.
- The weight slider runs from 0 to 16. The last positive weight of a dimension does not go below 1.
- The maze bias slider runs from 5% to 95% in steps of 5%.
- The maze bias label says "ringwise" below 50%, "uniform" at 50% and "lengthwise" above 50%.
- The decay scale slider steps through the powers of two from 32 to 1024.
- The decay threshold slider runs from 0% to 100% in steps of 5%. 100% is a threshold of 1.
- The erosion threshold slider runs from 5% to 100% in steps of 5%.
- The erosion threshold of a variation stays at least 5% above its decay threshold. A slider that passes the other pushes it along, so the decay threshold tops out at 95%.
- The three block fields are, from left to right, the maze wall block, the maze path block and the interior block.
- A text row above the block fields shows the label of each field. A block field has no tooltip.
- A block field reads a block id or a block state string, such as `oxidized_copper` or `oak_log[axis=x]`.
- Bad text gives the block field a red border. The field keeps its last good block.
- While a block field has focus and text, a dropdown lists up to 8 blocks whose id or name holds the text. A click or Enter picks one.
- The dropdown shows the item icon and the name of each block. It lists no air block.
- A dimension with one variation has its Remove button off.
- Export writes one variation to the system clipboard as one line of JSON.
- The JSON of one variation has the fields of a variation entry: bias, decay_scale, decay_threshold, erosion_threshold, interior, wall and path.
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

### Preview

- The footer of the shafts screen has a Preview button between Done and Cancel.
- Each footer button of the shafts screen is half the vanilla default button width.
- The Preview button shows only when ImGui loaded.
- The Preview button opens the preview screen with a copy of the settings.
- Done in the preview sends the copy back to the shafts screen. Cancel drops the copy.
- The preview shows the shafts of a square region of chunks around the region center.
- The region center is a world X and Z position in blocks. It starts at the origin.
- The region is the square of `2 * radius + 1` chunks around the chunk that holds the region center.
- The preview paints the shafts with the shaft step code into scratch proto chunks. The vanilla block renderer bakes the blocks.
- An ImGui panel covers the right edge of the window. The game view fills the part left of the panel.
- A splitter on the left edge of the panel sets the panel width, from 240 to 600 ImGui units.
- The panel edits the settings of the menu screens, with the same slider steps and the same rules.
- The panel shows the Preview section, then one collapsing header per configured dimension, then the add row.
- Each dimension header starts open.
- The id of a dimension header does not change in a rebuild. So the header keeps its open state after an add or a remove.
- Each dimension header holds two groups that start open: Shaft Placement and Shaft Variations.
- The group Shaft Placement holds the column chance slider and the two spacing sliders.
- The group Shaft Variations holds the variation rows and the Add Variation button.
- Each variation row of the panel shows the sliders in the menu sequence: weight, maze bias, decay scale, decay threshold, erosion threshold.
- The block fields follow the sliders. The Export button, the Import button and the Remove button follow on one line.
- Export and Import in the panel act like Export and Import of the dimension screen, with the same tooltips.
- The panel applies a variation removal after its row loop. So each row of the frame reads its own index.
- Each ImGui end call of the panel runs in a `finally` block. So an exception leaves no ImGui window open.
- A good import in the panel replaces the variation in the preview copy and starts a rebuild.
- A radio button left of each dimension name selects the previewed dimension.
- The radio buttons and the dimension dropdown of the Preview section always show the same dimension.
- A small Remove button sits at the right end of each dimension header in the panel.
- After the dimensions, the panel has a dimension combo, a list combo and an Add button.
- The list combo lists "Blank" and every variation list by its id path. It follows the rules of the list dropdown of the shafts screen.
- When no dimension is left to add, both combos and the Add button are off. The dimension combo reads "All dimensions configured".
- The panel shows the label of each block field left of the field. A block field has no tooltip.
- The panel shows the block fields in the sequence of the menu, from top to bottom.
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
- Paste in the panel replaces the preview copy and starts a rebuild. A bad parse shows the toast of the shafts screen.
- Defaults in the panel follows the rule of the Defaults button of the shafts screen.
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
- The seeded shaft layout gives the world positions of the shafts. So a pan shows other parts of the same world.
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

The shaft data lives in datapack registries and data maps. See the glossary for the terms.

### Registries

- Three datapack registries hold the shaft data. They do not sync to the client.
- `aerolite:shaft_variation` holds the variations. The shipped entries are `aerolite:stone`, `aerolite:tuff` and the others in `AeroliteShaftEntries.java`.
- A variation entry holds `bias`, `decay_scale`, `decay_threshold`, `erosion_threshold`, `interior`, `wall` and `path`. It holds no weight.
- The variation codec clamps the bias to the slider range.
- The variation codec raises an erosion threshold at or below the decay threshold to the decay threshold plus 5%.
- `aerolite:shaft_placement` holds the placements. A dimension takes the entry under its own dimension id.
- A dimension without its own placement entry takes `aerolite:default`.
- The placements of the vanilla dimensions are in the namespace `minecraft`, for example `data/minecraft/aerolite/shaft_placement/overworld.json`.
- `aerolite:variation_list` holds `aerolite:overworld`, `aerolite:nether` and `aerolite:end`. Each entry is a variation list.
- The menu copies from the variation lists. Worldgen reads them only for a generator without the field `aerolite`.
- A change to a shipped variation list changes the shafts of each world whose generator has no field `aerolite`.
- The JSON key sequence of a variation list is the pick sequence. A tool that sorts the keys changes the shafts.
- Data generation writes the variation lists with their key sequence. The other generated JSON has sorted keys.

### Data maps

- The data map `aerolite:shaft_variations` is on the level stem registry. Its value is a variation list.
- Its file is `data/<namespace>/data_maps/dimension/shaft_variations.json`. The keys are dimension ids.
- It ships without values. So no dimension gets shafts from the shipped data alone.
- The world creation screen applies data maps to a level stem registry without the dimensions of the new world.
- So `DimensionShaftValues` reads the files of `aerolite:shaft_variations` by dimension id, with the merge rules of NeoForge.
- `DimensionShaftValues` skips a tag entry, because a tag cannot resolve without the dimensions.
- The data map `aerolite:cloud_height` is on the dimension type registry. Its value is a Y level.

### Level binding

- The level load resolves the shaft data of each server level. A data attachment of the level holds it. The level does not save it.
- The shafts step and the cloud height packet read the attachment.
- The binding resolves once per level load. A reload of the data packs does not change it.
- The project timed a mixin field on the server level against the attachment. Per chunk, the difference stayed below the noise.
- So the attachment holds the shaft data. The project rule allows a mixin only when no API can do the task.
- A data map value for the dimension wins over the pending settings and the generator settings.
- Without a data map value, a level takes the claimed pending settings when they list its dimension.
- The server claims pending settings only for a new world. See [Hand-over to the server](#hand-over-to-the-server).
- Claimed pending settings give their cloud height to each level of the dimension type `aerolite:overworld`, listed or not.
- Without a data map value and without pending settings for it, a level takes the generator settings for its dimension. Its generator must be the Aerolite generator, and the settings must list the dimension.
- A server with claimed pending settings writes the world shaft pack from them at the first level load.
- That pack holds each listed dimension without a data map value, and the cloud height of the pending settings.
- A new world gets vanilla noise generators. So only a world of 0.2 takes generator settings.
- A level that takes the generator settings and has the dimension type `aerolite:overworld` takes the cloud height of the generator settings.
- A generator without the field `aerolite` takes the default settings. They come from the shipped variation lists and placements.
- A 0.1.0 save without a variation map reads the default lists from the registries of the save.

### World shaft pack

- A level that takes the generator settings writes the world shaft pack to `<world>/datapacks/aerolite_shafts/`.
- The level load writes the pack once. A world with a folder `datapacks/aerolite_shafts` gets no new pack.
- The game enables the new pack on the next world load. From then on, the data maps give the shaft data.
- The writer puts the partial pack in `<world>/aerolite_shafts.tmp`. Then it moves the pack into the data pack folder.
- The pack holds a value for each dimension whose Aerolite generator lists it and that has no data map value.
- `pack.mcmeta` holds the data pack format of the game.
- `data/aerolite/data_maps/dimension/shaft_variations.json` holds the variation list of each of those dimensions.
- `data/<dimension namespace>/aerolite/shaft_placement/<dimension path>.json` holds the placement of each of those dimensions.
- A variation equal to a shipped entry refers to that entry. Each dimension list refers to one entry at most once.
- Any other variation gets the entry `aerolite_world:<dimension namespace>/<dimension path>/<index>`. The index is its position in the list.
- `data/aerolite/data_maps/dimension_type/cloud_height.json` holds the cloud height of the Overworld generator for `aerolite:overworld`.

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
- The pick walks the variation list in its sequence. The same seed, weights and sequence give the same shafts.
- A horizontal shaft picks with a random at its axis and cell. A column picks with a random at the cells of its two shafts.
- A junction box takes the variation of its horizontal shaft. So do its two patches and the maze path blocks on them.
- A dimension without shaft data gets no shafts. The noise settings of the dimension still make its terrain.
- `ShaftLayoutTest` holds a fingerprint of the default shafts of one seed. A change that moves a shaft fails it.
- Shafts pierce islands. Each solid block of the finished shaft replaces the terrain block at its position.
- The shaft paint writes only solid blocks. A block of the chunk stays where the finished shaft is air.
- The shaft paint computes the finished shaft in a buffer. It does not read the chunk.

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
- A maze path at a chunk edge can have its maze path block in the next chunk. Each chunk writes the blocks of a maze path that lie in it.
- The maze grid of a chunk holds the maze grid blocks up to two blocks outside the chunk. So both chunks decay one maze path the same way.
- Decay is one fractal Brownian simplex noise over the shaft. Far negative values keep only maze walls. Far positive values keep only maze paths. Central values keep both.
- The decay threshold t sets the regimes. Noise below -t keeps only maze walls. Noise above t keeps only maze paths.
- A maze wall or maze path block that decay removes leaves no shaft block. A removed path exposes the interior block.
- Decay acts on the 13x13 inside of each patch. On a patch, the box block that decay exposes becomes the maze path block.
- Erosion starts where the absolute decay noise passes the erosion threshold, at either end of the noise.
- The reach is the absolute noise, normalized from the erosion threshold to 1.
- Each block has an erosion resistance from 0 to 1. Erosion removes a block when the reach exceeds its resistance.
- In a shaft, the resistance rises over 5 layers from the outer layer inward. The 3x3 core has a resistance of 1, so it never vanishes.
- In a junction box, the box resistance rises over 6 layers from the box face inward. The 3x3x3 core has a resistance of 1.
- A box block takes the largest of the box resistance and the resistance of each shaft whose cross section holds it. So erosion never cuts a shaft at a junction.
- Erosion samples the noise at each block, so one side of a shaft can erode deeper than the other.
- Erosion runs after decay and before shear. An eroded block leaves no shaft block.
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
