package us.drullk.aerolite.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.VoidPresetBiomes;
import us.drullk.aerolite.world.island.IslandGenerators;
import us.drullk.aerolite.world.island.VoronoiIslands;

import java.util.Map;
import java.util.Optional;

/**
 * The customize screen "Aerolite Islands" of the Islands preset. It is the Aerolite Void screen with the header Islands
 * and the cell boundary distortion slider above the dimension rows.
 * The slider starts at the distortion of the island density function of the world. Done writes the distortion to the generators.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public final class IslandsPresetScreen extends VoidPresetScreen {

    private final IslandGenerators generators;
    private double distortion;

    public IslandsPresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools, VoidPresetBiomes presetBiomes, IslandGenerators generators) {
        super(parent, context, tools, presetBiomes, AeroliteWorldgen.ISLANDS_PRESET, Component.translatable(KEY + "islands.title"));
        this.generators = generators;
        this.distortion = generators.distortion(context.selectedDimensions()).orElse(VoronoiIslands.MIN_DISTORTION);
    }

    @Override
    protected void addTopRows(SettingsList list) {
        PresetSliders sliders = this.tools().sliders();
        list.addHeader(Component.translatable(KEY + "islands"));
        list.addRow(new StepSlider(PresetSliders.DISTORTION_STEPS, sliders.distortionStep(this.distortion),
                step -> Component.translatable(KEY + "distortion", sliders.distortion(step)),
                step -> this.distortion = sliders.distortion(step)));
    }

    @Override
    protected WorldDimensions dimensions(RegistryAccess registries, WorldDimensions current, Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes) {
        return this.generators.withDistortion(super.dimensions(registries, current, biomes), this.distortion);
    }

}
