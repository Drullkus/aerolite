package us.drullk.aerolite.client.preview.imgui;

import com.mojang.serialization.DataResult;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import us.drullk.aerolite.client.BlockSearch;
import us.drullk.aerolite.client.ProviderText;
import us.drullk.aerolite.client.preview.ItemIconAtlas;
import us.drullk.aerolite.client.provider.NumberBox;
import us.drullk.aerolite.client.provider.ProviderDraft;
import us.drullk.aerolite.client.provider.ProviderType;
import us.drullk.aerolite.world.StateProviders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The editor of one field tab: the type combo, then the widgets of the chosen type. It edits a {@link ProviderDraft}.
 * The draft keeps a value that builds no provider. Then the error text of the build shows in red under the editor.
 * A provider that changes outside the editor replaces the draft.
 * The fields and the carry-over on a type change are the ones of the vanilla provider screen.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ImGuiProviderEditor {

    private static final String KEY = "createWorld.customize.aerolite.provider.";
    private static final String HINT = KEY + "state_hint";
    private static final int TEXT_CAPACITY = 128;
    private static final float INVALID_RED = 0.55F;
    private static final float[] ERROR_COLOR = {1, 0.4F, 0.4F, 1};
    /** The largest share of the line that the label column takes. */
    private static final float MAX_COLUMN = 0.5F;
    /** The width of a weight field, in characters of the panel font. */
    private static final int WEIGHT_CHARS = 4;

    private final String id;
    private final Map<String, ImString> texts = new HashMap<>();
    private final Map<String, ImGuiBlockField> fields = new HashMap<>();
    private final int[] intValue = new int[1];
    private ProviderDraft draft;
    /** The provider that the draft last read or built. */
    private BlockStateProvider source;
    @Nullable
    private String error;
    private boolean changed;
    @Nullable
    private BlockSearch blocks;
    @Nullable
    private ItemIconAtlas icons;
    /** The label column of the labeled rows of the type. */
    private float column;

    /** @param id the id of the field tab; it is unique in the panel */
    ImGuiProviderEditor(String id, BlockStateProvider provider) {
        this.id = id;
        this.source = provider;
        this.draft = ProviderDraft.of(provider);
    }

    private static String text(String key, Object... args) {
        return I18n.get(KEY + key, args);
    }

    /** An ImGui format string prints a percent sign only when it is doubled. */
    private static String format(String label) {
        return label.replace("%", "%%");
    }

    /**
     * Draws the editor of the provider of the field. Returns the provider to write to the field, or null when the field stays.
     */
    @Nullable
    BlockStateProvider draw(BlockStateProvider current, ProviderText providerText, BlockSearch blocks, ItemIconAtlas icons) {
        this.blocks = blocks;
        this.icons = icons;
        this.sync(current);
        this.changed = false;
        this.drawType(providerText);
        switch (this.draft.type) {
            case SIMPLE -> {
                this.column(KEY + "state");
                this.labeled("state");
                this.blockField("state", this.draft.state, -1, false).ifPresent(state -> this.draft.state = state);
            }
            case WEIGHTED -> this.drawWeighted();
            case NOISE -> {
                this.column(KEY + "seed", KEY + "octave", KEY + "amplitudes", KEY + "scale");
                this.drawSeed();
                this.drawNoise(this.draft.noise, "");
                this.drawStates("states", this.draft.states);
            }
            case NOISE_THRESHOLD -> this.drawNoiseThreshold();
            case DUAL_NOISE -> this.drawDualNoise();
            case ROTATED -> this.drawRotated();
            case RANDOMIZED_INT -> this.drawRandomizedInt();
            case OTHER -> ImGui.textWrapped(format(text("no_editor", this.draft.other == null ? "" : StateProviders.typeId(this.draft.other))));
        }
        BlockStateProvider result = null;
        if (this.changed) {
            DataResult<BlockStateProvider> built = this.draft.build();
            Optional<BlockStateProvider> provider = built.result();
            if (provider.isPresent()) {
                this.error = null;
                if (!StateProviders.same(provider.get(), current)) {
                    this.source = provider.get();
                    result = provider.get();
                }
            } else {
                this.error = built.error().map(DataResult.Error::message).orElse("");
            }
        }
        if (this.error != null) {
            ImGui.pushStyleColor(ImGuiCol.Text, ERROR_COLOR[0], ERROR_COLOR[1], ERROR_COLOR[2], ERROR_COLOR[3]);
            ImGui.textWrapped(format(this.error));
            ImGui.popStyleColor();
        }
        return result;
    }

    /** A provider other than the last one that the draft read or built replaces the draft. */
    private void sync(BlockStateProvider current) {
        if (current == this.source) {
            return;
        }
        if (!StateProviders.same(current, this.source)) {
            this.draft = ProviderDraft.of(current);
            this.error = null;
            this.texts.clear();
            this.fields.clear();
        }
        this.source = current;
    }

    private void drawType(ProviderText providerText) {
        List<ProviderType> types = new ArrayList<>(ProviderType.EDITABLE);
        if (this.draft.type == ProviderType.OTHER) {
            types.add(ProviderType.OTHER);
        }
        String typeName = providerText.typeName(this.draft.type).getString();
        String preview = text("type", typeName);
        // A line too narrow for the prefix shows the type name alone.
        if (ImGui.calcTextSizeX(preview) + ImGui.getFrameHeight() + 2 * ImGui.getStyle().getFramePaddingX() > ImGui.getContentRegionAvailX()) {
            preview = typeName;
        }
        ProviderType picked = this.combo("##type", preview, types, this.draft.type,
                type -> providerText.typeName(type).getString(), type -> type != ProviderType.OTHER, type -> type == ProviderType.OTHER ? null : type.id());
        if (this.draft.type != ProviderType.OTHER) {
            this.tooltip(this.draft.type.id());
        }
        if (picked != null) {
            this.draft.changeType(picked);
            this.texts.clear();
            this.changed = true;
        }
    }

    /** The entries title, one line per entry with its state, its weight and a minus button, then the add button. */
    private void drawWeighted() {
        List<ProviderDraft.Entry> entries = this.draft.entries;
        ImGui.text(text("entries"));
        float weightWidth = ImGui.calcTextSizeX("0".repeat(WEIGHT_CHARS)) + 2 * ImGui.getStyle().getFramePaddingX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        int removed = -1;
        for (int index = 0; index < entries.size(); index++) {
            ProviderDraft.Entry entry = entries.get(index);
            ImGui.pushID(index);
            try {
                this.blockField("entry/" + index, entry.state, -(weightWidth + ImGui.getFrameHeight() + 2 * spacing), false)
                        .ifPresent(state -> entry.state = state);
                ImGui.sameLine();
                ImGui.setNextItemWidth(weightWidth);
                this.intValue[0] = entry.weight;
                if (ImGui.dragInt("##weight", this.intValue, 0.1F, 1, Integer.MAX_VALUE) && this.intValue[0] != entry.weight && this.intValue[0] > 0) {
                    entry.weight = this.intValue[0];
                    this.changed = true;
                }
                this.tooltip(text("weight"));
                ImGui.sameLine();
                if (this.square("##remove", entries.size() > 1, text("remove_entry"))) {
                    removed = index;
                }
            } finally {
                ImGui.popID();
            }
        }
        if (removed >= 0) {
            entries.remove(removed);
            this.changed = true;
        }
        if (this.wideButton(text("add_entry") + "##add_entry")) {
            entries.add(new ProviderDraft.Entry(entries.isEmpty() ? this.draft.carriedStates().getFirst() : entries.getLast().state, 1));
            this.changed = true;
        }
    }

    private void drawNoiseThreshold() {
        this.column(KEY + "seed", KEY + "octave", KEY + "amplitudes", KEY + "scale", KEY + "threshold", KEY + "high_chance", KEY + "default_state");
        this.drawSeed();
        this.drawNoise(this.draft.noise, "");
        this.labeled("threshold");
        this.numberField("threshold", Float.toString(this.draft.threshold), text -> NumberBox.parseFloat(text, -1, false, 1),
                value -> this.draft.threshold = value);
        this.labeled("high_chance");
        this.numberField("high_chance", Float.toString(this.draft.highChance), text -> NumberBox.parseFloat(text, 0, false, 1),
                value -> this.draft.highChance = value);
        this.labeled("default_state");
        this.blockField("default", this.draft.defaultState, -1, false).ifPresent(state -> this.draft.defaultState = state);
        this.drawStates("low_states", this.draft.lowStates);
        this.drawStates("high_states", this.draft.highStates);
    }

    /** The variety sliders keep the minimum at or below the maximum. */
    private void drawDualNoise() {
        this.column(KEY + "seed", KEY + "octave", KEY + "amplitudes", KEY + "scale", KEY + "slow_octave", KEY + "slow_amplitudes", KEY + "slow_scale");
        this.drawSeed();
        this.drawNoise(this.draft.noise, "");
        Integer min = this.slider("##variety_min", this.draft.varietyMin, ProviderDraft.MIN_VARIETY, ProviderDraft.MAX_VARIETY,
                text("variety_min", this.draft.varietyMin));
        if (min != null) {
            this.draft.varietyMin = min;
            this.draft.varietyMax = Math.max(this.draft.varietyMax, min);
        }
        Integer max = this.slider("##variety_max", this.draft.varietyMax, ProviderDraft.MIN_VARIETY, ProviderDraft.MAX_VARIETY,
                text("variety_max", this.draft.varietyMax));
        if (max != null) {
            this.draft.varietyMax = max;
            this.draft.varietyMin = Math.min(this.draft.varietyMin, max);
        }
        this.drawNoise(this.draft.slowNoise, "slow_");
        this.drawStates("states", this.draft.states);
    }

    /** A block without the property axis shows red text. The placement turns only the axis. */
    private void drawRotated() {
        this.column(KEY + "block");
        this.labeled("block");
        BlockState shown = this.draft.rotatedBlock.defaultBlockState();
        this.blockField("block", shown, -1, !shown.hasProperty(BlockStateProperties.AXIS)).ifPresent(state -> this.draft.rotatedBlock = state.getBlock());
        this.tooltip(text("block_tooltip"));
    }

    /**
     * The source, the property combo, the values combo and the value sliders.
     * A source of another provider type and values of another int provider type show as text.
     */
    private void drawRandomizedInt() {
        this.column(KEY + "source");
        BlockState sourceState = this.draft.sourceState();
        this.labeled("source");
        if (sourceState == null) {
            ImGuiBlockField source = this.fields.computeIfAbsent("source", key -> new ImGuiBlockField());
            source.drawReadOnly(this.fieldId("source"), StateProviders.typeId(this.draft.source));
            this.tooltip(text("source_tooltip"));
            ImGui.text(format(text("property", this.draft.property)));
            ImGui.text(format(text("values", this.valuesName())));
            return;
        }
        this.draft.fitProperty();
        this.blockField("source", sourceState, -1, false).ifPresent(this.draft::setSourceState);
        IntegerProperty property = this.draft.sourceProperty();
        String propertyText = property == null ? text("property_none") : text("property", property.getName());
        IntegerProperty pickedProperty = this.combo("##property", propertyText, this.draft.sourceProperties(), property,
                candidate -> text("property_entry", candidate.getName(), ProviderDraft.lowest(candidate), ProviderDraft.highest(candidate)),
                candidate -> true, candidate -> null);
        if (pickedProperty != null) {
            this.draft.property = pickedProperty.getName();
            this.draft.fitProperty();
            this.changed = true;
        }
        if (this.draft.values == ProviderDraft.Values.OTHER) {
            ImGui.text(format(text("values", this.valuesName())));
            return;
        }
        ProviderDraft.Values mode = this.combo("##values", text("values", this.valuesName()),
                List.of(ProviderDraft.Values.CONSTANT, ProviderDraft.Values.UNIFORM), this.draft.values,
                ImGuiProviderEditor::modeName, choice -> true, choice -> null);
        if (mode != null) {
            this.draft.values = mode;
            this.changed = true;
        }
        IntegerProperty known = this.draft.sourceProperty();
        if (known == null) {
            return;
        }
        int low = ProviderDraft.lowest(known);
        int high = ProviderDraft.highest(known);
        if (this.draft.values == ProviderDraft.Values.CONSTANT) {
            Integer value = this.slider("##constant", this.draft.constant, low, high, text("value", this.draft.constant));
            if (value != null) {
                this.draft.constant = value;
            }
            return;
        }
        Integer min = this.slider("##min", this.draft.min, low, high, text("min", this.draft.min));
        if (min != null) {
            this.draft.min = min;
            this.draft.max = Math.max(this.draft.max, min);
        }
        Integer max = this.slider("##max", this.draft.max, low, high, text("max", this.draft.max));
        if (max != null) {
            this.draft.max = max;
            this.draft.min = Math.min(this.draft.min, max);
        }
    }

    private static String modeName(ProviderDraft.Values values) {
        return text("values." + values.name().toLowerCase(Locale.ROOT));
    }

    private String valuesName() {
        if (this.draft.values == ProviderDraft.Values.OTHER && this.draft.otherValues != null) {
            return String.valueOf(BuiltInRegistries.INT_PROVIDER_TYPE.getKey(this.draft.otherValues.getType()));
        }
        return modeName(this.draft.values);
    }

    private void drawSeed() {
        this.labeled("seed");
        this.numberField("seed", Long.toString(this.draft.seed), NumberBox::parseLong, value -> this.draft.seed = value);
    }

    /** The octave field, the amplitudes field and the scale field of one noise. */
    private void drawNoise(ProviderDraft.Noise noise, String prefix) {
        this.labeled(prefix + "octave");
        this.numberField(prefix + "octave", Integer.toString(noise.firstOctave), text -> NumberBox.parseInt(text, Integer.MIN_VALUE, Integer.MAX_VALUE),
                value -> noise.firstOctave = value);
        this.labeled(prefix + "amplitudes");
        this.numberField(prefix + "amplitudes", NumberBox.doublesText(noise.amplitudes), NumberBox::parseDoubles, value -> {
            noise.amplitudes.clear();
            noise.amplitudes.addAll(value);
        });
        this.labeled(prefix + "scale");
        this.numberField(prefix + "scale", Float.toString(noise.scale), text -> NumberBox.parseFloat(text, 0, true, Float.MAX_VALUE),
                value -> noise.scale = value);
    }

    /** A title line, one line per state with a minus button, then the add button. The list keeps one state. */
    private void drawStates(String title, List<BlockState> states) {
        ImGui.text(text(title));
        ImGui.pushID(title);
        try {
            int removed = -1;
            for (int index = 0; index < states.size(); index++) {
                int at = index;
                ImGui.pushID(index);
                try {
                    this.blockField(title + "/" + index, states.get(index), -(ImGui.getFrameHeight() + ImGui.getStyle().getItemSpacingX()), false)
                            .ifPresent(state -> states.set(at, state));
                    ImGui.sameLine();
                    if (this.square("##remove", states.size() > 1, text("remove_state"))) {
                        removed = index;
                    }
                } finally {
                    ImGui.popID();
                }
            }
            if (removed >= 0) {
                states.remove(removed);
                this.changed = true;
            }
            if (this.wideButton(text("add_state") + "##add_state")) {
                states.add(states.isEmpty() ? ProviderDraft.blank(ProviderType.SIMPLE).state : states.getLast());
                this.changed = true;
            }
        } finally {
            ImGui.popID();
        }
    }

    /** Sets the label column to the widest of the labels of the keys. The column takes at most half the line. */
    private void column(String... keys) {
        float widest = 0;
        for (String key : keys) {
            widest = Math.max(widest, ImGui.calcTextSizeX(I18n.get(key)));
        }
        this.column = Math.min(widest, ImGui.getContentRegionAvailX() * MAX_COLUMN) + ImGui.getStyle().getItemSpacingX();
    }

    /**
     * Draws the label of the key and moves the cursor to the label column on the same line.
     * A label wider than the column takes its own line, and the field starts the next line.
     */
    private void labeled(String key) {
        String label = text(key);
        float lineStart = ImGui.getCursorPosX();
        if (ImGui.calcTextSizeX(label) + ImGui.getStyle().getItemSpacingX() > this.column) {
            ImGui.text(label);
            return;
        }
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
        ImGui.sameLine();
        ImGui.setCursorPosX(lineStart + this.column);
    }

    private String fieldId(String name) {
        return "##" + this.id + "/" + name;
    }

    /** A block field. A changed state also marks the draft changed. */
    private Optional<BlockState> blockField(String name, BlockState state, float width, boolean warn) {
        ImGuiBlockField field = this.fields.computeIfAbsent(name, key -> new ImGuiBlockField());
        BlockState picked = field.draw(this.fieldId(name), HINT, state, this.blocks(), this.icons(), width, warn);
        if (picked != null) {
            this.changed = true;
        }
        return Optional.ofNullable(picked);
    }

    private BlockSearch blocks() {
        return java.util.Objects.requireNonNull(this.blocks, "The editor draws only with its components");
    }

    private ItemIconAtlas icons() {
        return java.util.Objects.requireNonNull(this.icons, "The editor draws only with its components");
    }

    /**
     * A text field for a number or a number list to the right edge. Bad text gives the field a red frame.
     * The value keeps the last good parse.
     */
    private <T> void numberField(String name, String initial, Function<String, T> parse, Consumer<T> onValue) {
        ImString buffer = this.texts.computeIfAbsent(name, key -> new ImString(initial, TEXT_CAPACITY));
        boolean valid = parse.apply(buffer.get()) != null;
        if (!valid) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, INVALID_RED, 0.1F, 0.1F, 1);
        }
        ImGui.setNextItemWidth(-1);
        boolean edited = ImGui.inputText("##" + name, buffer);
        if (!valid) {
            ImGui.popStyleColor();
        }
        if (edited) {
            T value = parse.apply(buffer.get());
            if (value != null) {
                onValue.accept(value);
                this.changed = true;
            }
        }
    }

    /** A full width slider from {@code min} to {@code max}. Returns the new value, or null when the value stays. */
    @Nullable
    private Integer slider(String sliderId, int value, int min, int max, String label) {
        this.intValue[0] = value;
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderInt(sliderId, this.intValue, min, max, format(label)) && this.intValue[0] != value) {
            this.changed = true;
            return this.intValue[0];
        }
        return null;
    }

    /** A square minus button with a tooltip, off when {@code enabled} is false. Returns true when it was pressed. */
    private boolean square(String buttonId, boolean enabled, String tooltip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed;
        try {
            float size = ImGui.getFrameHeight();
            pressed = ImGui.button("-" + buttonId, size, size);
        } finally {
            ImGui.endDisabled();
        }
        this.tooltip(tooltip);
        return pressed;
    }

    /** A full width button. Returns true when it was pressed. */
    private boolean wideButton(String label) {
        return ImGui.button(label, -1, 0);
    }

    /** Shows the tooltip for the last item, also while it is off. */
    private void tooltip(String tooltip) {
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(format(tooltip));
        }
    }

    /**
     * A full width combo. A choice that the enabled test rejects shows off. Returns the picked choice, or null when the choice stays.
     *
     * @param tooltips the tooltip of each entry, or null for none
     */
    @Nullable
    private <T> T combo(String comboId, String preview, List<T> choices, @Nullable T chosen, Function<T, String> names,
                        java.util.function.Predicate<T> enabled, Function<T, String> tooltips) {
        ImGui.setNextItemWidth(-1);
        T picked = null;
        if (ImGui.beginCombo(comboId, preview)) {
            try {
                for (int index = 0; index < choices.size(); index++) {
                    T choice = choices.get(index);
                    int flags = enabled.test(choice) ? 0 : ImGuiSelectableFlags.Disabled;
                    if (ImGui.selectable(names.apply(choice) + "##" + index, choice.equals(chosen), flags) && !choice.equals(chosen)) {
                        picked = choice;
                    }
                    String tooltip = tooltips.apply(choice);
                    if (tooltip != null && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                        ImGui.setTooltip(format(tooltip));
                    }
                }
            } finally {
                ImGui.endCombo();
            }
        }
        return picked;
    }

}
