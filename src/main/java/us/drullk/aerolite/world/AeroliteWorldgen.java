package us.drullk.aerolite.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;

/**
 * The registry entries and data keys of the preset.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class AeroliteWorldgen {

    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "void"));

    public static final ResourceKey<NoiseGeneratorSettings> OVERWORLD_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "overworld"));
    public static final ResourceKey<NoiseGeneratorSettings> NETHER_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "nether"));
    public static final ResourceKey<DimensionType> OVERWORLD_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "overworld"));
    /** The dimension special effects id of the Overworld dimension type. */
    public static final ResourceLocation OVERWORLD_EFFECTS = ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "overworld");

    public static final ResourceKey<NoiseGeneratorSettings> END_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "end"));

    private final DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, Aerolite.MODID);

    public AeroliteWorldgen() {
        this.chunkGenerators.register("aerolite", () -> AeroliteChunkGenerator.CODEC);
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        this.chunkGenerators.register(modEventBus);
    }

}
