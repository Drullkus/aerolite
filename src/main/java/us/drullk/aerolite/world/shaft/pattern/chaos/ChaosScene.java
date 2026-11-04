package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;
import us.drullk.aerolite.world.shaft.surface.path.PathFit;
import us.drullk.aerolite.world.shaft.surface.path.SurfacePath;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The chaos band of one ring over a chunk range: the stones, the grooves, the surface paths and the loop share rationing.
 *
 * <p>The scene of chunks {@code [lo, hi)} computes a window with the margin of the parameters on each side, clipped to the chunks of the pillar.
 * So each chunk of the range is a pure function of the seed, the pillar and the chunk index.
 * Only the faces with a block from {@value ChaosParams#PATH_BELOW} chunks below the range to {@value ChaosParams#PATH_ABOVE} chunks above it
 * take a surface path. The rationing samples the segments from 2 chunks below the range to 2 chunks above it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ChaosScene {

    /** Gives the box blocks of a window. A cell index is {@code ring * rows + row}. */
    @FunctionalInterface
    public interface BoxMask {

        /** No box. */
        BoxMask NONE = (ring, axis0, rows) -> new boolean[ring * rows];

        boolean[] mask(int ring, int axis0, int rows);
    }

    /**
     * One computed range.
     *
     * @param band   the band of the chunk range
     * @param result the rationing, or null at a loop share of 1
     * @param paths  the surface paths of the window
     */
    public record Result(Band band, @Nullable LoopRationing.Result result, List<SurfacePath> paths) {
    }

    private final ChaosParams params;
    private final ChaosSeeds seeds;
    private final BoxMask boxes;
    private final int minChunk;
    private final int maxChunk;

    /**
     * @param minChunk the first chunk of the pillar
     * @param maxChunk the last chunk of the pillar
     */
    public ChaosScene(ChaosParams params, ChaosSeeds seeds, BoxMask boxes, int minChunk, int maxChunk) {
        this.params = params;
        this.seeds = seeds;
        this.boxes = boxes;
        this.minChunk = minChunk;
        this.maxChunk = maxChunk;
    }

    /** The band of the chunks {@code [lo, hi)}, clipped to the chunks of the pillar. */
    public Result compute(int lo, int hi) {
        int margin = this.params.margin();
        int w0 = (int) Math.max(this.minChunk, (long) lo - margin);
        int w1 = (int) Math.min((long) this.maxChunk + 1, (long) hi + margin);
        int chunk = ChaosParams.CHUNK;
        int axis0 = chunk * w0;
        int rows = chunk * (w1 - w0);
        int ring = this.params.ringLength();
        boolean[] box = this.boxes.mask(ring, axis0, rows);
        boolean[] grooves = new ChaosStones(this.params, this.seeds, axis0, rows, box).grooves();
        Band band = new Band(ring, axis0, rows);
        for (int r = 0; r < ring; r++) {
            for (int y = 0; y < rows; y++) {
                int i = r * rows + y;
                if (box[i]) {
                    band.set(r, axis0 + y, BandKind.BOX);
                } else if (grooves[i]) {
                    band.set(r, axis0 + y, BandKind.GROOVE);
                }
            }
        }
        LongSet pins = new LongOpenHashSet();
        List<SurfacePath> paths = List.of();
        if (this.params.surface()) {
            paths = new ChaosPaths(band, this.params, this.seeds).choose(chunk * (lo - ChaosParams.PATH_BELOW), chunk * (hi + ChaosParams.PATH_ABOVE));
            for (SurfacePath path : paths) {
                PathFit.place(band, path, this.params.cut());
                if (!this.params.cut()) {
                    pins.addAll(path.ends());
                }
            }
        }
        LoopRationing.Result result = null;
        if (this.params.share() < 1) {
            result = new ChaosRationing(band, pins).ration(lo - 2, hi + 2, this.seeds, this.params.share());
        }
        int from = Math.max(chunk * lo, axis0);
        int to = Math.min(chunk * hi, axis0 + rows);
        return new Result(band.crop(from, Math.max(0, to - from)), result, paths);
    }

}
