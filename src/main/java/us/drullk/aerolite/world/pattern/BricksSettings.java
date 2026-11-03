package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.stream.Stream;

/**
 * The options of the bricks pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param size  the brick size
 * @param paths the loop and surface path options
 */
public record BricksSettings(Size size, SurfacePathSettings paths) implements PatternSettings {

    public static final BricksSettings DEFAULT = new BricksSettings(Size.LARGE, SurfacePathSettings.DEFAULT);

    public static final MapCodec<BricksSettings> MAP_CODEC = AeroliteCodecs.sequenced(RecordCodecBuilder.mapCodec(instance -> instance.group(
            AeroliteCodecs.defaultedField(Size.CODEC, "size", DEFAULT.size).forGetter(BricksSettings::size),
            SurfacePathSettings.MAP_CODEC.forGetter(BricksSettings::paths)
    ).apply(instance, BricksSettings::new)), Stream.concat(Stream.of("size"), SurfacePathSettings.KEYS.stream()).toList());

    @Override
    public PatternType patternType() {
        return PatternType.BRICKS;
    }

    public BricksSettings withSize(Size size) {
        return new BricksSettings(size, this.paths);
    }

    public BricksSettings withPaths(SurfacePathSettings paths) {
        return new BricksSettings(this.size, paths);
    }

    /** The brick sizes: small 5x3, jumbled courses and large 11x7. */
    public enum Size implements StringRepresentable {
        SMALL("small"),
        JUMBLED("jumbled"),
        LARGE("large");

        public static final Codec<Size> CODEC = StringRepresentable.fromEnum(Size::values);

        private final String id;

        Size(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

}
