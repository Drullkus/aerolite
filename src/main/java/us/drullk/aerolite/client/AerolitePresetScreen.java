package us.drullk.aerolite.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * The customize screen of the preset in the world creation menu. It edits the settings of every Aerolite dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public class AerolitePresetScreen extends Screen {

    private static final int SLIDER_WIDTH = 210;
    private static final int MIN_SCALE_EXPONENT = Mth.log2(AeroliteSettings.MIN_DECAY_SCALE);
    private static final int MAX_SCALE_EXPONENT = Mth.log2(AeroliteSettings.MAX_DECAY_SCALE);
    private static final int SPACING_STEP = 16;
    private static final int SPACING_STEPS = (AeroliteSettings.MAX_SHAFT_SPACING - AeroliteSettings.MIN_SHAFT_SPACING) / SPACING_STEP;
    /** Steps of 0.05 from 0 to 1. */
    private static final int FRACTION_STEPS = 20;

    private final CreateWorldScreen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private AeroliteSettings settings;

    public AerolitePresetScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("createWorld.customize.aerolite.title"));
        this.parent = parent;
        this.settings = context.selectedDimensions().dimensions().values().stream()
                .map(LevelStem::generator)
                .filter(AeroliteChunkGenerator.class::isInstance)
                .map(generator -> ((AeroliteChunkGenerator) generator).aeroliteSettings())
                .findFirst()
                .orElse(AeroliteSettings.DEFAULT);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout contents = this.layout.addToContents(LinearLayout.vertical().spacing(8));

        contents.addChild(new StepSlider(FRACTION_STEPS, Math.round(this.settings.columnChance() * FRACTION_STEPS),
                step -> Component.translatable("createWorld.customize.aerolite.column_chance", fraction(step)),
                step -> this.settings = this.settings.withColumnChance(step / (float) FRACTION_STEPS)));

        StepSlider[] spacing = new StepSlider[2];
        spacing[0] = new StepSlider(SPACING_STEPS, spacingStep(this.settings.minShaftSpacing()),
                step -> Component.translatable("createWorld.customize.aerolite.min_shaft_spacing", spacing(step)),
                step -> {
                    int max = Math.max(spacing(step), this.settings.maxShaftSpacing());
                    this.settings = this.settings.withShaftSpacing(spacing(step), max);
                    spacing[1].setStep(spacingStep(max));
                });
        spacing[1] = new StepSlider(SPACING_STEPS, spacingStep(this.settings.maxShaftSpacing()),
                step -> Component.translatable("createWorld.customize.aerolite.max_shaft_spacing", spacing(step)),
                step -> {
                    int min = Math.min(spacing(step), this.settings.minShaftSpacing());
                    this.settings = this.settings.withShaftSpacing(min, spacing(step));
                    spacing[0].setStep(spacingStep(min));
                });
        contents.addChild(spacing[0]);
        contents.addChild(spacing[1]);

        contents.addChild(new StepSlider(MAX_SCALE_EXPONENT - MIN_SCALE_EXPONENT, Mth.log2(this.settings.decayScale()) - MIN_SCALE_EXPONENT,
                step -> Component.translatable("createWorld.customize.aerolite.decay_scale", 1 << (MIN_SCALE_EXPONENT + step)),
                step -> this.settings = this.settings.withDecayScale(1 << (MIN_SCALE_EXPONENT + step))));
        contents.addChild(new StepSlider(FRACTION_STEPS, Math.round(this.settings.decayThreshold() * FRACTION_STEPS),
                step -> Component.translatable("createWorld.customize.aerolite.decay_threshold", fraction(step)),
                step -> this.settings = this.settings.withDecayThreshold(step / (float) FRACTION_STEPS)));

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            AeroliteSettings chosen = this.settings;
            this.parent.getUiState().updateDimensions((registries, dimensions) -> withSettings(dimensions, chosen));
            this.onClose();
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    private static String fraction(int step) {
        return String.format(Locale.ROOT, "%.2f", step / (float) FRACTION_STEPS);
    }

    private static int spacing(int step) {
        return AeroliteSettings.MIN_SHAFT_SPACING + SPACING_STEP * step;
    }

    private static int spacingStep(int spacing) {
        return Math.round((spacing - AeroliteSettings.MIN_SHAFT_SPACING) / (float) SPACING_STEP);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static WorldDimensions withSettings(WorldDimensions dimensions, AeroliteSettings settings) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        dimensions.dimensions().forEach((key, stem) -> stems.put(key, stem.generator() instanceof AeroliteChunkGenerator generator
                ? new LevelStem(stem.type(), generator.withAeroliteSettings(settings))
                : stem));
        return new WorldDimensions(stems);
    }

    /** A slider over a fixed count of steps. Each step maps to one setting value. */
    private static final class StepSlider extends AbstractSliderButton {

        private final int steps;
        private final IntFunction<Component> message;
        private final IntConsumer onStep;

        StepSlider(int steps, int step, IntFunction<Component> message, IntConsumer onStep) {
            super(0, 0, SLIDER_WIDTH, 20, CommonComponents.EMPTY, Mth.clamp(step / (double) steps, 0, 1));
            this.steps = steps;
            this.message = message;
            this.onStep = onStep;
            this.updateMessage();
        }

        private int step() {
            return (int) Math.round(this.value * this.steps);
        }

        /** Moves the slider without a change callback. */
        void setStep(int step) {
            this.value = Mth.clamp(step / (double) this.steps, 0, 1);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.message.apply(this.step()));
        }

        @Override
        protected void applyValue() {
            this.value = this.step() / (double) this.steps;
            this.onStep.accept(this.step());
        }
    }

}
