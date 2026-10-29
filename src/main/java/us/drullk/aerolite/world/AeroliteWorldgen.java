package us.drullk.aerolite.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.aerolite.Aerolite;

/**
 * The registry entries and data keys of the preset.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public final class AeroliteWorldgen {

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Aerolite.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<AeroliteChunkGenerator>> AEROLITE_GENERATOR = CHUNK_GENERATORS.register("aerolite", () -> AeroliteChunkGenerator.CODEC);

    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, id("aerolite"));

    public static final ResourceKey<NoiseGeneratorSettings> OVERWORLD_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("overworld"));
    public static final ResourceKey<NoiseGeneratorSettings> NETHER_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("nether"));
    public static final ResourceKey<NoiseGeneratorSettings> END_NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, id("end"));

    private AeroliteWorldgen() {
    }

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, path);
    }

}
