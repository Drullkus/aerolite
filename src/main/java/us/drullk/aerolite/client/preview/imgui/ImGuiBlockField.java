package us.drullk.aerolite.client.preview.imgui;

import foundry.imgui.api.ImGuiMC;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
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
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
final class ImGuiBlockField {

    private static final int TEXT_CAPACITY = 256;
    private static final float INVALID_RED = 0.55F;

    private final ImString text = new ImString(TEXT_CAPACITY);
    @Nullable
    private BlockState shown;
    private boolean valid = true;
    private boolean open;
    /** True when the mouse was over the match list in the last frame. A click on a match takes the focus from the field. */
    private boolean listHovered;
    private List<Block> matches = new ArrayList<>();

    /** Draws the field. Returns the new state, or null when the state stays. */
    @Nullable
    BlockState draw(String id, String hintKey, BlockState state, BlockSearch search, ItemIconAtlas icons) {
        if (state != this.shown && !this.open) {
            this.text.set(search.text(state));
            this.shown = state;
            this.valid = true;
        }
        if (!this.valid) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, INVALID_RED, 0.1F, 0.1F, 1);
        }
        ImGui.setNextItemWidth(-1);
        boolean changed = ImGui.inputTextWithHint(id, I18n.get(hintKey), this.text);
        if (!this.valid) {
            ImGui.popStyleColor();
        }
        boolean active = ImGui.isItemActive();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.get(hintKey));
        }
        BlockState result = null;
        if (changed) {
            BlockState parsed = search.parse(this.text.get());
            this.valid = parsed != null;
            if (parsed != null && parsed != state) {
                result = parsed;
                this.shown = parsed;
            }
        }
        if (changed || ImGui.isItemActivated()) {
            this.matches = search.search(this.text.get());
            this.open = true;
        }
        if (this.open && !this.text.get().isEmpty() && !this.matches.isEmpty() && (active || this.listHovered)) {
            float row = ImGui.getFrameHeight();
            ImGui.beginChild(id + "_matches", 0, row * this.matches.size() + 2 * ImGui.getStyle().getWindowPaddingY(), ImGuiChildFlags.Borders);
            for (Block block : this.matches) {
                ItemIconAtlas.Cell cell = icons.cell(block.asItem());
                if (cell != null && icons.target() != null) {
                    ImGuiMC.image(ImGuiMC.getColorTexture(icons.target()), row, row, cell.u0(), cell.v0(), cell.u1(), cell.v1());
                } else {
                    ImGui.dummy(row, row);
                }
                ImGui.sameLine();
                if (ImGui.selectable(block.getName().getString() + "##" + search.text(block.defaultBlockState()))) {
                    BlockState picked = block.defaultBlockState();
                    this.text.set(search.text(picked));
                    this.valid = true;
                    this.shown = picked;
                    this.open = false;
                    result = picked;
                }
            }
            this.listHovered = ImGui.isWindowHovered();
            ImGui.endChild();
        } else {
            this.listHovered = false;
            if (!active) {
                this.open = false;
            }
        }
        return result;
    }

}
