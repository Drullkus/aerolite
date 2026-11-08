package us.drullk.aerolite.world.island;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.WorldDimensions;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.VoidPresetBiomes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The island edits of the generators of a world. It reads the island density function of a generator and writes the island settings.
 * A write gives the dimension direct generator settings. Only the two terrain functions of the router change.
 * The other functions of the router keep their registry references.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@Component
public final class IslandGenerators {

    public IslandGenerators() {
    }

    /** The island density function behind the final density of the generator. Empty for a generator without one. */
    public Optional<VoronoiIslands> islands(ChunkGenerator generator) {
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
            return Optional.empty();
        }
        return this.islands(noise.generatorSettings().value());
    }

    /** The island density function behind the final density of the generator settings. Empty for settings without one. */
    public Optional<VoronoiIslands> islands(NoiseGeneratorSettings settings) {
        return this.islands(settings.noiseRouter().finalDensity());
    }

    /** The island density function inside the holders and markers of the function. Empty for any other function. */
    public Optional<VoronoiIslands> islands(DensityFunction function) {
        while (true) {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                function = holder.function().value();
            } else if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                function = marker.wrapped();
            } else {
                break;
            }
        }
        return function instanceof VoronoiIslands islands ? Optional.of(islands) : Optional.empty();
    }

    /** The island settings of the first preset dimension with an island density function. Empty when no dimension has one. */
    public Optional<IslandSettings> settings(WorldDimensions dimensions) {
        for (ResourceKey<LevelStem> stem : VoidPresetBiomes.DIMENSIONS) {
            LevelStem levelStem = dimensions.dimensions().get(stem);
            if (levelStem == null) {
                continue;
            }
            Optional<VoronoiIslands> islands = this.islands(levelStem.generator());
            if (islands.isPresent()) {
                return Optional.of(IslandSettings.of(islands.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * The dimensions with the island settings in each island density function.
     * A dimension whose island density function has the settings stays as it is.
     * Any other dimension with an island density function gets direct generator settings. Its generator keeps its biome source.
     */
    public WorldDimensions withSettings(WorldDimensions current, IslandSettings settings) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>(current.dimensions());
        current.dimensions().forEach((key, stem) -> {
            if (!(stem.generator() instanceof NoiseBasedChunkGenerator generator)) {
                return;
            }
            Optional<VoronoiIslands> islands = this.islands(generator);
            if (islands.isEmpty() || IslandSettings.of(islands.get()).equals(settings)) {
                return;
            }
            NoiseGeneratorSettings edited = this.withSettings(generator.generatorSettings().value(), settings);
            stems.put(key, new LevelStem(stem.type(), new NoiseBasedChunkGenerator(generator.getBiomeSource(), Holder.direct(edited))));
        });
        return new WorldDimensions(stems);
    }

    /** The generator settings with the island settings in the island density functions of the two terrain functions. */
    public NoiseGeneratorSettings withSettings(NoiseGeneratorSettings generatorSettings, IslandSettings settings) {
        return new NoiseGeneratorSettings(
                generatorSettings.noiseSettings(),
                generatorSettings.defaultBlock(),
                generatorSettings.defaultFluid(),
                withSettings(generatorSettings.noiseRouter(), settings),
                generatorSettings.surfaceRule(),
                generatorSettings.spawnTarget(),
                generatorSettings.seaLevel(),
                generatorSettings.disableMobGeneration(),
                generatorSettings.aquifersEnabled(),
                generatorSettings.oreVeinsEnabled(),
                generatorSettings.useLegacyRandomSource()
        );
    }

    /** The router with the island settings in the island density functions of its two terrain functions. */
    private static NoiseRouter withSettings(NoiseRouter router, IslandSettings settings) {
        DensityFunction.Visitor visitor = function -> function instanceof VoronoiIslands islands ? settings.apply(islands) : function;
        return new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.initialDensityWithoutJaggedness().mapAll(visitor),
                router.finalDensity().mapAll(visitor),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap()
        );
    }

}
