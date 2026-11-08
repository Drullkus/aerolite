package us.drullk.aerolite.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.VoidPresetBiomes;
import us.drullk.aerolite.world.island.IslandSettings;

import java.util.Map;
import java.util.Optional;

/**
 * The customize screen "Aerolite Islands" of the Islands preset. It is the Aerolite Void screen with the header Islands
 * and the island settings sliders above the dimension rows. The cell boundary distortion slider comes first.
 * The sliders start at the island settings of the island density function of the world. Done writes the settings to the generators.
 * The Preview button opens the islands preview with a copy of the settings. Done in the preview sends the copy back.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public final class IslandsPresetScreen extends VoidPresetScreen {

    /** Half the vanilla default button width. */
    private static final int FOOTER_BUTTON_WIDTH = 75;

    private IslandSettings settings;

    public IslandsPresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools, VoidPresetBiomes presetBiomes) {
        super(parent, context, tools, presetBiomes, AeroliteWorldgen.ISLANDS_PRESET, Component.translatable(KEY + "islands.title"));
        this.settings = tools.islands().settings(context.selectedDimensions()).orElse(IslandSettings.DEFAULT);
    }

    /** The edited island settings. */
    public IslandSettings settings() {
        return this.settings;
    }

    @Override
    protected void addTopRows(SettingsList list) {
        PresetSliders sliders = this.tools().sliders();
        list.addHeader(Component.translatable(KEY + "islands"));
        list.addRow(new StepSlider(PresetSliders.DISTORTION_STEPS, sliders.distortionStep(this.settings.distortion()),
                step -> Component.translatable(KEY + "distortion", sliders.distortion(step)),
                step -> this.settings = this.settings.withDistortion(sliders.distortion(step))));
        list.addRow(
                new StepSlider(PresetSliders.CELL_SIZE_STEPS, sliders.cellSizeStep(this.settings.cellSize()),
                        step -> Component.translatable(KEY + "cell_size", sliders.cellSize(step)),
                        step -> this.settings = this.settings.withCellSize(sliders.cellSize(step))),
                new StepSlider(PresetSliders.CELL_HEIGHT_STEPS, sliders.cellHeightStep(this.settings.cellHeight()),
                        step -> Component.translatable(KEY + "cell_height", sliders.cellHeight(step)),
                        step -> this.settings = this.settings.withCellHeight(sliders.cellHeight(step))));
        list.addRow(
                new StepSlider(PresetSliders.ISLAND_BOTTOM_STEPS, sliders.islandBottomStep(this.settings.layerMinY()),
                        step -> Component.translatable(KEY + "island_bottom", sliders.islandBottom(step)),
                        step -> this.settings = this.settings.withLayerMinY(sliders.islandBottom(step))),
                new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.jitter()),
                        step -> Component.translatable(KEY + "jitter", sliders.percent(step)),
                        step -> this.settings = this.settings.withJitter(sliders.fraction(step))));
        // The fill sliders push each other along, so each one moves the other when the edit crosses it.
        StepSlider[] fill = new StepSlider[2];
        fill[0] = new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.fillMin()),
                step -> Component.translatable(KEY + "fill_min", sliders.percent(step)),
                step -> {
                    this.settings = this.settings.withFillMin(sliders.fraction(step));
                    fill[1].setStep(sliders.fractionStep((float) this.settings.fillMax()));
                });
        fill[1] = new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.fillMax()),
                step -> Component.translatable(KEY + "fill_max", sliders.percent(step)),
                step -> {
                    this.settings = this.settings.withFillMax(sliders.fraction(step));
                    fill[0].setStep(sliders.fractionStep((float) this.settings.fillMin()));
                });
        list.addRow(fill[0], fill[1]);
        list.addRow(
                new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.emptyChance()),
                        step -> Component.translatable(KEY + "empty_chance", sliders.percent(step)),
                        step -> this.settings = this.settings.withEmptyChance(sliders.fraction(step))),
                new StepSlider(PresetSliders.DISTORTION_SCALE_STEPS, sliders.distortionScaleStep(this.settings.distortionScale()),
                        step -> Component.translatable(KEY + "distortion_scale", sliders.distortionScale(step)),
                        step -> this.settings = this.settings.withDistortionScale(sliders.distortionScale(step))));
        list.addColumns(2, new StepSlider(PresetSliders.DISTORTION_OCTAVE_STEPS, sliders.distortionOctavesStep(this.settings.distortionOctaves()),
                step -> Component.translatable(KEY + "distortion_octaves", sliders.distortionOctaves(step)),
                step -> this.settings = this.settings.withDistortionOctaves(sliders.distortionOctaves(step))));
    }

    /** Done, Preview and Cancel. The Preview button shows only when ImGui loaded. */
    @Override
    protected void addFooterButtons(LinearLayout footer) {
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.done()).width(FOOTER_BUTTON_WIDTH).build());
        if (this.tools().panel().available()) {
            footer.addChild(Button.builder(Component.translatable(KEY + "preview"), button -> this.openPreview()).width(FOOTER_BUTTON_WIDTH).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());
    }

    /** Opens the islands preview with a copy of the settings. Done in the preview sends the settings and the seed text back. */
    private void openPreview() {
        PreviewScreen.islands(this, this.context(), this.tools(), this.tools().holder().settings(), this.settings, this.parent().getUiState().getSeed(),
                (chosen, seedText) -> {
                    this.settings = chosen;
                    this.parent().getUiState().setSeed(seedText);
                    this.requestRebuild();
                }).open();
    }

    @Override
    protected WorldDimensions dimensions(RegistryAccess registries, WorldDimensions current, Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes) {
        return this.tools().islands().withSettings(super.dimensions(registries, current, biomes), this.settings);
    }

}
