package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import us.drullk.aerolite.client.BlockField;
import us.drullk.aerolite.client.BlockSearch;
import us.drullk.aerolite.client.ChoiceDropdown;
import us.drullk.aerolite.client.StepSlider;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Adds the rows of a minimal provider editor to a list: the type dropdown, then real widgets for the fields of the type.
 * It edits the sample in place. A change of the row set calls the rebuild callback, and the screen rebuilds on its next tick.
 * It is enough to judge the layout. It is not a complete codec editor.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProviderEditor {

    private static final int SMALL_BUTTON_WIDTH = 60;
    private static final int WEIGHT_WIDTH = 72;
    private static final int SQUARE = 20;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int INVALID_COLOR = 0xFFFF5555;

    private final Font font;
    private final BlockSearch blocks;
    private final Overlays overlays;

    public ProviderEditor(Font font, BlockSearch blocks, Overlays overlays) {
        this.font = font;
        this.blocks = blocks;
        this.overlays = overlays;
    }

    /** Adds the editor rows of the sample with a left indent. */
    public void addRows(ProviderList list, int indent, ProviderSample sample, Runnable onRebuild) {
        new Rows(list, indent, onRebuild).add(sample);
    }

    /** The rows of one editor in one list. */
    private final class Rows {

        private final ProviderList list;
        private final int indent;
        private final Runnable onRebuild;

        Rows(ProviderList list, int indent, Runnable onRebuild) {
            this.list = list;
            this.indent = indent;
            this.onRebuild = onRebuild;
        }

        void add(ProviderSample sample) {
            ChoiceDropdown<ProviderType> type = ProviderEditor.this.overlays.add(this.list, new ChoiceDropdown<>(List.of(ProviderType.values()), sample.type,
                    value -> Component.literal("Type: " + value.displayName()), Component.literal("Type")));
            type.onPick(value -> {
                if (value != sample.type) {
                    sample.type = value;
                    this.onRebuild.run();
                }
            });
            type.setTooltip(Tooltip.create(Component.literal(sample.type.id())));
            this.list.addLine(this.indent, 0, type);
            switch (sample.type) {
                case SIMPLE -> this.labeled("State", this.blockField(sample.simpleState, "Block state", state -> sample.simpleState = state));
                case WEIGHTED -> this.weighted(sample);
                case NOISE -> {
                    this.seed(sample.noise);
                    this.noise(sample.noise, "");
                    this.states("States", sample.noiseStates);
                }
                case NOISE_THRESHOLD -> this.noiseThreshold(sample);
                case DUAL_NOISE -> this.dualNoise(sample);
                case ROTATED -> this.rotated(sample);
                case RANDOMIZED_INT -> this.randomizedInt(sample);
            }
        }

        private void weighted(ProviderSample sample) {
            List<ProviderSample.WeightedState> entries = sample.weighted;
            this.list.addText(this.indent, Component.literal("Entries"));
            for (int i = 0; i < entries.size(); i++) {
                ProviderSample.WeightedState entry = entries.get(i);
                int index = i;
                BlockField field = this.blockField(entry.state, "Block state", state -> entry.state = state);
                StepSlider weight = this.slider(ProviderSample.MAX_WEIGHT - 1, entry.weight - 1, step -> "Weight: " + (step + 1), step -> entry.weight = step + 1);
                weight.setWidth(WEIGHT_WIDTH);
                Button remove = this.square("-", "Remove this entry", () -> {
                    entries.remove(index);
                    this.onRebuild.run();
                });
                remove.active = entries.size() > 1;
                this.list.addLine(this.indent, 0, field, weight, remove);
            }
            this.list.addLine(this.indent, ProviderList.NO_FLEX, this.small("+ Entry", () -> {
                entries.add(new ProviderSample.WeightedState(entries.get(entries.size() - 1).state, 1));
                this.onRebuild.run();
            }));
        }

        private void noiseThreshold(ProviderSample sample) {
            this.seed(sample.thresholdNoise);
            this.noise(sample.thresholdNoise, "");
            this.list.addLine(this.indent, 0, this.slider(ProviderSample.THRESHOLD_STEPS, sample.thresholdStep,
                    step -> String.format(Locale.ROOT, "Threshold: %.2f", -1 + 0.05 * step), step -> sample.thresholdStep = step));
            this.list.addLine(this.indent, 0, this.slider(ProviderSample.HIGH_CHANCE_STEPS, sample.highChanceStep,
                    step -> "High Chance: " + step * 5 + "%", step -> sample.highChanceStep = step));
            this.labeled("Default", this.blockField(sample.defaultState, "Block state", state -> sample.defaultState = state));
            this.states("Low States", sample.lowStates);
            this.states("High States", sample.highStates);
        }

        private void dualNoise(ProviderSample sample) {
            this.seed(sample.dualNoise);
            this.noise(sample.dualNoise, "");
            StepSlider[] variety = new StepSlider[2];
            variety[0] = this.slider(ProviderSample.MAX_VARIETY - 1, sample.varietyMin - 1, step -> "Variety Min: " + (step + 1), step -> {
                sample.varietyMin = step + 1;
                sample.varietyMax = Math.max(sample.varietyMax, sample.varietyMin);
                variety[1].setStep(sample.varietyMax - 1);
            });
            variety[1] = this.slider(ProviderSample.MAX_VARIETY - 1, sample.varietyMax - 1, step -> "Variety Max: " + (step + 1), step -> {
                sample.varietyMax = step + 1;
                sample.varietyMin = Math.min(sample.varietyMin, sample.varietyMax);
                variety[0].setStep(sample.varietyMin - 1);
            });
            this.list.addEqual(this.indent, variety);
            this.noise(sample.slowNoise, "Slow ");
            this.states("States", sample.dualStates);
        }

        private void rotated(ProviderSample sample) {
            BlockField[] field = new BlockField[1];
            field[0] = this.blockField(sample.rotatedBlock, "Block with axis", state -> {
                sample.rotatedBlock = state;
                markAxis(field[0], state);
            });
            markAxis(field[0], sample.rotatedBlock);
            field[0].setTooltip(Tooltip.create(Component.literal("Only a block with the property axis. The placement turns the axis.")));
            this.labeled("Block", field[0]);
        }

        private void randomizedInt(ProviderSample sample) {
            BlockField source = this.blockField(sample.randomSource, "Block state", state -> {
                List<String> before = names(ProviderSample.intProperties(sample.randomSource));
                sample.randomSource = state;
                List<IntegerProperty> after = ProviderSample.intProperties(state);
                if (!before.equals(names(after))) {
                    if (after.stream().noneMatch(property -> property.getName().equals(sample.randomProperty))) {
                        sample.randomProperty = after.isEmpty() ? "" : after.get(0).getName();
                    }
                    this.onRebuild.run();
                }
            });
            source.setTooltip(Tooltip.create(Component.literal("The source is a full provider in the codec. The prototype shows a simple state.")));
            this.labeled("Source", source);

            List<IntegerProperty> properties = ProviderSample.intProperties(sample.randomSource);
            IntegerProperty property = properties.stream().filter(candidate -> candidate.getName().equals(sample.randomProperty)).findFirst()
                    .orElse(properties.isEmpty() ? null : properties.get(0));
            ChoiceDropdown<IntegerProperty> propertyDropdown = ProviderEditor.this.overlays.add(this.list, new ChoiceDropdown<>(properties, property,
                    candidate -> Component.literal("Property: " + candidate.getName() + " (" + ProviderSample.min(candidate) + "-" + ProviderSample.max(candidate) + ")"),
                    Component.literal("Property: none")));
            propertyDropdown.onPick(candidate -> {
                sample.randomProperty = candidate.getName();
                this.onRebuild.run();
            });
            this.list.addLine(this.indent, 0, propertyDropdown);

            ChoiceDropdown<Boolean> mode = ProviderEditor.this.overlays.add(this.list, new ChoiceDropdown<>(List.of(false, true), sample.randomConstant,
                    constant -> Component.literal("Values: " + (constant ? "Constant" : "Uniform")), Component.literal("Values")));
            mode.onPick(constant -> {
                if (constant != sample.randomConstant) {
                    sample.randomConstant = constant;
                    this.onRebuild.run();
                }
            });
            this.list.addLine(this.indent, 0, mode);
            if (property == null) {
                return;
            }
            int min = ProviderSample.min(property);
            int max = ProviderSample.max(property);
            sample.randomMin = Math.clamp(sample.randomMin, min, max);
            sample.randomMax = Math.clamp(sample.randomMax, min, max);
            sample.randomValue = Math.clamp(sample.randomValue, min, max);
            if (sample.randomConstant) {
                this.list.addLine(this.indent, 0, this.slider(max - min, sample.randomValue - min, step -> "Value: " + (step + min), step -> sample.randomValue = step + min));
                return;
            }
            StepSlider[] range = new StepSlider[2];
            range[0] = this.slider(max - min, sample.randomMin - min, step -> "Min: " + (step + min), step -> {
                sample.randomMin = step + min;
                sample.randomMax = Math.max(sample.randomMax, sample.randomMin);
                range[1].setStep(sample.randomMax - min);
            });
            range[1] = this.slider(max - min, sample.randomMax - min, step -> "Max: " + (step + min), step -> {
                sample.randomMax = step + min;
                sample.randomMin = Math.min(sample.randomMin, sample.randomMax);
                range[0].setStep(sample.randomMin - min);
            });
            this.list.addLine(this.indent, 0, range[0]);
            this.list.addLine(this.indent, 0, range[1]);
        }

        private void seed(NoiseSample noise) {
            this.labeled("Seed", this.numberBox(noise.seedText, "0", NoiseSample::parseSeed, text -> noise.seedText = text, value -> noise.seed = value));
        }

        /** The octave slider, the amplitudes field and the scale slider of one noise. */
        private void noise(NoiseSample noise, String prefix) {
            this.list.addLine(this.indent, 0, this.slider(-NoiseSample.MIN_OCTAVE, noise.firstOctave - NoiseSample.MIN_OCTAVE,
                    step -> prefix + "Octave: " + (step + NoiseSample.MIN_OCTAVE), step -> noise.firstOctave = step + NoiseSample.MIN_OCTAVE));
            this.labeled(prefix + "Amplitudes", this.numberBox(noise.amplitudesText, "1.0, 1.0", NoiseSample::parseAmplitudes,
                    text -> noise.amplitudesText = text, value -> noise.amplitudes = value));
            this.list.addLine(this.indent, 0, this.slider(NoiseSample.SCALES.length - 1, noise.scaleStep,
                    step -> prefix + "Scale: " + NoiseSample.SCALES[step], step -> noise.scaleStep = step));
        }

        /** A title line, one line per state with a minus button, then the add button. The list keeps one state. */
        private void states(String title, List<BlockState> states) {
            this.list.addText(this.indent, Component.literal(title));
            for (int i = 0; i < states.size(); i++) {
                int index = i;
                BlockField field = this.blockField(states.get(i), "Block state", state -> states.set(index, state));
                Button remove = this.square("-", "Remove this state", () -> {
                    states.remove(index);
                    this.onRebuild.run();
                });
                remove.active = states.size() > 1;
                this.list.addLine(this.indent, 0, field, remove);
            }
            this.list.addLine(this.indent, ProviderList.NO_FLEX, this.small("+ State", () -> {
                states.add(states.get(states.size() - 1));
                this.onRebuild.run();
            }));
        }

        private void labeled(String label, AbstractWidget widget) {
            this.list.addLine(this.indent, 1, this.list.label(Component.literal(label)), widget);
        }

        private BlockField blockField(BlockState state, String hint, Consumer<BlockState> onChange) {
            return ProviderEditor.this.overlays.add(this.list, new BlockField(ProviderEditor.this.font, ProviderEditor.this.blocks, state, Component.literal(hint), onChange));
        }

        private StepSlider slider(int steps, int step, IntFunction<String> message, IntConsumer onStep) {
            return new StepSlider(steps, step, value -> Component.literal(message.apply(value)), onStep);
        }

        private Button square(String text, String tooltip, Runnable onPress) {
            return Button.builder(Component.literal(text), button -> onPress.run())
                    .size(SQUARE, SQUARE)
                    .tooltip(Tooltip.create(Component.literal(tooltip)))
                    .build();
        }

        private Button small(String text, Runnable onPress) {
            return Button.builder(Component.literal(text), button -> onPress.run()).size(SMALL_BUTTON_WIDTH, SQUARE).build();
        }

        /** A text field for a number or a number list. Bad text turns red. The value keeps the last good parse. */
        private <T> EditBox numberBox(String text, String hint, Function<String, T> parse, Consumer<String> onText, Consumer<T> onValue) {
            EditBox box = new EditBox(ProviderEditor.this.font, SQUARE, SQUARE, Component.literal(hint)) {
                /** The visible part of the text follows the width, so the text shows from its start. */
                @Override
                public void setWidth(int width) {
                    super.setWidth(width);
                    this.moveCursorToStart(false);
                }
            };
            box.setMaxLength(128);
            box.setHint(Component.literal(hint));
            box.setValue(text);
            box.setTextColor(parse.apply(text) == null ? INVALID_COLOR : TEXT_COLOR);
            box.setResponder(value -> {
                onText.accept(value);
                T parsed = parse.apply(value);
                box.setTextColor(parsed == null ? INVALID_COLOR : TEXT_COLOR);
                if (parsed != null) {
                    onValue.accept(parsed);
                }
            });
            return box;
        }
    }

    private static List<String> names(List<IntegerProperty> properties) {
        return properties.stream().map(IntegerProperty::getName).toList();
    }

    /** A block without the property axis shows red text. */
    private static void markAxis(BlockField field, BlockState state) {
        field.setTextColor(state.hasProperty(BlockStateProperties.AXIS) ? TEXT_COLOR : INVALID_COLOR);
    }

}
