package us.drullk.aerolite.world;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * The codec, the comparison and the display of the block state providers of a variation.
 * {@link BlockStateProvider} has no equality of its own.
 * A simple provider compares by its state. Any other provider compares by its JSON.
 * A provider whose JSON does not encode compares by identity.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 */
public final class StateProviders {

    /** A provider object, or a bare block state of the 0.3 format. It writes the provider object. */
    public static final Codec<BlockStateProvider> CODEC = Codec.withAlternative(BlockStateProvider.CODEC, BlockState.CODEC, BlockStateProvider::simple);

    private StateProviders() {
    }

    /** The state of a simple provider, or null for any other provider type. */
    @Nullable
    public static BlockState simpleState(BlockStateProvider provider) {
        // A simple provider reads neither the random nor the position.
        return provider instanceof SimpleStateProvider simple ? simple.getState(RandomSource.create(0L), BlockPos.ZERO) : null;
    }

    /** True when the two providers give the same blocks. */
    public static boolean same(BlockStateProvider first, BlockStateProvider second) {
        if (first == second) {
            return true;
        }
        BlockState firstState = simpleState(first);
        BlockState secondState = simpleState(second);
        if (firstState != null || secondState != null) {
            return firstState == secondState;
        }
        Optional<JsonElement> firstJson = json(first);
        return firstJson.isPresent() && firstJson.equals(json(second));
    }

    /** A hash that agrees with {@link #same}. */
    public static int hash(BlockStateProvider provider) {
        BlockState state = simpleState(provider);
        if (state != null) {
            return state.hashCode();
        }
        return json(provider).map(Objects::hashCode).orElseGet(() -> System.identityHashCode(provider));
    }

    /** The id of the provider type, such as {@code minecraft:weighted_state_provider}. */
    public static String typeId(BlockStateProvider provider) {
        return json(provider)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(object -> object.get("type"))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .orElseGet(() -> provider.getClass().getSimpleName());
    }

    private static Optional<JsonElement> json(BlockStateProvider provider) {
        return BlockStateProvider.CODEC.encodeStart(JsonOps.INSTANCE, provider).result();
    }

}
