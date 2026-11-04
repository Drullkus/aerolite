package us.drullk.aerolite.client.preview.imgui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import us.drullk.aerolite.client.PresetTools;
import us.drullk.aerolite.client.ProviderText;
import us.drullk.aerolite.client.preview.ProviderTabs;
import us.drullk.aerolite.client.provider.ProviderDraft;
import us.drullk.aerolite.client.provider.ProviderType;
import us.drullk.aerolite.client.provider.VariationBlock;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.StateProviders;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tab bar of the block fields of one variation box. The tab Blocks holds one line per field: the label, the arrow button and the field.
 * A simple state shows its text field and its dropdown. Any other provider shows its one-line summary.
 * The arrow of a field and a click on a summary open or select the tab of the field. Only the cross on a tab closes it.
 * A field tab holds the provider editor of the field.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ImGuiProviderFields {

    private static final String KEY = "createWorld.customize.aerolite.";
    /** A middle click closes no tab. The cross is the only close. A tab bar too narrow for its tabs scrolls and keeps each title whole. */
    private static final int TAB_BAR_FLAGS = ImGuiTabBarFlags.NoCloseWithMiddleMouseButton | ImGuiTabBarFlags.FittingPolicyScroll;

    private final Map<String, ImGuiBlockField> blockFields = new HashMap<>();
    private final Map<String, ImGuiProviderEditor> editors = new HashMap<>();
    private final Map<String, Summary> summaries = new HashMap<>();
    private final ImBoolean tabOpen = new ImBoolean(true);

    /** The summary text of one provider, split after the type name. */
    private record Summary(BlockStateProvider provider, String name, String rest, String typeId) {
    }

    /** Drops the widget state of every field. */
    void clear() {
        this.blockFields.clear();
        this.editors.clear();
        this.summaries.clear();
    }

    private static String text(String key, Object... args) {
        return I18n.get(KEY + key, args);
    }

    /** An ImGui format string prints a percent sign only when it is doubled. */
    private static String format(String label) {
        return label.replace("%", "%%");
    }

    private static String fieldKey(String key, VariationBlock field) {
        return key + "/" + field.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Draws the tab bar and the selected tab. Returns the variation with the edited fields, or the same variation.
     *
     * @param key the key of the variation box; it is unique in the panel and stable across rebuilds
     */
    ShaftVariation draw(ShaftVariation variation, ProviderTabs.FieldTabs tabs, String key, PresetTools tools) {
        List<VariationBlock> shown = VariationBlock.shown(variation);
        tabs.retain(shown);
        for (VariationBlock field : VariationBlock.values()) {
            if (!tabs.open().contains(field)) {
                this.editors.remove(fieldKey(key, field));
            }
        }
        ShaftVariation edited = variation;
        VariationBlock closed = null;
        boolean reported = false;
        VariationBlock selected = null;
        if (ImGui.beginTabBar("##provider_tabs", TAB_BAR_FLAGS)) {
            try {
                // The tab Blocks leads, so the scroll of the tab bar never hides it.
                int blocksFlags = ImGuiTabItemFlags.Leading | (tabs.selectRequested() && tabs.selected() == null ? ImGuiTabItemFlags.SetSelected : 0);
                if (ImGui.beginTabItem(text("blocks") + "###blocks", blocksFlags)) {
                    try {
                        reported = true;
                        edited = this.drawBlocks(edited, shown, tabs, key, tools);
                    } finally {
                        ImGui.endTabItem();
                    }
                }
                for (VariationBlock field : List.copyOf(tabs.open())) {
                    this.tabOpen.set(true);
                    int flags = tabs.selectRequested() && tabs.selected() == field ? ImGuiTabItemFlags.SetSelected : 0;
                    String id = field.name().toLowerCase(Locale.ROOT);
                    if (ImGui.beginTabItem(I18n.get(field.key()) + "###" + id, this.tabOpen, flags)) {
                        try {
                            reported = true;
                            selected = field;
                            edited = this.drawEditor(edited, field, key, tools);
                        } finally {
                            ImGui.endTabItem();
                        }
                    }
                    if (!this.tabOpen.get()) {
                        closed = field;
                    }
                }
            } finally {
                ImGui.endTabBar();
            }
        }
        if (reported) {
            tabs.shown(selected);
        }
        if (closed != null) {
            tabs.close(closed);
            this.editors.remove(fieldKey(key, closed));
        }
        return edited;
    }

    /** One line per shown field. Its look does not depend on the open tabs. */
    private ShaftVariation drawBlocks(ShaftVariation variation, List<VariationBlock> shown, ProviderTabs.FieldTabs tabs, String key, PresetTools tools) {
        float labelWidth = 0;
        for (VariationBlock field : shown) {
            labelWidth = Math.max(labelWidth, ImGui.calcTextSizeX(I18n.get(field.key())));
        }
        float fieldStart = labelWidth + ImGui.getStyle().getItemSpacingX();
        ShaftVariation edited = variation;
        for (VariationBlock field : shown) {
            String label = I18n.get(field.key());
            ImGui.pushID(field.name());
            try {
                float lineStart = ImGui.getCursorPosX();
                ImGui.alignTextToFramePadding();
                ImGui.text(label);
                ImGui.sameLine();
                ImGui.setCursorPosX(lineStart + fieldStart);
                if (ImGui.arrowButton("##open_tab", ImGuiDir.Right)) {
                    tabs.open(field);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(format(text("provider.open_tab", label)));
                }
                ImGui.sameLine();
                BlockStateProvider provider = field.get(edited);
                BlockState state = StateProviders.simpleState(provider);
                String fieldKey = fieldKey(key, field);
                if (state != null) {
                    ImGuiBlockField blockField = this.blockFields.computeIfAbsent(fieldKey, ignored -> new ImGuiBlockField());
                    BlockState picked = blockField.draw("##" + fieldKey, field.key(), state, tools.blocks(), tools.icons());
                    if (picked != null) {
                        edited = field.with(edited, BlockStateProvider.simple(picked));
                    }
                } else if (this.drawSummary(fieldKey, provider, label, tools.providerText())) {
                    tabs.open(field);
                }
            } finally {
                ImGui.popID();
            }
        }
        return edited;
    }

    /**
     * A frame to the right edge with the type name in the off text color and the summary in the text color.
     * A frame too narrow for both drops the type name. The frame clips the text.
     * The tooltip names the type id. Returns true when it was clicked.
     */
    private boolean drawSummary(String fieldKey, BlockStateProvider provider, String label, ProviderText providerText) {
        Summary summary = this.summaries.get(fieldKey);
        if (summary == null || summary.provider() != provider) {
            summary = summary(provider, providerText);
            this.summaries.put(fieldKey, summary);
        }
        float width = Math.max(1, ImGui.getContentRegionAvailX());
        boolean clicked = ImGui.invisibleButton("##summary", width, ImGui.getFrameHeight());
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX();
        float maxY = ImGui.getItemRectMaxY();
        ImGuiStyle style = ImGui.getStyle();
        float padX = style.getFramePaddingX();
        ImDrawList drawList = ImGui.getWindowDrawList();
        int frame = ImGui.getColorU32(active ? ImGuiCol.FrameBgActive : hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg);
        drawList.addRectFilled(minX, minY, maxX, maxY, frame, style.getFrameRounding());
        drawList.pushClipRect(minX + padX, minY, maxX - padX, maxY, true);
        try {
            float textY = minY + style.getFramePaddingY();
            float nameWidth = ImGui.calcTextSizeX(summary.name());
            String rest = summary.rest();
            if (nameWidth + ImGui.calcTextSizeX(rest) <= maxX - minX - 2 * padX) {
                drawList.addText(minX + padX, textY, ImGui.getColorU32(ImGuiCol.TextDisabled), summary.name());
            } else {
                nameWidth = 0;
                rest = rest.stripLeading();
            }
            drawList.addText(minX + padX + nameWidth, textY, ImGui.getColorU32(ImGuiCol.Text), rest);
        } finally {
            drawList.popClipRect();
        }
        if (hovered) {
            ImGui.setTooltip(format(summary.typeId() + "\n" + text("provider.open_tab", label)));
        }
        return clicked;
    }

    /** Splits the typed summary after the type name and its colon. A lang format without that prefix shows in one color. */
    private static Summary summary(BlockStateProvider provider, ProviderText providerText) {
        ProviderDraft draft = ProviderDraft.of(provider);
        String typed = providerText.typedSummary(draft).getString();
        String name = providerText.typeName(draft.type).getString();
        String typeId = draft.type == ProviderType.OTHER ? StateProviders.typeId(provider) : draft.type.id();
        int split = typed.startsWith(name + ":") ? name.length() + 1 : 0;
        return new Summary(provider, typed.substring(0, split), typed.substring(split), typeId);
    }

    /** The editor of the field. A provider that builds and differs from the field replaces it. */
    private ShaftVariation drawEditor(ShaftVariation variation, VariationBlock field, String key, PresetTools tools) {
        String fieldKey = fieldKey(key, field);
        BlockStateProvider current = field.get(variation);
        ImGuiProviderEditor editor = this.editors.computeIfAbsent(fieldKey, ignored -> new ImGuiProviderEditor(fieldKey + "/editor", current));
        BlockStateProvider built = editor.draw(current, tools.providerText(), tools.blocks(), tools.icons());
        return built == null ? variation : field.with(variation, built);
    }

}
