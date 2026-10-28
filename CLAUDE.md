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
| Junction | The piece where a column crosses a horizontal shaft. |
| Interior | The 11x11 solid core of a shaft. |
| Outer layer | The one-block skin around the interior. It holds the maze. |
| Maze wall | A cell of the maze that keeps its outer layer block. |
| Maze path | A cell of the maze with no outer layer block. The block under it is the maze path block. |
| Decay | Fractal Brownian simplex noise that removes maze walls or maze paths from a region of a shaft. |
| Shear | Noise that removes blocks from a column near a build limit. |
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
- Declare each compat feature as an interface.
- Give each interface a `@Bean` static method that checks `ModList` and returns one implementation.
- Return a no-op implementation when the mod is absent.
- Do not annotate an optional-mod implementation with `@Component`. Create it only in the `@Bean` method.
- Use `@Configurable` and `@Autowired` fields in blocks, items and block entities.
- Do not use an `@Autowired` field in a constructor. Injection occurs at the end of the constructor.
- Use constructor injection when a constructor needs a bean.
- In JUnit tests, extend with `MockitoFixer` and `MockitoRunner` from `tamaized.beanification.junit`. Use Mockito `@Mock` fields.
- Do not use `BeanContextJunitExtension` or `@MockBean`. JUnit runs in the game's module layer, and that extension is in a split package.

### Veil

- Use Veil for the ImGui panel.
- Use Veil for custom block and entity rendering, for example the tree block.
- Do not use Veil for the sky. Each dimension keeps its vanilla sky.

## Mixins

- Use APIs, events and capabilities first.
- Write a mixin only when no API, event or capability can do the task.
- Add a comment to each mixin. The comment tells why no API can do the task.

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
- The Markov maze is a standalone program in `prototypes/maze/`. Prototype it there before you write any maze code in the mod.
- Before you design or change an island shape, point the user at Earth photographs. See the global rule.

## Dimension

- The preset is in `#minecraft:normal`. It shows in the world type list without Alt.
- The preset gives the Aerolite generator to the Overworld, the Nether and the End.
- Each dimension keeps its vanilla dimension type. The Overworld has its sun, moon, day cycle and weather. The Overworld spans -64 to 320.
- Each dimension uses its own vanilla biome list. Overworld biomes in the Overworld, Nether biomes in the Nether, End biomes in the End.
- Each dimension has the same structure: shafts, islands, bridges and center structures.

## World creation menu

The player tweaks the settings in the world creation menu, like the superflat customize screen. The settings are:

| Setting | Default |
|---|---|
| Voronoi cell size | About 128 blocks |
| Island fill fraction, lower bound | 0.3 |
| Island fill fraction, upper bound | 0.9 |
| Island height band | Y 0 to 128 |
| Island type weights (landmass, biosphere, lake bowl, donut) | 8, 1, 1, 1 |
| Shaft spacing | 256 blocks |
| Column chance | 0.25 |
| Maze decay scale | 256 blocks |
| Shaft interior block | Stone |
| Maze wall block | Stone bricks |
| Maze path block | Cobblestone |
| Bridge chance | 0.5 |
| Allowed biome list | The vanilla biome list of the dimension |

The three shaft blocks are placeholders. The producer swaps them later.

- Each island picks its fill fraction at random between the two bounds.
- The island height band is the Y range of the island centers in the generating layer.
- The shaft spacing is the mean distance between horizontal shafts along one axis.
- The maze decay scale is the size of one decay region along a shaft.

## Shafts

### Placement

- Along X, a seeded sequence of X positions exists. At each position, one Z-aligned horizontal shaft runs at one seeded height.
- Along Z, a seeded sequence of Z positions exists. At each position, one X-aligned horizontal shaft runs at one seeded height.
- A horizontal shaft keeps one height for its full length.
- Shafts have no elbows. A shaft never turns and never steps.
- An X shaft and a Z shaft never intersect.
- The build height is cut into slots of 16 blocks. The seed shuffles which slots belong to X shafts and which to Z shafts.
- A horizontal shaft picks one slot of its axis. Inside the slot it picks an offset of 1 or 2, so at least one block of air separates it from a shaft in the next slot.
- A column can stand where an X shaft and a Z shaft cross in plan view. The column chance setting decides which crossings get one.
- Columns span the full build height.
- Shear thins each column near the top and bottom build limits. No column ends in a flat cut.
- Near a build limit, a noise threshold removes blocks from the column. The threshold rises toward the limit, so fewer blocks remain the closer they are to it.
- Where a column crosses a horizontal shaft, a junction joins them. The junction is a 15x15x15 box of the shaft interior block, centered on the crossing.
- Shafts pierce islands. Where a shaft and an island overlap, the shaft replaces the terrain.

### Cross section

- The outside is 13x13. The interior is 11x11 and solid. The interior uses the shaft interior block.
- The outer layer is one block thick. It holds the maze.

### Maze

- The maze comes from Markov rewrite rules, as in <https://youtu.be/zbXKcDVV4G0>.
- The rules run on a 2D grid that wraps around the four faces of a shaft.
- A maze wall cell keeps its outer layer block. That block is the maze wall block, at the 13x13 extent.
- A maze path cell has no outer layer block. The block under it, at the 11x11 extent, is the maze path block.
- Decay is one fractal Brownian simplex noise over the shaft. Far negative values keep only maze walls. Far positive values keep only maze paths. Central values keep both.
- A maze wall or maze path block that decay removes becomes air. A removed path exposes the interior block.
- The rule set is not fixed. The prototype in `prototypes/maze/` tries rule sets, and the user picks one from its output images.

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

Tree growth is not a config value. It is the game rule `walkerTreeGrowChance`.

## ImGui panel

- The panel shows the tree block render settings of the client config and edits them live.
- F12 toggles the panel. The key mapping is `key.aerolite.toggle_panel`.

## Sounds

Aerolite adds no sounds. Blocks use vanilla sound types.

## Tests

- Write NeoForge GameTests for world generation results. Run them with `./gradlew runGameTestServer`.
- Write JUnit tests for pure logic: the Voronoi, the signed distance functions, the maze rules and the walker.
- Put all tests in `src/test`.

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
