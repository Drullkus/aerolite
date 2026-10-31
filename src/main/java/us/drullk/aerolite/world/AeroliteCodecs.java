package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Codec helpers of the shaft data.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public final class AeroliteCodecs {

    private AeroliteCodecs() {
    }

    /** A field that reads its default when absent and is always written. */
    public static <A> MapCodec<A> defaultedField(Codec<A> codec, String name, A defaultValue) {
        return codec.optionalFieldOf(name).xmap(value -> value.orElse(defaultValue), Optional::of);
    }

}
