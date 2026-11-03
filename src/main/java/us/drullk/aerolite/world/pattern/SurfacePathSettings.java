package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.List;

/**
 * The loop and surface path options that Bricks, Tiles, Scales and Chaos share. The fields sit flat in the pattern JSON.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param allowLoops   false gives the behavior of a loop share of 0
 * @param loopShare    the share of cycle grooves that stays, from 0 to 1
 * @param surfacePaths true places surface paths
 * @param cutting      true carves each surface path as a groove
 * @param pathChance   the chance of a surface path in one region, from 0 to 1
 */
public record SurfacePathSettings(boolean allowLoops, float loopShare, boolean surfacePaths, boolean cutting, float pathChance) {

    public static final float MIN_SHARE = 0;
    public static final float MAX_SHARE = 1;

    /** The keys of the shared fields in their JSON sequence. */
    public static final List<String> KEYS = List.of("allow_loops", "loop_share", "surface_paths", "cutting", "path_chance");

    public static final SurfacePathSettings DEFAULT = new SurfacePathSettings(true, 0.5F, false, false, 0.75F);

    public static final MapCodec<SurfacePathSettings> MAP_CODEC = AeroliteCodecs.sequenced(RecordCodecBuilder.mapCodec(instance -> instance.group(
            AeroliteCodecs.defaultedField(Codec.BOOL, "allow_loops", DEFAULT.allowLoops).forGetter(SurfacePathSettings::allowLoops),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedFloat(MIN_SHARE, MAX_SHARE), "loop_share", DEFAULT.loopShare).forGetter(SurfacePathSettings::loopShare),
            AeroliteCodecs.defaultedField(Codec.BOOL, "surface_paths", DEFAULT.surfacePaths).forGetter(SurfacePathSettings::surfacePaths),
            AeroliteCodecs.defaultedField(Codec.BOOL, "cutting", DEFAULT.cutting).forGetter(SurfacePathSettings::cutting),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedFloat(MIN_SHARE, MAX_SHARE), "path_chance", DEFAULT.pathChance).forGetter(SurfacePathSettings::pathChance)
    ).apply(instance, SurfacePathSettings::new)), KEYS);

    public SurfacePathSettings withAllowLoops(boolean allowLoops) {
        return new SurfacePathSettings(allowLoops, this.loopShare, this.surfacePaths, this.cutting, this.pathChance);
    }

    public SurfacePathSettings withLoopShare(float loopShare) {
        return new SurfacePathSettings(this.allowLoops, loopShare, this.surfacePaths, this.cutting, this.pathChance);
    }

    public SurfacePathSettings withSurfacePaths(boolean surfacePaths) {
        return new SurfacePathSettings(this.allowLoops, this.loopShare, surfacePaths, this.cutting, this.pathChance);
    }

    public SurfacePathSettings withCutting(boolean cutting) {
        return new SurfacePathSettings(this.allowLoops, this.loopShare, this.surfacePaths, cutting, this.pathChance);
    }

    public SurfacePathSettings withPathChance(float pathChance) {
        return new SurfacePathSettings(this.allowLoops, this.loopShare, this.surfacePaths, this.cutting, pathChance);
    }

}
