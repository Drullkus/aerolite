package us.drullk.aerolite.client;

import us.drullk.aerolite.client.preview.ItemIconAtlas;
import us.drullk.aerolite.client.preview.PreviewPanel;
import us.drullk.aerolite.client.preview.PreviewViewport;
import us.drullk.aerolite.world.island.IslandGenerators;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

/**
 * The components that the shafts screen, the dimension screen and the preview screen use. The client component builds it from its own components.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public record PresetTools(PresetSliders sliders, PresetEdits edits, BlockSearch blocks, ShaftPainter painter, ShaftMazes mazes,
                          PreviewPanel panel, PreviewViewport viewport, AeroliteClientConfig config, ItemIconAtlas icons,
                          SettingsJson json, ShaftSettingsHolder holder, ProviderText providerText, IslandGenerators islands) {
}
