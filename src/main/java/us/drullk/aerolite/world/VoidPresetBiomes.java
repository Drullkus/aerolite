package us.drullk.aerolite.world;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import tamaized.beanification.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The biome edits of the Aerolite Void preset and the Islands preset. A dimension of the preset has its vanilla biome source or one fixed biome.
 * A rebuilt dimension keeps its dimension type and the noise settings of its noise generator.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@Component
public final class VoidPresetBiomes {

    /** The dimensions of the preset, in the sequence of the customize screen. */
    public static final List<ResourceKey<LevelStem>> DIMENSIONS = List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END);

    public VoidPresetBiomes() {
    }

    /** The fixed biome of the dimension. It is empty while the dimension has a biome source that is not fixed. */
    public Optional<Holder<Biome>> fixedBiome(WorldDimensions dimensions, ResourceKey<LevelStem> stem) {
        LevelStem levelStem = dimensions.dimensions().get(stem);
        if (levelStem != null && levelStem.generator().getBiomeSource() instanceof FixedBiomeSource fixed) {
            return fixed.possibleBiomes().stream().findFirst();
        }
        return Optional.empty();
    }

    /** The fixed biome of each preset dimension, or empty for a dimension with its vanilla biome source. */
    public Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> fixedBiomes(WorldDimensions dimensions) {
        Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes = new LinkedHashMap<>();
        for (ResourceKey<LevelStem> stem : DIMENSIONS) {
            biomes.put(stem, this.fixedBiome(dimensions, stem));
        }
        return biomes;
    }

    /** The dimensions of the preset in the registries. */
    public WorldDimensions presetDimensions(RegistryAccess registries, ResourceKey<WorldPreset> preset) {
        return registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions();
    }

    /**
     * The dimensions with each listed dimension rebuilt. A biome gives a fixed biome source. An empty value gives the biome source of the preset.
     * A dimension keeps the noise settings of its noise generator. A dimension without a noise generator takes the generator settings of the preset.
     */
    public WorldDimensions withBiomes(RegistryAccess registries, ResourceKey<WorldPreset> presetKey, WorldDimensions current, Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes) {
        WorldDimensions preset = this.presetDimensions(registries, presetKey);
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>(current.dimensions());
        biomes.forEach((stem, biome) -> {
            LevelStem presetStem = preset.dimensions().get(stem);
            if (presetStem == null || !(presetStem.generator() instanceof NoiseBasedChunkGenerator presetGenerator)) {
                return;
            }
            LevelStem currentStem = stems.getOrDefault(stem, presetStem);
            Holder<NoiseGeneratorSettings> settings = currentStem.generator() instanceof NoiseBasedChunkGenerator noise
                    ? noise.generatorSettings()
                    : presetGenerator.generatorSettings();
            BiomeSource source = biome.<BiomeSource>map(FixedBiomeSource::new).orElse(presetGenerator.getBiomeSource());
            ChunkGenerator generator = new NoiseBasedChunkGenerator(source, settings);
            stems.put(stem, new LevelStem(currentStem.type(), generator));
        });
        return new WorldDimensions(stems);
    }

}
