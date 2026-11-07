package us.drullk.aerolite.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import us.drullk.aerolite.world.island.VoronoiIslands;

/**
 * The registry entries and data keys of the presets.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
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

    private final DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, Aerolite.MODID);
    private final DeferredRegister<MapCodec<? extends DensityFunction>> densityFunctionTypes = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Aerolite.MODID);

    public AeroliteWorldgen() {
        this.chunkGenerators.register("aerolite", () -> AeroliteChunkGenerator.CODEC);
        this.densityFunctionTypes.register(VORONOI_ISLANDS_TYPE.getPath(), () -> VoronoiIslands.CODEC.codec());
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        this.chunkGenerators.register(modEventBus);
        this.densityFunctionTypes.register(modEventBus);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, path);
    }

}
