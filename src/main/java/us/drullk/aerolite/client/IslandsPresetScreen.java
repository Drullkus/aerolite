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
import us.drullk.aerolite.world.island.graph.IslandGraphs;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The customize screen "Aerolite Islands" of the Islands preset. It is the Aerolite Void screen with the header Islands
 * and the island settings sliders above the dimension rows. The cell boundary distortion slider comes first.
 * The screen edits a copy of the graphs of the graph holder. The sliders edit the first {@code aerolite:voronoi_islands} function node
 * of the first router graph that reaches one. Without such a function node, they start at the island density function of the world.
 * Done writes the graphs to the holder and the island settings to the generators.
 * The Preview button opens the islands preview with a copy of the graphs. Done in the preview sends the copy back.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class IslandsPresetScreen extends VoidPresetScreen {

    /** Half the vanilla default button width. */
    private static final int FOOTER_BUTTON_WIDTH = 75;

    private final IslandGraphs.EntryLoader loader;
    private IslandGraphs graphs;
    private IslandSettings settings;

    public IslandsPresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools, VoidPresetBiomes presetBiomes) {
        super(parent, context, tools, presetBiomes, AeroliteWorldgen.ISLANDS_PRESET, Component.translatable(KEY + "islands.title"));
        this.loader = tools.graphSettings().loader(context.worldgenLoadContext());
        this.graphs = tools.graphHolder().graphs(context);
        this.settings = this.graphIslands().orElseGet(() -> tools.islands().settings(context.selectedDimensions()).orElse(IslandSettings.DEFAULT));
    }

    /** The edited island settings. */
    public IslandSettings settings() {
        return this.settings;
    }

    /** The island settings of the first router graph that reaches an island function node. */
    private Optional<IslandSettings> graphIslands() {
        return this.graphs.firstIslandsDimension(this.loader).flatMap(dimension -> this.graphs.islands(dimension, this.loader));
    }

    /** Edits the island settings and the island function node of the first router graph that reaches one. */
    private void edit(UnaryOperator<IslandSettings> edit) {
        this.settings = edit.apply(this.settings);
        this.graphs.firstIslandsDimension(this.loader).ifPresent(dimension -> this.graphs.setIslands(dimension, this.settings, this.loader));
    }

    @Override
    protected void addTopRows(SettingsList list) {
        PresetSliders sliders = this.tools().sliders();
        list.addHeader(Component.translatable(KEY + "islands"));
        list.addRow(new StepSlider(PresetSliders.DISTORTION_STEPS, sliders.distortionStep(this.settings.distortion()),
                step -> Component.translatable(KEY + "distortion", sliders.distortion(step)),
                step -> this.edit(current -> current.withDistortion(sliders.distortion(step)))));
        list.addRow(
                new StepSlider(PresetSliders.CELL_SIZE_STEPS, sliders.cellSizeStep(this.settings.cellSize()),
                        step -> Component.translatable(KEY + "cell_size", sliders.cellSize(step)),
                        step -> this.edit(current -> current.withCellSize(sliders.cellSize(step)))),
                new StepSlider(PresetSliders.CELL_HEIGHT_STEPS, sliders.cellHeightStep(this.settings.cellHeight()),
                        step -> Component.translatable(KEY + "cell_height", sliders.cellHeight(step)),
                        step -> this.edit(current -> current.withCellHeight(sliders.cellHeight(step)))));
        list.addRow(
                new StepSlider(PresetSliders.ISLAND_BOTTOM_STEPS, sliders.islandBottomStep(this.settings.layerMinY()),
                        step -> Component.translatable(KEY + "island_bottom", sliders.islandBottom(step)),
                        step -> this.edit(current -> current.withLayerMinY(sliders.islandBottom(step)))),
                new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.jitter()),
                        step -> Component.translatable(KEY + "jitter", sliders.percent(step)),
                        step -> this.edit(current -> current.withJitter(sliders.fraction(step)))));
        // The fill sliders push each other along, so each one moves the other when the edit crosses it.
        StepSlider[] fill = new StepSlider[2];
        fill[0] = new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.fillMin()),
                step -> Component.translatable(KEY + "fill_min", sliders.percent(step)),
                step -> {
                    this.edit(current -> current.withFillMin(sliders.fraction(step)));
                    fill[1].setStep(sliders.fractionStep((float) this.settings.fillMax()));
                });
        fill[1] = new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.fillMax()),
                step -> Component.translatable(KEY + "fill_max", sliders.percent(step)),
                step -> {
                    this.edit(current -> current.withFillMax(sliders.fraction(step)));
                    fill[0].setStep(sliders.fractionStep((float) this.settings.fillMin()));
                });
        list.addRow(fill[0], fill[1]);
        list.addRow(
                new StepSlider(PresetSliders.FRACTION_STEPS, sliders.fractionStep((float) this.settings.emptyChance()),
                        step -> Component.translatable(KEY + "empty_chance", sliders.percent(step)),
                        step -> this.edit(current -> current.withEmptyChance(sliders.fraction(step)))),
                new StepSlider(PresetSliders.DISTORTION_SCALE_STEPS, sliders.distortionScaleStep(this.settings.distortionScale()),
                        step -> Component.translatable(KEY + "distortion_scale", sliders.distortionScale(step)),
                        step -> this.edit(current -> current.withDistortionScale(sliders.distortionScale(step)))));
        list.addColumns(2, new StepSlider(PresetSliders.DISTORTION_OCTAVE_STEPS, sliders.distortionOctavesStep(this.settings.distortionOctaves()),
                step -> Component.translatable(KEY + "distortion_octaves", sliders.distortionOctaves(step)),
                step -> this.edit(current -> current.withDistortionOctaves(sliders.distortionOctaves(step)))));
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

    /** Opens the islands preview with a copy of the graphs. Done in the preview sends the graphs and the seed text back. */
    private void openPreview() {
        PreviewScreen.islands(this, this.context(), this.tools(), this.tools().holder().settings(), this.graphs.copy(), this.parent().getUiState().getSeed(),
                (chosen, seedText) -> {
                    this.graphs = chosen;
                    this.settings = this.graphIslands().orElse(this.settings);
                    this.parent().getUiState().setSeed(seedText);
                    this.requestRebuild();
                }).open();
    }

    /** Writes the graphs to the holder, then applies the edits. */
    @Override
    protected void done() {
        this.tools().graphHolder().set(this.graphs);
        super.done();
    }

    @Override
    protected WorldDimensions dimensions(RegistryAccess registries, WorldDimensions current, Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes) {
        return this.tools().islands().withSettings(super.dimensions(registries, current, biomes), this.settings);
    }

}
