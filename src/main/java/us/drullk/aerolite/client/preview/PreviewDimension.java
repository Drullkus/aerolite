package us.drullk.aerolite.client.preview;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import us.drullk.aerolite.client.PresetEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * One dimension that the preview can show, from the world creation context.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 *
 * @param type the dimension type; it gives the build height, as it does for the level
 */
public record PreviewDimension(ResourceKey<Level> key, Component name, DimensionType type, BiomeSource biomeSource,
                               Holder<NoiseGeneratorSettings> noiseSettings) {

    public int minY() {
        return this.type.minY();
    }

    /** The top build limit, exclusive. */
    public int maxY() {
        return this.type.minY() + this.type.height();
    }

    /** The dimensions of the variation sections that the context holds with a noise-based generator. */
    public static List<PreviewDimension> of(WorldCreationContext context) {
        List<PreviewDimension> dimensions = new ArrayList<>();
        for (ResourceKey<Level> key : PresetEdits.DIMENSIONS) {
            LevelStem stem = context.selectedDimensions().dimensions().get(Registries.levelToLevelStem(key));
            if (stem != null && stem.generator() instanceof NoiseBasedChunkGenerator generator) {
                dimensions.add(new PreviewDimension(key,
                        Component.translatable("createWorld.customize.aerolite.dimension." + key.location().getPath()),
                        stem.type().value(), generator.getBiomeSource(), generator.generatorSettings()));
            }
        }
        return dimensions;
    }

}
