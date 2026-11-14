package us.drullk.aerolite.world.island.node;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import java.util.List;
import java.util.Map;

/**
 * Prints the time of 100000 density samples of the island function with the shipped list shape, Empty at 1 and Basic at 9.
 * It prints the time of {@link VoronoiIslands} with the values of 0.5.0 next to it. The test asserts no time limit.
 * The samples lie 4 blocks apart on X and Z, like the noise cells of a chunk. They lie 40 blocks apart on Y.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandFunctionTimingTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));
    private static final ResourceKey<IslandVariationList> LIST = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST,
            ResourceLocation.fromNamespaceAndPath("aerolite", "timing_islands"));
    private static final ResourceKey<IslandVariation> BASIC = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION,
            ResourceLocation.fromNamespaceAndPath("aerolite", "timing_basic"));
    private static final ResourceKey<IslandVariation> EMPTY = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION,
            ResourceLocation.fromNamespaceAndPath("aerolite", "timing_empty"));
    private static final int SAMPLES = 100000;

    private static IslandFunction islands() {
        CheckerboardColumnBiomeSource biomes = new CheckerboardColumnBiomeSource(HolderSet.direct(), 2);
        IslandVariation basic = IslandVariation.island(Holder.direct(IslandValue.Kind.ISLAND_BODY.function()), biomes, IslandVariation.DEFAULT_CELL_WEIGHT,
                IslandVariation.DEFAULT_FILL, IslandVariation.DEFAULT_DISTORTION, IslandVariation.DEFAULT_DISTORTION_SCALE, IslandVariation.DEFAULT_DISTORTION_OCTAVES);
        VariationLookup lookup = VariationLookup.of(Map.of(BASIC, basic, EMPTY, IslandVariation.empty(biomes, IslandVariation.DEFAULT_CELL_WEIGHT)),
                Map.of(LIST, new IslandVariationList(List.of(IslandVariationList.Entry.variation(EMPTY, 1), IslandVariationList.Entry.variation(BASIC, 9)))));
        return new IslandFunction(NOISE, 256, 128, 0, 0.8, LIST, lookup);
    }

    @Test
    void printDensityTime() {
        double sink = 0;
        // The first passes warm up the compiler and the caches.
        for (int pass = 0; pass < 3; pass++) {
            sink += pass(islands(), pass * 4096);
            sink += plainPass(new VoronoiIslands(NOISE, 256, 128, 0, 0.8, 0.3, 0.9, 0.1, 12.0, 64, 3), pass * 4096);
        }
        IslandFunction islands = islands();
        long start = System.nanoTime();
        sink += pass(islands, 20000);
        long time = System.nanoTime() - start;
        VoronoiIslands plain = new VoronoiIslands(NOISE, 256, 128, 0, 0.8, 0.3, 0.9, 0.1, 12.0, 64, 3);
        long plainStart = System.nanoTime();
        sink += plainPass(plain, 20000);
        long plainTime = System.nanoTime() - plainStart;
        System.out.printf("Island function timing: %d density samples take %.1f ms, VoronoiIslands takes %.1f ms (checksum %.3f)%n",
                SAMPLES, time / 1.0E6, plainTime / 1.0E6, sink);
    }

    private static double pass(IslandFunction islands, int originX) {
        double sum = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                for (int y = 0; y < 10; y++) {
                    sum += islands.compute(new DensityFunction.SinglePointContext(originX + x * 4, -64 + y * 40, z * 4));
                }
            }
        }
        return sum;
    }

    private static double plainPass(VoronoiIslands islands, int originX) {
        double sum = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                for (int y = 0; y < 10; y++) {
                    sum += islands.compute(originX + x * 4, -64 + y * 40, z * 4);
                }
            }
        }
        return sum;
    }

}
