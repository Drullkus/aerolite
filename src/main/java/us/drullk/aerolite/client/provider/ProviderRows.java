package us.drullk.aerolite.client.provider;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import us.drullk.aerolite.client.BlockField;
import us.drullk.aerolite.client.BlockSearch;
import us.drullk.aerolite.client.ChoiceDropdown;
import us.drullk.aerolite.client.ProviderText;
import us.drullk.aerolite.client.SettingsList;
import us.drullk.aerolite.client.StepSlider;
import us.drullk.aerolite.world.StateProviders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Adds the rows of a provider editor to a settings list: the type dropdown, then the widgets of the chosen type.
 * The rows edit the draft in place. A change of the row set calls the rebuild callback. The screen rebuilds the rows on its next tick.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProviderRows {

    private static final String KEY = "createWorld.customize.aerolite.provider.";
    /** The label width of the editor rows. "Slow Amplitudes" fits. */
    private static final int LABEL_WIDTH = 84;
    private static final int SQUARE = 20;
    private static final int WEIGHT_WIDTH = 40;
    private static final int BUTTON_PADDING = 8;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int INVALID_COLOR = 0xFFFF5555;

    private final SettingsList list;
    private final Font font;
    private final BlockSearch blocks;
    private final ProviderText text;
    private final Consumer<BlockField> fields;
    private final Consumer<ChoiceDropdown<?>> dropdowns;
    private final Runnable onRebuild;

    /**
     * @param fields    takes each block field, so the screen draws its dropdown
     * @param dropdowns takes each choice dropdown, so the screen draws its list
     * @param onRebuild asks the screen for a rebuild of the rows on its next tick
     */
    public ProviderRows(SettingsList list, Font font, BlockSearch blocks, ProviderText text,
                        Consumer<BlockField> fields, Consumer<ChoiceDropdown<?>> dropdowns, Runnable onRebuild) {
        this.list = list;
        this.font = font;
        this.blocks = blocks;
        this.text = text;
        this.fields = fields;
        this.dropdowns = dropdowns;
        this.onRebuild = onRebuild;
    }

    private static Component key(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    /** Adds the type dropdown and the rows of the type of the draft. */
    public void add(ProviderDraft draft) {
        List<ProviderType> types = new ArrayList<>(ProviderType.EDITABLE);
        if (draft.type == ProviderType.OTHER) {
            types.add(ProviderType.OTHER);
        }
        ChoiceDropdown<ProviderType> type = this.dropdown(types, draft.type, this.text::typeName, choice -> key("type", this.text.typeName(choice)), choice -> {
            if (choice != draft.type) {
                draft.changeType(choice);
                this.onRebuild.run();
            }
        });
        type.enabled(choice -> choice != ProviderType.OTHER);
        type.entryTooltips(choice -> choice == ProviderType.OTHER ? null : Component.literal(choice.id()));
        if (draft.type != ProviderType.OTHER) {
            type.setTooltip(Tooltip.create(Component.literal(draft.type.id())));
        }
        this.list.addLine(0, type);
        switch (draft.type) {
            case SIMPLE -> this.labeled("state", this.blockField(draft.state, state -> draft.state = state));
            case WEIGHTED -> this.weighted(draft);
            case NOISE -> {
                this.seed(draft);
                this.noise(draft.noise, "");
                this.states("states", draft.states);
            }
            case NOISE_THRESHOLD -> this.noiseThreshold(draft);
            case DUAL_NOISE -> this.dualNoise(draft);
            case ROTATED -> this.rotated(draft);
            case RANDOMIZED_INT -> this.randomizedInt(draft);
            case OTHER -> this.list.addText(key("no_editor", draft.other == null ? "" : StateProviders.typeId(draft.other)));
        }
    }

    /** The entries title, one line per entry with its state, its weight and a minus button, then the add button. */
    private void weighted(ProviderDraft draft) {
        List<ProviderDraft.Entry> entries = draft.entries;
        this.list.addText(key("entries"));
        for (int i = 0; i < entries.size(); i++) {
            ProviderDraft.Entry entry = entries.get(i);
            int index = i;
            BlockField field = this.blockField(entry.state, state -> entry.state = state);
            NumberBox<Integer> weight = new NumberBox<>(this.font, Integer.toString(entry.weight), key("weight"),
                    value -> NumberBox.parseInt(value, 1, Integer.MAX_VALUE), value -> entry.weight = value);
            weight.setWidth(WEIGHT_WIDTH);
            weight.setTooltip(Tooltip.create(key("weight")));
            Button remove = this.square(key("remove_entry"), () -> {
                entries.remove(index);
                this.onRebuild.run();
            });
            remove.active = entries.size() > 1;
            this.list.addLine(0, field, weight, remove);
        }
        this.list.addLine(SettingsList.NO_FLEX, this.fitted(key("add_entry"), () -> {
            entries.add(new ProviderDraft.Entry(entries.isEmpty() ? draft.carriedStates().getFirst() : entries.getLast().state, 1));
            this.onRebuild.run();
        }));
    }

    private void noiseThreshold(ProviderDraft draft) {
        this.seed(draft);
        this.noise(draft.noise, "");
        this.labeled("threshold", this.floatBox("threshold", draft.threshold, -1, false, 1, value -> draft.threshold = value));
        this.labeled("high_chance", this.floatBox("high_chance", draft.highChance, 0, false, 1, value -> draft.highChance = value));
        this.labeled("default_state", this.blockField(draft.defaultState, state -> draft.defaultState = state));
        this.states("low_states", draft.lowStates);
        this.states("high_states", draft.highStates);
    }

    private void dualNoise(ProviderDraft draft) {
        this.seed(draft);
        this.noise(draft.noise, "");
        StepSlider[] variety = new StepSlider[2];
        int steps = ProviderDraft.MAX_VARIETY - ProviderDraft.MIN_VARIETY;
        variety[0] = this.slider(steps, draft.varietyMin - ProviderDraft.MIN_VARIETY, step -> key("variety_min", step + ProviderDraft.MIN_VARIETY), step -> {
            draft.varietyMin = step + ProviderDraft.MIN_VARIETY;
            draft.varietyMax = Math.max(draft.varietyMax, draft.varietyMin);
            variety[1].setStep(draft.varietyMax - ProviderDraft.MIN_VARIETY);
        });
        variety[1] = this.slider(steps, draft.varietyMax - ProviderDraft.MIN_VARIETY, step -> key("variety_max", step + ProviderDraft.MIN_VARIETY), step -> {
            draft.varietyMax = step + ProviderDraft.MIN_VARIETY;
            draft.varietyMin = Math.min(draft.varietyMin, draft.varietyMax);
            variety[0].setStep(draft.varietyMin - ProviderDraft.MIN_VARIETY);
        });
        this.list.addEqual(variety);
        this.noise(draft.slowNoise, "slow_");
        this.states("states", draft.states);
    }

    /** A block without the property axis shows red text. The placement turns only the axis. */
    private void rotated(ProviderDraft draft) {
        BlockField[] field = new BlockField[1];
        field[0] = this.blockField(draft.rotatedBlock.defaultBlockState(), state -> {
            draft.rotatedBlock = state.getBlock();
            markAxis(field[0], state);
        });
        markAxis(field[0], draft.rotatedBlock.defaultBlockState());
        field[0].setTooltip(Tooltip.create(key("block_tooltip")));
        this.labeled("block", field[0]);
    }

    private static void markAxis(BlockField field, BlockState state) {
        field.setTextColor(state.hasProperty(BlockStateProperties.AXIS) ? TEXT_COLOR : INVALID_COLOR);
    }

    /**
     * The source, the property dropdown, the values dropdown and the value sliders.
     * A source of another provider type and values of another int provider type show read-only.
     */
    private void randomizedInt(ProviderDraft draft) {
        BlockState sourceState = draft.sourceState();
        if (sourceState == null) {
            BlockField source = BlockField.readOnly(this.font, this.blocks, StateProviders.typeId(draft.source), key("source"));
            source.setTooltip(Tooltip.create(key("source_tooltip")));
            this.fields.accept(source);
            this.labeled("source", source);
            this.list.addText(key("property", draft.property));
            this.list.addText(key("values", this.valuesName(draft)));
            return;
        }
        draft.fitProperty();
        this.labeled("source", this.blockField(sourceState, state -> {
            List<String> before = names(draft.sourceProperties());
            draft.setSourceState(state);
            if (!before.equals(names(draft.sourceProperties()))) {
                this.onRebuild.run();
            }
        }));
        IntegerProperty property = draft.sourceProperty();
        ChoiceDropdown<IntegerProperty> propertyDropdown = this.dropdown(draft.sourceProperties(), property,
                candidate -> key("property_entry", candidate.getName(), ProviderDraft.lowest(candidate), ProviderDraft.highest(candidate)),
                candidate -> key("property", candidate.getName()),
                candidate -> {
                    draft.property = candidate.getName();
                    draft.fitProperty();
                    this.onRebuild.run();
                });
        if (property == null) {
            propertyDropdown.setMessage(key("property", draft.property));
        }
        this.list.addLine(0, propertyDropdown);

        if (draft.values == ProviderDraft.Values.OTHER) {
            this.list.addText(key("values", this.valuesName(draft)));
            return;
        }
        ChoiceDropdown<ProviderDraft.Values> mode = this.dropdown(List.of(ProviderDraft.Values.CONSTANT, ProviderDraft.Values.UNIFORM), draft.values,
                choice -> key("values." + choice.name().toLowerCase(Locale.ROOT)),
                choice -> key("values", key("values." + choice.name().toLowerCase(Locale.ROOT))),
                choice -> {
                    if (choice != draft.values) {
                        draft.values = choice;
                        this.onRebuild.run();
                    }
                });
        this.list.addLine(0, mode);
        if (property == null) {
            return;
        }
        int low = ProviderDraft.lowest(property);
        int high = ProviderDraft.highest(property);
        int steps = Math.max(1, high - low);
        if (draft.values == ProviderDraft.Values.CONSTANT) {
            this.list.addLine(0, this.slider(steps, draft.constant - low, step -> key("value", step + low), step -> draft.constant = step + low));
            return;
        }
        StepSlider[] range = new StepSlider[2];
        range[0] = this.slider(steps, draft.min - low, step -> key("min", step + low), step -> {
            draft.min = step + low;
            draft.max = Math.max(draft.max, draft.min);
            range[1].setStep(draft.max - low);
        });
        range[1] = this.slider(steps, draft.max - low, step -> key("max", step + low), step -> {
            draft.max = step + low;
            draft.min = Math.min(draft.min, draft.max);
            range[0].setStep(draft.min - low);
        });
        this.list.addEqual(range);
    }

    private Component valuesName(ProviderDraft draft) {
        if (draft.values == ProviderDraft.Values.OTHER && draft.otherValues != null) {
            return Component.literal(String.valueOf(BuiltInRegistries.INT_PROVIDER_TYPE.getKey(draft.otherValues.getType())));
        }
        return key("values." + draft.values.name().toLowerCase(Locale.ROOT));
    }

    private static List<String> names(List<IntegerProperty> properties) {
        return properties.stream().map(IntegerProperty::getName).toList();
    }

    private void seed(ProviderDraft draft) {
        this.labeled("seed", new NumberBox<>(this.font, Long.toString(draft.seed), key("seed"), NumberBox::parseLong, value -> draft.seed = value));
    }

    /** The octave field, the amplitudes field and the scale field of one noise. */
    private void noise(ProviderDraft.Noise noise, String prefix) {
        this.labeled(prefix + "octave", new NumberBox<>(this.font, Integer.toString(noise.firstOctave), key(prefix + "octave"),
                value -> NumberBox.parseInt(value, Integer.MIN_VALUE, Integer.MAX_VALUE), value -> noise.firstOctave = value));
        this.labeled(prefix + "amplitudes", new NumberBox<>(this.font, NumberBox.doublesText(noise.amplitudes), key(prefix + "amplitudes"),
                NumberBox::parseDoubles, value -> {
                    noise.amplitudes.clear();
                    noise.amplitudes.addAll(value);
                }));
        this.labeled(prefix + "scale", this.floatBox(prefix + "scale", noise.scale, 0, true, Float.MAX_VALUE, value -> noise.scale = value));
    }

    /** A title line, one line per state with a minus button, then the add button. The list keeps one state. */
    private void states(String title, List<BlockState> states) {
        this.list.addText(key(title));
        for (int i = 0; i < states.size(); i++) {
            int index = i;
            BlockField field = this.blockField(states.get(i), state -> states.set(index, state));
            Button remove = this.square(key("remove_state"), () -> {
                states.remove(index);
                this.onRebuild.run();
            });
            remove.active = states.size() > 1;
            this.list.addLine(0, field, remove);
        }
        this.list.addLine(SettingsList.NO_FLEX, this.fitted(key("add_state"), () -> {
            states.add(states.isEmpty() ? ProviderDraft.blank(ProviderType.SIMPLE).state : states.getLast());
            this.onRebuild.run();
        }));
    }

    /** A line with a gray label of the key and the widget, which takes the rest of the row. */
    private void labeled(String key, AbstractWidget widget) {
        this.list.addLine(1, this.list.label(key(key), LABEL_WIDTH), widget);
    }

    private BlockField blockField(BlockState state, Consumer<BlockState> onChange) {
        BlockField field = new BlockField(this.font, this.blocks, state, key("state_hint"), onChange);
        this.fields.accept(field);
        return field;
    }

    private NumberBox<Float> floatBox(String key, float value, float min, boolean openMin, float max, Consumer<Float> onValue) {
        return new NumberBox<>(this.font, Float.toString(value), key(key), text -> NumberBox.parseFloat(text, min, openMin, max), onValue);
    }

    private StepSlider slider(int steps, int step, IntFunction<Component> message, IntConsumer onStep) {
        return new StepSlider(steps, step, message, onStep);
    }

    private <T> ChoiceDropdown<T> dropdown(List<T> choices, @Nullable T chosen, Function<T, Component> names,
                                           Function<T, Component> buttonNames, Consumer<T> onPick) {
        ChoiceDropdown<T> dropdown = new ChoiceDropdown<>(choices, chosen, names, key("property_none")).buttonNames(buttonNames).onPick(onPick);
        this.dropdowns.accept(dropdown);
        return dropdown;
    }

    /** A minus button of 20x20 with the tooltip. */
    private Button square(Component tooltip, Runnable onPress) {
        return Button.builder(Component.literal("-"), button -> onPress.run())
                .size(SQUARE, SQUARE)
                .tooltip(Tooltip.create(tooltip))
                .createNarration(message -> tooltip.copy())
                .build();
    }

    /** A button of 20 high that fits its text. */
    private Button fitted(Component message, Runnable onPress) {
        return Button.builder(message, button -> onPress.run())
                .size(this.font.width(message) + 2 * BUTTON_PADDING, SQUARE)
                .build();
    }

}
