package us.drullk.aerolite.client.provider;

import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import us.drullk.aerolite.world.StateProviders;

import java.util.List;

/**
 * The block state provider types of Minecraft 1.21.1 that the editors know, and {@link #OTHER} for any other type.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum ProviderType {
    SIMPLE("simple_state_provider"),
    WEIGHTED("weighted_state_provider"),
    NOISE_THRESHOLD("noise_threshold_provider"),
    NOISE("noise_provider"),
    DUAL_NOISE("dual_noise_provider"),
    ROTATED("rotated_block_provider"),
    RANDOMIZED_INT("randomized_int_state_provider"),
    OTHER("other");

    /** The types that an editor can build, in the sequence of the type dropdown. */
    public static final List<ProviderType> EDITABLE = List.of(SIMPLE, WEIGHTED, NOISE_THRESHOLD, NOISE, DUAL_NOISE, ROTATED, RANDOMIZED_INT);

    private final String path;

    ProviderType(String path) {
        this.path = path;
    }

    /** The path of the type id, such as {@code weighted_state_provider}. The lang keys of the type use it. */
    public String path() {
        return this.path;
    }

    /** The type id, such as {@code minecraft:weighted_state_provider}. {@link #OTHER} has no id of its own. */
    public String id() {
        return "minecraft:" + this.path;
    }

    /** The type of the id, or {@link #OTHER}. */
    public static ProviderType byId(String id) {
        for (ProviderType type : EDITABLE) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return OTHER;
    }

    /** The type of the provider, or {@link #OTHER}. */
    public static ProviderType of(BlockStateProvider provider) {
        return StateProviders.simpleState(provider) != null ? SIMPLE : byId(StateProviders.typeId(provider));
    }

}
