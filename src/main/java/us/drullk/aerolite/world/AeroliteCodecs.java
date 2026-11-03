package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Codec helpers of the shaft data.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class AeroliteCodecs {

    private AeroliteCodecs() {
    }

    /** A field that reads its default when absent and is always written. */
    public static <A> MapCodec<A> defaultedField(Codec<A> codec, String name, A defaultValue) {
        return codec.optionalFieldOf(name).xmap(value -> value.orElse(defaultValue), Optional::of);
    }

    /**
     * The codec with its fields written in the sequence of the keys. Fields without a listed key follow in the sequence of the codec.
     * A record codec of more than four fields writes its fields in groups out of sequence.
     */
    public static <A> MapCodec<A> sequenced(MapCodec<A> codec, List<String> keys) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return codec.keys(ops);
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                return codec.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                DataResult<MapLike<T>> fields = codec.encoder().encodeStart(ops, input).flatMap(ops::getMap);
                Optional<MapLike<T>> values = fields.result();
                if (values.isEmpty()) {
                    return prefix.withErrorsFrom(fields);
                }
                for (String key : keys) {
                    T value = values.get().get(key);
                    if (value != null) {
                        prefix.add(key, value);
                    }
                }
                values.get().entries()
                        .filter(entry -> !ops.getStringValue(entry.getFirst()).result().map(keys::contains).orElse(false))
                        .forEach(entry -> prefix.add(entry.getFirst(), entry.getSecond()));
                return prefix;
            }

            @Override
            public String toString() {
                return codec.toString();
            }
        };
    }

    /** A float that clamps to the range on read. */
    public static Codec<Float> clampedFloat(float min, float max) {
        return Codec.FLOAT.xmap(value -> Mth.clamp(value, min, max), value -> value);
    }

    /** An int that clamps to the range on read. */
    public static Codec<Integer> clampedInt(int min, int max) {
        return Codec.INT.xmap(value -> Mth.clamp(value, min, max), value -> value);
    }

}
