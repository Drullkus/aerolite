# Handoff: preview fixes

Date: 2026-10-29. For a new Claude session that dispatches Opus subagents. Main is at `ff0a007` (preview mode) or later. The owner approved this plan.

## How to run this handoff

1. Read `/Users/drullkus/.claude/CLAUDE.md` and the project `CLAUDE.md`. Read `llm/plans/2026-10-29_settings_clouds_variations_preview.md` for the history of the preview.
2. Run `python3 llm/tools/claude_transcript_to_md.py --print-path` from the repository root. Give that path to every subagent for the class Javadoc line.
3. Dispatch wave 1: packages E and F in parallel, each in its own worktree (`git worktree add -b claude/<topic> .claude/worktrees/<topic> main`). Their file sets are disjoint.
4. The owner applies each branch with `git cherry-pick -n main..claude/<topic>` and commits. Wait for both.
5. Dispatch wave 2: package G from the new main. It is alone, so it also carries every `CLAUDE.md` edit of this handoff.
6. Each subagent brief states: the worktree path, the file set it may touch, the decisions below that apply, the checks, the commit rule and the worktree removal. A subagent that needs a file outside its set stops and reports instead of editing it.

## Lessons from the preview package

- A client check that waits for a timeout and greps for exceptions cannot see a black or frozen window. Every client check reads pixels of the front buffer of the window (`glReadBuffer(GL_FRONT)` on framebuffer 0) and of the main render target, and logs them. A temporary `@EventBusSubscriber(modid = "aerolite", value = Dist.CLIENT)` class in `src/main` does that on `ClientTickEvent.Post`. The devtools mod is `lowcodefml` and scans no annotations, so a probe there never runs. The probe is deleted before the commit.
- A probe that opens screens from code: title screen, `CreateWorldScreen`, the Aerolite preset, the Preview button, then the panel. It logs what it sees and reads the front buffer at each step. The package D agent did this with a temporary client component.
- Dependency versions come from the maven metadata of the artifact, never from the Gradle cache. imgui-mc 2.0.0 blanked the window on this Mac; 2.0.5 works. `gradle.properties` holds `imguimc_version`.
- Beanification 1.6 finds a `@Bean` method by name only. A `@Bean` method takes no parameters.
- imgui-mc runs ImGui with multiple viewports. ImGui positions are desktop coordinates. Every window uses `ImGui.getMainViewport()` position and size and `setNextWindowViewport`.
- The Beanification class-transform Gradle task sometimes fails with a `NoSuchFileException` right after another build. Rerun once before treating it as a failure.
- Do not use `pkill`. Start a client with `timeout 150 ./gradlew runClient -Pmute`; exit code 124 is the expected end.

## Decisions

| Topic | Decision |
|---|---|
| Box lines | Each region box edge draws opaque. An edge with any pixel in front of a block inside the region draws at 75% translucency. The floor grid draws with the normal depth test. Corrected 2026-10-30: the depth-range draw is dropped. |
| Fog | Removed. No fog setup, no fog color math. The sky fill stays as it is. |
| Progressive scene | Chunks appear as they finish, in a spiral from the center chunk outward. One worker thread paints the chunks in spiral sequence. A pool of available cores minus one, at least one, bakes the sections. Baked sections hand over in spiral chunk sequence. The render thread uploads each finished chunk at once and shows it. |
| Collapsible dimensions | In the ImGui panel every dimension under Shaft Variations is a tree node that collapses. Default open. |
| Dropdown click | A click on a block in the ImGui dropdown sets the field and the variation. Bug fix. |
| Dropdown layout | The ImGui dropdown is a popup over the content, not a child in the layout. It pushes nothing. It scrolls when it is taller than the available space below the field, with a maximum height of eight rows. |
| View gizmo | ImGuizmo view manipulator (`ImGuizmo.viewManipulate`) in the top right corner of the game view, left of the panel. Interactive: a drag rotates the orbit camera, a click on a face snaps to that axis view. The binding in imgui-mc 2.0.5 ships `imgui.extension.imguizmo.ImGuizmo`. |
| Preview settings | The Preview section moves to the top of the panel. |
| Dimension radios | In the panel, a radio button sits left of each dimension name under Shaft Variations. It selects the previewed dimension. The dimension combo in the Preview section stays. The two stay equal. |
| Remove dimension | Both screens: a Remove button at the far right of each dimension header under Shaft Variations removes that dimension and its variations. |
| Unlisted dimensions | The boolean `shafts_in_unlisted_dimensions` goes away. A dimension not in the map gets no shafts. The codec ignores the old field. |
| Add dimension | Both screens: a dropdown of the dimensions that can be added, and an Add button. Candidates: every level stem key of the world being created, from the world creation context, minus the configured ones. When no candidate is left the dropdown is disabled and reads "All dimensions configured". A new dimension starts with its default list: Overworld, Nether and End get their defaults, any other dimension gets the stone variation with weight 1. |
| Preview of a removed dimension | The preview stays on that dimension and shows the empty region: sky, grid and outline, no shafts. |
| Menu labels | The vanilla screen shows the label of each block field in a text row above the row of fields. No tooltips. |
| Panel labels | The ImGui panel shows the label of each block field left of the field. No tooltips. |
| Vanilla dropdown | The vanilla screen has no combo widget. The add row is a dropdown widget over the candidates on the left and an Add button on the right. The dropdown opens a list on click and closes on a pick. Both are disabled with the text "All dimensions configured" when no candidate is left. Corrected 2026-10-30: no cycle button. |

## Wave 1, package E: scene

Branch `claude/preview-scene`. Files: `src/main/java/us/drullk/aerolite/client/preview/PreviewRenderer.java`, `PreviewBuilder.java`, `PreviewBlockGetter.java`. No other file. No `CLAUDE.md`, no lang.

- Remove the fog: `setupFog`, `fogColor`, the `FogRenderer` and `FogShape` imports and the fog bullets of the class Javadoc. Keep the sky fill and the end sky.
- Box edges per the decision: opaque, or 75% translucent when any pixel of the edge is in front of a block. The grid keeps the normal depth test.
- Progressive build:
  - `PreviewBuilder` paints chunks in a spiral from the center chunk: `(0,0)`, then the ring at distance 1, and so on to the radius. One paint thread.
  - A bake pool of `max(1, availableProcessors - 1)` threads bakes the sections of a painted chunk. Results hand over in spiral chunk sequence: a chunk is published when all its sections are baked and every earlier chunk in the spiral is published.
  - The output queue carries one `ChunkReady` per chunk with its baked sections, then one `Complete` at the end. `Progress` counts painted chunks and baked sections.
  - `PreviewRenderer.drain` uploads each `ChunkReady` as it arrives and draws the sections it has. The old sections of the previous generation go away when the first chunk of the new generation arrives.
  - A stale generation stops at the next chunk on the paint thread and drops its pending bakes.
  - The painter needs the neighbor chunks of a chunk for the maze crossings of shafts that reach into it. Keep the current region logic: paint the chunk, bake with a block getter that sees the painted neighbors. If baking a chunk needs painted neighbors for face culling at chunk borders, bake a chunk after its spiral neighbors are painted, or bake border faces without culling. State the choice in the report.
- Checks: compile, `./gradlew test`, a muted client run with a probe that opens the preview and logs that chunks arrive one by one (log the spiral index of each uploaded chunk) and reads the front buffer. Report the time to the first chunk and to completion at radius 12 and 32.

## Wave 1, package F: data and vanilla menu

Branch `claude/variations-dimensions`. Files: `src/main/java/us/drullk/aerolite/world/AeroliteSettings.java`, `ShaftVariation.java`, `src/main/java/us/drullk/aerolite/client/PresetEdits.java`, `AerolitePresetScreen.java`, `SettingsList.java`, `BlockField.java`, `src/datagen/java/us/drullk/aerolite/datagen/AeroliteLanguageProvider.java`, `src/generated/resources/assets/aerolite/lang/en_us.json` through `runData`, and the tests `AeroliteSettingsTest`, `ShaftLayoutTest`, `ShaftGameTests`. No `CLAUDE.md`. No file under `client/preview`.

- `AeroliteSettings`: drop `shaftsInUnlistedDimensions` and its with-method. `variations(key)` returns the list of the key or an empty list. The codec ignores `shafts_in_unlisted_dimensions` in old data. Add `withoutDimension(key)` and `withDimension(key, list)`.
- `ShaftVariation`: `defaultList(ResourceKey<Level> key)` returns the default list of the Overworld, the Nether or the End, else one stone variation with weight 1.
- `PresetEdits`: drop `DIMENSIONS`. Add `configuredDimensions(settings)` (the map keys in a stable sequence: Overworld, Nether, End first, then the rest by id), `addableDimensions(settings, Collection<ResourceKey<Level>> all)`, `withAddedDimension(settings, key)`, `withoutDimension(settings, key)`. Keep every current method that the ImGui panel calls, so wave 2 compiles against it.
- `SettingsList`: `addHeader(Component)` stays. Add `addHeader(Component, AbstractWidget trailing)` that puts a widget of width 20 at the right end of the header row, and a label row helper `addLabels(Component...)` for one to three labels above a field row at the same column widths as `addRow`.
- `AerolitePresetScreen`:
  - Remove the cycle button row of the boolean.
  - For every configured dimension: the header with a Remove button (text "−" or "Remove", key `createWorld.customize.aerolite.remove_dimension`) at the far right; then the variation rows. Above each row of three block fields, a label row with "Interior Block", "Maze Wall Block", "Maze Path Block" from the existing keys. No tooltips.
  - After the last dimension: the add row per the decision. Candidates come from `context.selectedDimensions().dimensions().keySet()`.
  - Remove and Add rebuild the list and keep the scroll position, as the variation Add and Remove do.
- Lang: `remove_dimension`, `add_dimension`, `all_dimensions_configured`. Remove `shafts_in_unlisted_dimensions`. Dimension names for keys outside the three: use the id path as the fallback text (a `Component.literal`).
- Tests: `AeroliteSettingsTest` covers the removed field (old data with the field still parses), `withoutDimension`, `withDimension` and the empty list for an unlisted key. `ShaftLayoutTest` and `ShaftGameTests`: the unlisted dimension test stays and expects no shafts without the flag.
- Checks: compile, test, runData (commit `src/generated`), runGameTestServer, a muted client run with a probe that opens the preset screen and reads the front buffer.

## Wave 2, package G: ImGui panel

Branch `claude/preview-panel`. Starts from main with E and F applied. Files: `src/main/java/us/drullk/aerolite/client/preview/imgui/ImGuiPreviewPanel.java`, `ImGuiBlockField.java`, new `ImGuiViewGizmo.java`, `src/main/java/us/drullk/aerolite/client/preview/OrbitCamera.java`, `PreviewScreen.java`, `PreviewDimension.java`, `AeroliteLanguageProvider.java` and the generated lang, and `CLAUDE.md`. Nothing else. If a change in `PreviewRenderer` or `PreviewBuilder` is unavoidable, stop and report.

- Section sequence: Preview, Shaft Placement, Shaft Surface, Clouds, Shaft Variations.
- Shaft Variations: no boolean. Per configured dimension (from `PresetEdits.configuredDimensions`) one line: a radio button (`ImGui.radioButton`) that is on when this is the previewed dimension and sets it when clicked, then a `treeNode` with the dimension name, then a small Remove button at the far right of the same line (`ImGui.sameLine` with the remaining width). The node holds the variation rows. After the dimensions: a combo of the addable dimensions and an Add button; disabled with "All dimensions configured" when empty. The Preview combo and the radios read and write the same `PreviewScreen` dimension index.
- `PreviewScreen`: `dimensions()` stays every level stem. A previewed dimension without variations builds an empty scene. Setting the dimension by key as well as by index.
- Labels left of each block field: `ImGui.text(label)` then `sameLine`, then the field with the remaining width. No tooltips.
- `ImGuiBlockField`: the dropdown is a popup (`ImGui.setNextWindowPos` under the field, `ImGui.beginPopup` or a tooltip-style window with `NoFocusOnAppearing` and `NoMove`, whichever keeps the text field focused while typing). Maximum height eight rows, scrollable. A click on an entry sets the text, parses it and applies the state through the callback, then closes the popup. Confirm the fix with a probe that clicks an entry and reads the variation back.
- `ImGuiViewGizmo`: `ImGuizmo.beginFrame()` once per ImGui frame, then `ImGuizmo.viewManipulate(float[] view, float length, ImVec2 position, ImVec2 size, int backgroundColor)` (the binding in imgui-mc 2.0.5 has this signature and a float variant) in a borderless ImGui window at the top right of the game view (main viewport position plus game view width minus the gizmo size minus a margin). The gizmo reads the orbit view matrix from `OrbitCamera` and writes yaw and pitch back when the matrix changes. `OrbitCamera` gains the view matrix getter and yaw and pitch setters it needs.
- Lang: keys for the new panel texts that the vanilla screen does not already have.
- `CLAUDE.md` edits, all of them:
  - World creation menu: remove the unlisted-dimensions bullets; state that a dimension without a variations list gets no shafts; add the Remove button, the add dropdown and its disabled text; state that the panel shows the Preview section first, that each dimension collapses, that a radio left of the dimension name selects the previewed dimension together with the Preview combo; state the labels above the fields in the menu and left of the fields in the panel; state the view gizmo.
  - Preview bullets: remove the fog bullet; the scene appears chunk by chunk in a spiral from the center; the box edges draw only over the background.
  - Glossary: nothing new.
- Checks: compile, test, runData, a muted client run with a probe that opens the preview, switches the dimension by radio and by combo, removes and adds a dimension, clicks a dropdown entry, drags the gizmo, and reads the front buffer after each step.

## Final hand-off to the owner

Each package ends with `git cherry-pick -n main..claude/<topic>` and a report of what is not verified. The owner checks the panel on the Retina display and on the external display.
