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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One dimension that the preview can show, from the world creation context.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 *
 * @param type the dimension type; it gives the build height, as it does for the level
 */
public record PreviewDimension(ResourceKey<Level> key, Component name, DimensionType type, BiomeSource biomeSource,
                               Holder<NoiseGeneratorSettings> noiseSettings) {

    /** The Overworld, the Nether and the End come first, then the other dimensions by id. */
    private static final List<ResourceKey<Level>> VANILLA_DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    public int minY() {
        return this.type.minY();
    }

    /** The top build limit, exclusive. */
    public int maxY() {
        return this.type.minY() + this.type.height();
    }

    /** Every level stem of the context with a noise-based generator, in the sequence of the variation sections. */
    public static List<PreviewDimension> of(WorldCreationContext context, PresetEdits edits) {
        List<PreviewDimension> dimensions = new ArrayList<>();
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : context.selectedDimensions().dimensions().entrySet()) {
            if (entry.getValue().generator() instanceof NoiseBasedChunkGenerator generator) {
                ResourceKey<Level> key = Registries.levelStemToLevel(entry.getKey());
                dimensions.add(new PreviewDimension(key, edits.dimensionName(key), entry.getValue().type().value(),
                        generator.getBiomeSource(), generator.generatorSettings()));
            }
        }
        dimensions.sort(Comparator.comparingInt((PreviewDimension dimension) -> rank(dimension.key()))
                .thenComparing(dimension -> dimension.key().location()));
        return List.copyOf(dimensions);
    }

    private static int rank(ResourceKey<Level> key) {
        int index = VANILLA_DIMENSIONS.indexOf(key);
        return index < 0 ? VANILLA_DIMENSIONS.size() : index;
    }

}
