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
import java.util.OptionalDouble;

/**
 * The island edits of the generators of a world. It reads the island density function of a generator and writes its distortion.
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
        return this.islands(noise.generatorSettings().value().noiseRouter().finalDensity());
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

    /** The distortion of the first preset dimension with an island density function. Empty when no dimension has one. */
    public OptionalDouble distortion(WorldDimensions dimensions) {
        for (ResourceKey<LevelStem> stem : VoidPresetBiomes.DIMENSIONS) {
            LevelStem levelStem = dimensions.dimensions().get(stem);
            if (levelStem == null) {
                continue;
            }
            Optional<VoronoiIslands> islands = this.islands(levelStem.generator());
            if (islands.isPresent()) {
                return OptionalDouble.of(islands.get().distortion());
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * The dimensions with the distortion in each island density function.
     * A dimension whose island density function has the distortion stays as it is.
     * Any other dimension with an island density function gets direct generator settings. Its generator keeps its biome source.
     */
    public WorldDimensions withDistortion(WorldDimensions current, double distortion) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>(current.dimensions());
        current.dimensions().forEach((key, stem) -> {
            if (!(stem.generator() instanceof NoiseBasedChunkGenerator generator)) {
                return;
            }
            Optional<VoronoiIslands> islands = this.islands(generator);
            if (islands.isEmpty() || islands.get().distortion() == distortion) {
                return;
            }
            NoiseGeneratorSettings settings = generator.generatorSettings().value();
            NoiseGeneratorSettings edited = new NoiseGeneratorSettings(
                    settings.noiseSettings(),
                    settings.defaultBlock(),
                    settings.defaultFluid(),
                    withDistortion(settings.noiseRouter(), distortion),
                    settings.surfaceRule(),
                    settings.spawnTarget(),
                    settings.seaLevel(),
                    settings.disableMobGeneration(),
                    settings.aquifersEnabled(),
                    settings.oreVeinsEnabled(),
                    settings.useLegacyRandomSource()
            );
            stems.put(key, new LevelStem(stem.type(), new NoiseBasedChunkGenerator(generator.getBiomeSource(), Holder.direct(edited))));
        });
        return new WorldDimensions(stems);
    }

    /** The router with the distortion in the island density functions of its two terrain functions. */
    private static NoiseRouter withDistortion(NoiseRouter router, double distortion) {
        DensityFunction.Visitor visitor = function -> function instanceof VoronoiIslands islands ? islands.withDistortion(distortion) : function;
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
