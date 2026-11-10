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
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
public interface PreviewPanel {

    String IMGUIMC = "imguimc";

    /** True when ImGui loaded, so the preview can open. */
    boolean available();

    /** True while a text field of the panel takes the keyboard. */
    default boolean wantsTextInput() {
        return false;
    }

    /** True while the cursor is over a window of the panel, such as the view gizmo. */
    default boolean wantsMouse() {
        return false;
    }

    /** Beanification finds a bean method by its name only, so the method takes no parameters. */
    @Bean(dist = Dist.CLIENT)
    static PreviewPanel previewPanel() {
        if (!ModList.get().isLoaded(IMGUIMC)) {
            return () -> false;
        }
        return ImGuiPreviewPanel.create();
    }

}
