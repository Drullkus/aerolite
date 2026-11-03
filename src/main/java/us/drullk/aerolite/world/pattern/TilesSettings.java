package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.stream.Stream;

/**
 * The options of the tiles pattern. Large tiles and encased tiles have no X stagger.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param size    the tile size
 * @param stagger the stagger of the tile grid
 * @param encased true keeps the corner strip solid
 * @param paths   the loop and surface path options
 */
public record TilesSettings(Size size, Stagger stagger, boolean encased, SurfacePathSettings paths) implements PatternSettings {

    public static final TilesSettings DEFAULT = new TilesSettings(Size.LARGE, Stagger.NONE, false, SurfacePathSettings.DEFAULT);

    public static final MapCodec<TilesSettings> MAP_CODEC = AeroliteCodecs.sequenced(RecordCodecBuilder.mapCodec(instance -> instance.group(
            AeroliteCodecs.defaultedField(Size.CODEC, "size", DEFAULT.size).forGetter(TilesSettings::size),
            AeroliteCodecs.defaultedField(Stagger.CODEC, "stagger", DEFAULT.stagger).forGetter(TilesSettings::stagger),
            AeroliteCodecs.defaultedField(Codec.BOOL, "encased", DEFAULT.encased).forGetter(TilesSettings::encased),
            SurfacePathSettings.MAP_CODEC.forGetter(TilesSettings::paths)
    ).apply(instance, TilesSettings::new)), Stream.concat(Stream.of("size", "stagger", "encased"), SurfacePathSettings.KEYS.stream()).toList());

    /** An X stagger with large tiles or with encased tiles becomes no stagger. */
    public TilesSettings {
        if (stagger == Stagger.X && (size == Size.LARGE || encased)) {
            stagger = Stagger.NONE;
        }
    }

    @Override
    public PatternType patternType() {
        return PatternType.TILES;
    }

    public TilesSettings withSize(Size size) {
        return new TilesSettings(size, this.stagger, this.encased, this.paths);
    }

    public TilesSettings withStagger(Stagger stagger) {
        return new TilesSettings(this.size, stagger, this.encased, this.paths);
    }

    public TilesSettings withEncased(boolean encased) {
        return new TilesSettings(this.size, this.stagger, encased, this.paths);
    }

    public TilesSettings withPaths(SurfacePathSettings paths) {
        return new TilesSettings(this.size, this.stagger, this.encased, paths);
    }

    /** The tile sizes: small 3x3 and large 7x7. */
    public enum Size implements StringRepresentable {
        SMALL("small"),
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

    /** The stagger. X gives a running bond. Y alternates the ring groove phase from tile column to tile column. */
    public enum Stagger implements StringRepresentable {
        NONE("none"),
        X("x"),
        Y("y");

        public static final Codec<Stagger> CODEC = StringRepresentable.fromEnum(Stagger::values);

        private final String id;

        Stagger(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

}
