package us.drullk.aerolite.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.island.IslandBiomeSource;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.node.CellRandom;
import us.drullk.aerolite.world.island.node.IslandCarrier;
import us.drullk.aerolite.world.island.node.IslandFunction;
import us.drullk.aerolite.world.island.node.IslandValue;
import us.drullk.aerolite.world.island.node.SiteDistance;
import us.drullk.aerolite.world.island.node.SiteOffset;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;

/**
 * The registry entries and data keys of the presets.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@Component
public final class AeroliteWorldgen {

    /** The Aerolite Void preset. */
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, id("void"));
    /** The Aerolite Islands preset. */
    public static final ResourceKey<WorldPreset> ISLANDS_PRESET = ResourceKey.create(Registries.WORLD_PRESET, id("islands"));

    public static final ResourceKey<NoiseGeneratorSettings> OVERWORLD_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("overworld"));
    public static final ResourceKey<NoiseGeneratorSettings> NETHER_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("nether"));
    public static final ResourceKey<DimensionType> OVERWORLD_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, id("overworld"));
    /** The dimension special effects id of the Overworld dimension type. */
    public static final ResourceLocation OVERWORLD_EFFECTS = id("overworld");

    public static final ResourceKey<NoiseGeneratorSettings> END_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("end"));

    /** The noise settings of the Islands preset. Their final density is the island density function of the dimension. */
    public static final ResourceKey<NoiseGeneratorSettings> ISLANDS_OVERWORLD_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("islands/overworld"));
    public static final ResourceKey<NoiseGeneratorSettings> ISLANDS_NETHER_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("islands/nether"));
    public static final ResourceKey<NoiseGeneratorSettings> ISLANDS_END_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("islands/end"));

    /** The island density function of each dimension of the Islands preset. */
    public static final ResourceKey<DensityFunction> OVERWORLD_ISLANDS = ResourceKey.create(Registries.DENSITY_FUNCTION, id("islands/overworld"));
    public static final ResourceKey<DensityFunction> NETHER_ISLANDS = ResourceKey.create(Registries.DENSITY_FUNCTION, id("islands/nether"));
    public static final ResourceKey<DensityFunction> END_ISLANDS = ResourceKey.create(Registries.DENSITY_FUNCTION, id("islands/end"));

    /** The noise that seeds the island lattice. The random state wires it with the world seed. */
    public static final ResourceKey<NormalNoise.NoiseParameters> ISLANDS_NOISE = ResourceKey.create(Registries.NOISE, id("islands"));

    /** The id of the island density function type. */
    public static final ResourceLocation VORONOI_ISLANDS_TYPE = id("voronoi_islands");

    /** The noise settings of each dimension of the Islands preset. The island function entry is their terrain. */
    public static final ResourceKey<NoiseGeneratorSettings> ISLAND_VARIATIONS_OVERWORLD_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("island_variations/overworld"));
    public static final ResourceKey<NoiseGeneratorSettings> ISLAND_VARIATIONS_NETHER_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("island_variations/nether"));
    public static final ResourceKey<NoiseGeneratorSettings> ISLAND_VARIATIONS_END_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("island_variations/end"));

    /** The island function entry of each dimension of the Islands preset. */
    public static final ResourceKey<DensityFunction> OVERWORLD_ISLAND_VARIATIONS = ResourceKey.create(Registries.DENSITY_FUNCTION, id("island_variations/overworld"));
    public static final ResourceKey<DensityFunction> NETHER_ISLAND_VARIATIONS = ResourceKey.create(Registries.DENSITY_FUNCTION, id("island_variations/nether"));
    public static final ResourceKey<DensityFunction> END_ISLAND_VARIATIONS = ResourceKey.create(Registries.DENSITY_FUNCTION, id("island_variations/end"));
    /** The graph of the Basic variation: the island body node alone. */
    public static final ResourceKey<DensityFunction> BASIC_ISLAND = ResourceKey.create(Registries.DENSITY_FUNCTION, id("island_variations/basic"));

    /** The id of the island function type. */
    public static final ResourceLocation ISLAND_VARIATIONS_TYPE = id("island_variations");
    /** The id of the carrier type. */
    public static final ResourceLocation ISLAND_CARRIER_TYPE = id("island_carrier");
    public static final ResourceLocation SITE_OFFSET_TYPE = id("site_offset");
    public static final ResourceLocation SITE_DISTANCE_TYPE = id("site_distance");
    public static final ResourceLocation CELL_RANDOM_TYPE = id("cell_random");
    /** The id of the island biome source type. */
    public static final ResourceLocation ISLAND_BIOMES_TYPE = id("island_biomes");

    private final DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, Aerolite.MODID);
    private final DeferredRegister<MapCodec<? extends DensityFunction>> densityFunctionTypes = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Aerolite.MODID);
    private final DeferredRegister<MapCodec<? extends BiomeSource>> biomeSources = DeferredRegister.create(Registries.BIOME_SOURCE, Aerolite.MODID);

    public AeroliteWorldgen() {
        this.chunkGenerators.register("aerolite", () -> AeroliteChunkGenerator.CODEC);
        this.densityFunctionTypes.register(VORONOI_ISLANDS_TYPE.getPath(), () -> VoronoiIslands.CODEC.codec());
        this.densityFunctionTypes.register(ISLAND_VARIATIONS_TYPE.getPath(), () -> IslandFunction.CODEC.codec());
        this.densityFunctionTypes.register(ISLAND_CARRIER_TYPE.getPath(), () -> IslandCarrier.CODEC.codec());
        for (IslandValue.Kind kind : IslandValue.Kind.values()) {
            this.densityFunctionTypes.register(kind.path(), () -> kind.codec().codec());
        }
        this.densityFunctionTypes.register(SITE_OFFSET_TYPE.getPath(), () -> SiteOffset.CODEC.codec());
        this.densityFunctionTypes.register(SITE_DISTANCE_TYPE.getPath(), () -> SiteDistance.CODEC.codec());
        this.densityFunctionTypes.register(CELL_RANDOM_TYPE.getPath(), () -> CellRandom.CODEC.codec());
        this.biomeSources.register(ISLAND_BIOMES_TYPE.getPath(), () -> IslandBiomeSource.CODEC);
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        this.chunkGenerators.register(modEventBus);
        this.densityFunctionTypes.register(modEventBus);
        this.biomeSources.register(modEventBus);
    }

    /** The island function entry of a preset dimension, for example {@code aerolite:island_variations/overworld}. */
    public static ResourceKey<DensityFunction> islandVariationsOf(ResourceKey<Level> dimension) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, id("island_variations/" + IslandVariationRegistries.dimensionName(dimension)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, path);
    }

}
