package us.drullk.aerolite.client.preview.imgui;

import foundry.imgui.api.ImGuiMC;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import us.drullk.aerolite.client.BlockSearch;
import us.drullk.aerolite.client.preview.ItemIconAtlas;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * An ImGui text field for one block state. While it has focus and text, a list of matches with item icons shows below it.
 * The list is a window over the panel, so it moves no other widget. It scrolls when the space below the field is too short.
 * A click on a match sets the text, parses it and returns the state.
 * A read-only field shows the type id of a provider that has no editor.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_bf29e15f-47bb-42fe-94f0-29ae22f43f5a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ImGuiBlockField {

    private static final int TEXT_CAPACITY = 256;
    private static final float INVALID_RED = 0.55F;
    /** The green and the blue of the red warning text. */
    private static final float WARN_GREEN_BLUE = 0.4F;
    /** The list keeps the field focused, so it never takes the focus when it appears. */
    private static final int LIST_FLAGS = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoDocking;

    private final ImString text = new ImString(TEXT_CAPACITY);
    @Nullable
    private BlockState shown;
    private boolean valid = true;
    private boolean open;
    /** True when the list was hovered or focused in the last frame. A click on a match takes the focus from the field. */
    private boolean listInUse;
    private List<Block> matches = new ArrayList<>();

    /** Draws the field to the right edge of the line. Returns the new state, or null when the state stays. */
    @Nullable
    BlockState draw(String id, String hintKey, BlockState state, BlockSearch search, ItemIconAtlas icons) {
        return this.draw(id, hintKey, state, search, icons, -1, false);
    }

    /**
     * Draws the field. Returns the new state, or null when the state stays.
     *
     * @param itemWidth the ImGui item width; a negative width ends that far left of the right edge
     * @param warn  true draws the text red
     */
    @Nullable
    BlockState draw(String id, String hintKey, BlockState state, BlockSearch search, ItemIconAtlas icons, float itemWidth, boolean warn) {
        if (state != this.shown && !this.open) {
            this.text.set(search.text(state));
            this.shown = state;
            this.valid = true;
        }
        if (!this.valid) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, INVALID_RED, 0.1F, 0.1F, 1);
        }
        if (warn) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1, WARN_GREEN_BLUE, WARN_GREEN_BLUE, 1);
        }
        ImGui.setNextItemWidth(itemWidth);
        boolean changed = ImGui.inputTextWithHint(id, I18n.get(hintKey), this.text);
        if (warn) {
            ImGui.popStyleColor();
        }
        if (!this.valid) {
            ImGui.popStyleColor();
        }
        boolean active = ImGui.isItemActive();
        float left = ImGui.getItemRectMinX();
        float bottom = ImGui.getItemRectMaxY();
        float width = ImGui.getItemRectSizeX();

        BlockState result = null;
        if (changed) {
            result = this.parse(search, state);
        }
        if (changed || ImGui.isItemActivated()) {
            this.matches = search.search(this.text.get());
            this.open = true;
        }
        if (!active && !this.listInUse) {
            this.open = false;
        }
        if (this.open && !this.text.get().isEmpty() && !this.matches.isEmpty()) {
            BlockState picked = this.drawList(id, left, bottom, width, search, icons);
            if (picked != null) {
                result = picked;
            }
        } else {
            this.listInUse = false;
        }
        return result;
    }

    /** Draws the text in a field that takes no input. No match list shows. */
    void drawReadOnly(String id, String text) {
        this.text.set(text);
        this.shown = null;
        this.valid = true;
        this.open = false;
        this.listInUse = false;
        ImGui.setNextItemWidth(-1);
        ImGui.inputText(id, this.text, ImGuiInputTextFlags.ReadOnly);
    }

    @Nullable
    private BlockState parse(BlockSearch search, BlockState state) {
        BlockState parsed = search.parse(this.text.get());
        this.valid = parsed != null;
        if (parsed == null || parsed == state) {
            return null;
        }
        this.shown = parsed;
        return parsed;
    }

    /** Draws the match list under the field, in desktop coordinates of the main viewport. Returns the picked state. */
    @Nullable
    private BlockState drawList(String id, float left, float top, float width, BlockSearch search, ItemIconAtlas icons) {
        ImGuiStyle style = ImGui.getStyle();
        // The icon covers whole framebuffer pixels, so each item texel stays a sharp square.
        float framebufferScale = ImGui.getIO().getDisplayFramebufferScaleX();
        float pixelScale = framebufferScale > 0 ? framebufferScale : 1;
        int cellPixels = icons.requestCell((int) Math.ceil(ImGui.getFrameHeight() * pixelScale));
        float row = cellPixels / pixelScale;
        int rows = Math.min(this.matches.size(), BlockSearch.MAX_MATCHES);
        float wanted = rows * row + (rows - 1) * style.getItemSpacingY() + 2 * style.getWindowPaddingY();
        ImGuiViewport viewport = ImGui.getMainViewport();
        float below = viewport.getPosY() + viewport.getSizeY() - top;
        float height = Math.max(row + 2 * style.getWindowPaddingY(), Math.min(wanted, below));

        ImGui.setNextWindowViewport(viewport.getID());
        ImGui.setNextWindowPos(left, top);
        ImGui.setNextWindowSize(width, height);
        BlockState result = null;
        boolean shownList = ImGui.begin(id + "_matches", LIST_FLAGS);
        try {
            if (shownList) {
                // The panel comes to the front on a click in the field. The list stays over it.
                imgui.internal.ImGui.bringWindowToDisplayFront(imgui.internal.ImGui.getCurrentWindow());
                for (Block block : this.matches) {
                    ItemIconAtlas.Cell cell = icons.cell(block.asItem());
                    if (cell != null && icons.target() != null) {
                        ImGuiMC.image(ImGuiMC.getColorTexture(icons.target()), row, row, cell.u0(), cell.v0(), cell.u1(), cell.v1());
                    } else {
                        ImGui.dummy(row, row);
                    }
                    ImGui.sameLine();
                    String blockText = search.text(block.defaultBlockState());
                    if (ImGui.selectable(block.getName().getString() + "##" + blockText)) {
                        this.text.set(blockText);
                        BlockState parsed = search.parse(this.text.get());
                        this.valid = parsed != null;
                        if (parsed != null) {
                            this.shown = parsed;
                            result = parsed;
                        }
                        this.open = false;
                    }
                }
                this.listInUse = ImGui.isWindowFocused() || ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
            }
        } finally {
            ImGui.end();
        }
        if (!this.open) {
            this.listInUse = false;
        }
        return result;
    }

}
