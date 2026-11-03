package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.stream.Stream;

/**
 * The options of the scales pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param paths          the loop and surface path options
 * @param pathsOverTails true lets a cut surface path carve a tail block
 */
public record ScalesSettings(SurfacePathSettings paths, boolean pathsOverTails) implements PatternSettings {

    public static final ScalesSettings DEFAULT = new ScalesSettings(SurfacePathSettings.DEFAULT, false);

    public static final MapCodec<ScalesSettings> MAP_CODEC = AeroliteCodecs.sequenced(RecordCodecBuilder.mapCodec(instance -> instance.group(
            SurfacePathSettings.MAP_CODEC.forGetter(ScalesSettings::paths),
            AeroliteCodecs.defaultedField(Codec.BOOL, "paths_over_tails", DEFAULT.pathsOverTails).forGetter(ScalesSettings::pathsOverTails)
    ).apply(instance, ScalesSettings::new)), Stream.concat(SurfacePathSettings.KEYS.stream(), Stream.of("paths_over_tails")).toList());

    @Override
    public PatternType patternType() {
        return PatternType.SCALES;
    }

    public ScalesSettings withPaths(SurfacePathSettings paths) {
        return new ScalesSettings(paths, this.pathsOverTails);
    }

    public ScalesSettings withPathsOverTails(boolean pathsOverTails) {
        return new ScalesSettings(this.paths, pathsOverTails);
    }

}
