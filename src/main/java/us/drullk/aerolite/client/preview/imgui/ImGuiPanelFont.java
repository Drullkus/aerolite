package us.drullk.aerolite.client.preview.imgui;

import foundry.imgui.api.ImGuiMC;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.flag.ImGuiFreeTypeLoaderFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import us.drullk.aerolite.Aerolite;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * The font of the preview panel: the imgui-mc default font, rasterized by FreeType as one-bit glyphs.
 * The imgui-mc renderer bakes each font once at its load size and scales it to the drawn size.
 * So the panel font loads at the drawn size in framebuffer pixels, and each texel covers one pixel.
 * imgui-mc clears the atlas before each font event, so each event adds the font again.
 * The other ImGui windows keep the default font.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_21-56_ad766c27-abc7-4f8b-ae6a-53b92f85435c.md}.
 */
public final class ImGuiPanelFont {

    private static final ResourceLocation SOURCE = ImGuiMC.FONT_DEFAULT.withPath(path -> "imgui_font/" + path + "-regular.ttf");
    private static final int LOADER_FLAGS = ImGuiFreeTypeLoaderFlags.Monochrome | ImGuiFreeTypeLoaderFlags.MonoHinting;

    /** The drawn size of the panel text, in ImGui units. */
    private final float size;
    /** The font in the current atlas. Null until the first font event, or when the source did not load. */
    @Nullable
    private ImFont font;
    /** The framebuffer scale that the next font event bakes for. */
    private float scale = 1;
    /** The framebuffer scale of the current font. NaN until a rebuild is requested. */
    private float bakedScale = Float.NaN;

    public ImGuiPanelFont(float size) {
        this.size = size;
    }

    /** Adds the font to the atlas at the drawn size in framebuffer pixels. */
    public void register(ImFontAtlas atlas) {
        this.font = null;
        this.bakedScale = this.scale;
        byte[] bytes;
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(SOURCE)) {
            bytes = stream.readAllBytes();
        } catch (IOException e) {
            Aerolite.LOGGER.error("Failed to read the preview panel font {}", SOURCE, e);
            return;
        }
        // The binding cannot give one font its own loader, so the atlas uses FreeType for every font.
        atlas.setFreeTypeRenderer(true);
        ImFontConfig config = new ImFontConfig();
        try {
            config.setName("aerolite panel");
            config.setPixelSnapH(true);
            config.setFontLoaderFlags(LOADER_FLAGS);
            // The atlas copies the config and the font bytes.
            this.font = atlas.addFontFromMemoryTTF(bytes, this.size * this.scale, config);
        } finally {
            config.destroy();
        }
    }

    /** Requests one atlas rebuild when the framebuffer scale differs from the baked scale. */
    public void matchScale(float framebufferScale) {
        if (framebufferScale > 0) {
            this.scale = framebufferScale;
        }
        if (this.scale != this.bakedScale) {
            this.bakedScale = this.scale;
            ImGuiMC.rebuildFonts();
        }
    }

    /** The panel font, or the current font before the first font event. */
    public ImFont get() {
        return this.font != null ? this.font : ImGui.getFont();
    }

}
