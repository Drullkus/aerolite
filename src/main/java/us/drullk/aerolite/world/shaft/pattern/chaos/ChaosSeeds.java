package us.drullk.aerolite.world.shaft.pattern.chaos;

import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.shaft.MixedRandomFactory;

/**
 * The randoms and the sequence hashes of one ring of one pillar. Each value is a pure function of the pillar seed, the ring and its coordinates.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ChaosSeeds {

    private final MixedRandomFactory points;
    private final MixedRandomFactory plus;
    private final MixedRandomFactory jog;
    private final MixedRandomFactory rings;
    private final MixedRandomFactory lines;
    private final MixedRandomFactory paths;
    private final MixedRandomFactory segments;

    /**
     * @param pillarSeed the seed of the pillar, from the world seed and the pillar
     * @param ring       the ring index of the shape
     */
    public ChaosSeeds(long pillarSeed, int ring) {
        MixedRandomFactory root = new MixedRandomFactory(pillarSeed).fork(ring, 0, 0);
        this.points = root.fork("points");
        this.plus = root.fork("plus");
        this.jog = root.fork("jog");
        this.rings = root.fork("rings");
        this.lines = root.fork("lines");
        this.paths = root.fork("paths");
        this.segments = root.fork("segments");
    }

    /** The value in [0, 1) of a grid cell on one axis: 0 around the ring, 1 along the axis. */
    public double unit(int cellAround, int cellAlong, int axis) {
        return this.points.at(cellAround, cellAlong, axis).nextDouble();
    }

    /** The sequence key of the plus crossing at a block. */
    public long plusKey(int ring, int axis) {
        return this.plus.seedAt(ring, axis, 0);
    }

    /** The random that shuffles the repair variants of the plus crossing at a block. */
    public RandomSource jog(int ring, int axis) {
        return this.jog.at(ring, axis, 0);
    }

    /** The sequence key of the center ring at a stone center. */
    public long ringKey(int ring, int axis) {
        return this.rings.seedAt(ring, axis, 0);
    }

    /** The sequence key of a dual line between two stone centers. */
    public long lineKey(int ringA, int axisA, int ringB, int axisB) {
        return this.lines.fork(ringA, axisA, 0).seedAt(ringB, axisB, 0);
    }

    /** The random of the surface path of the face whose first block is at the position. */
    public RandomSource path(int ring, int axis) {
        return this.paths.at(ring, axis, 0);
    }

    /** The random of the loop rationing of one segment. */
    public RandomSource segment(int segment) {
        return this.segments.at(segment, 0, 0);
    }

}
