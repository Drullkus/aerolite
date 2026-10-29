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

/**
 * The customize screen of the preset in the world creation menu. It edits the settings of every Aerolite dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public class AerolitePresetScreen extends Screen {

    private static final int SLIDER_WIDTH = 210;
    private static final int MIN_SCALE_EXPONENT = Mth.log2(AeroliteSettings.MIN_DECAY_SCALE);
    private static final int MAX_SCALE_EXPONENT = Mth.log2(AeroliteSettings.MAX_DECAY_SCALE);
    private static final int THRESHOLD_STEPS = 20;

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
        contents.addChild(new DecayScaleSlider());
        contents.addChild(new DecayThresholdSlider());
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

    /** Picks a power of two from the smallest to the largest decay scale. */
    private class DecayScaleSlider extends AbstractSliderButton {

        DecayScaleSlider() {
            super(0, 0, SLIDER_WIDTH, 20, CommonComponents.EMPTY, toValue(AerolitePresetScreen.this.settings.decayScale()));
            this.updateMessage();
        }

        private static double toValue(int scale) {
            return (double) (Mth.log2(scale) - MIN_SCALE_EXPONENT) / (MAX_SCALE_EXPONENT - MIN_SCALE_EXPONENT);
        }

        private int scale() {
            return 1 << (MIN_SCALE_EXPONENT + (int) Math.round(this.value * (MAX_SCALE_EXPONENT - MIN_SCALE_EXPONENT)));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("createWorld.customize.aerolite.decay_scale", this.scale()));
        }

        @Override
        protected void applyValue() {
            this.value = toValue(this.scale());
            AerolitePresetScreen.this.settings = AerolitePresetScreen.this.settings.withDecayScale(this.scale());
        }
    }

    /** Picks the decay threshold from 0 to 1 in steps of 0.05. */
    private class DecayThresholdSlider extends AbstractSliderButton {

        DecayThresholdSlider() {
            super(0, 0, SLIDER_WIDTH, 20, CommonComponents.EMPTY, AerolitePresetScreen.this.settings.decayThreshold());
            this.updateMessage();
        }

        private float threshold() {
            return Math.round(this.value * THRESHOLD_STEPS) / (float) THRESHOLD_STEPS;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("createWorld.customize.aerolite.decay_threshold", String.format(Locale.ROOT, "%.2f", this.threshold())));
        }

        @Override
        protected void applyValue() {
            this.value = this.threshold();
            AerolitePresetScreen.this.settings = AerolitePresetScreen.this.settings.withDecayThreshold(this.threshold());
        }
    }

}
