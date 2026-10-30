package us.drullk.aerolite.client.preview;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import tamaized.beanification.Bean;
import us.drullk.aerolite.client.preview.imgui.ImGuiPreviewPanel;

/**
 * The ImGui panel of the preview screen. Without imgui-mc the panel does nothing and the preview is not available.
 * The panel takes the components of the open preview screen.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public interface PreviewPanel {

    String IMGUIMC = "imguimc";

    /** True when ImGui loaded, so the preview can open. */
    boolean available();

    /** Beanification finds a bean method by its name only, so the method takes no parameters. */
    @Bean(dist = Dist.CLIENT)
    static PreviewPanel previewPanel() {
        if (!ModList.get().isLoaded(IMGUIMC)) {
            return () -> false;
        }
        return ImGuiPreviewPanel.create();
    }

}
