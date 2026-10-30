package us.drullk.aerolite.client;

import us.drullk.aerolite.client.preview.ItemIconAtlas;
import us.drullk.aerolite.client.preview.PreviewPanel;
import us.drullk.aerolite.client.preview.PreviewViewport;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

/**
 * The components that the preset screen and the preview screen use. The client component builds it from its own components.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public record PresetTools(PresetSliders sliders, PresetEdits edits, BlockSearch blocks, ShaftPainter painter, ShaftMazes mazes,
                          PreviewPanel panel, PreviewViewport viewport, AeroliteClientConfig config, ItemIconAtlas icons) {
}
