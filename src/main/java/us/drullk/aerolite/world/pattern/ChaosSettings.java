package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.stream.Stream;

/**
 * The options of the chaos pattern, a jittered grid of cells on the unwrapped band.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param cells        the cell kind
 * @param spacing      the node spacing in blocks
 * @param jitter       the offset range of each cell point, from 0 to 1
 * @param cornerRadius the chamfer at each T junction, 0 for none
 * @param paths        the loop and surface path options
 */
public record ChaosSettings(Cells cells, int spacing, float jitter, int cornerRadius, SurfacePathSettings paths) implements PatternSettings {

    public static final int MIN_SPACING = 6;
    public static final int MAX_SPACING = 12;
    public static final float MIN_JITTER = 0;
    public static final float MAX_JITTER = 1;
    public static final int MIN_CORNER_RADIUS = 0;
    public static final int MAX_CORNER_RADIUS = 3;

    public static final ChaosSettings DEFAULT = new ChaosSettings(Cells.VORONOI, MIN_SPACING, 0.5F, 1, SurfacePathSettings.DEFAULT);

    public static final MapCodec<ChaosSettings> MAP_CODEC = AeroliteCodecs.sequenced(RecordCodecBuilder.mapCodec(instance -> instance.group(
            AeroliteCodecs.defaultedField(Cells.CODEC, "cells", DEFAULT.cells).forGetter(ChaosSettings::cells),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedInt(MIN_SPACING, MAX_SPACING), "spacing", DEFAULT.spacing).forGetter(ChaosSettings::spacing),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedFloat(MIN_JITTER, MAX_JITTER), "jitter", DEFAULT.jitter).forGetter(ChaosSettings::jitter),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedInt(MIN_CORNER_RADIUS, MAX_CORNER_RADIUS), "corner_radius", DEFAULT.cornerRadius).forGetter(ChaosSettings::cornerRadius),
            SurfacePathSettings.MAP_CODEC.forGetter(ChaosSettings::paths)
    ).apply(instance, ChaosSettings::new)), Stream.concat(Stream.of("cells", "spacing", "jitter", "corner_radius"), SurfacePathSettings.KEYS.stream()).toList());

    @Override
    public PatternType patternType() {
        return PatternType.CHAOS;
    }

    public ChaosSettings withCells(Cells cells) {
        return new ChaosSettings(cells, this.spacing, this.jitter, this.cornerRadius, this.paths);
    }

    public ChaosSettings withSpacing(int spacing) {
        return new ChaosSettings(this.cells, spacing, this.jitter, this.cornerRadius, this.paths);
    }

    public ChaosSettings withJitter(float jitter) {
        return new ChaosSettings(this.cells, this.spacing, jitter, this.cornerRadius, this.paths);
    }

    public ChaosSettings withCornerRadius(int cornerRadius) {
        return new ChaosSettings(this.cells, this.spacing, this.jitter, cornerRadius, this.paths);
    }

    public ChaosSettings withPaths(SurfacePathSettings paths) {
        return new ChaosSettings(this.cells, this.spacing, this.jitter, this.cornerRadius, paths);
    }

    /** The cell kinds: Voronoi stones, or the Delaunay dual with a groove ring at each cell center. */
    public enum Cells implements StringRepresentable {
        VORONOI("voronoi"),
        DELAUNAY("delaunay");

        public static final Codec<Cells> CODEC = StringRepresentable.fromEnum(Cells::values);

        private final String id;

        Cells(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

}
