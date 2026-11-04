package us.drullk.aerolite.world.shaft.pattern.chaos;

import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.PatchColumn;
import us.drullk.aerolite.world.shaft.surface.PatchSurface;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The chaos pattern of one pillar or column: a jittered 2D grid of cells on the unwrapped band of each ring.
 *
 * <p>Each segment is one chunk. Its band covers the chunk and the first block of the next chunk.
 * The pattern computes blocks of {@value #BLOCK_CHUNKS} chunks plus one chunk on each side, each with the window margin, and crops the segments from them.
 * A bounded pillar of at most {@value #WHOLE_CHUNKS} chunks computes all its chunks at once.
 * The corner columns take no special role: a stone can span a corner, and a groove can run along one.
 *
 * <p>A junction box makes its rows box blocks on every ring, except the 9x9 patch area of the first ring on the face of the patch.
 * The pattern continues onto that area from the band, and the bezel cuts the grooves. The patch surface takes the area.
 * The crossings of the maze do not apply.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ChaosPattern implements SurfacePattern {

    /** The chunks of one computed block. */
    public static final int BLOCK_CHUNKS = 4;
    /** A bounded pillar of at most this many chunks computes all its chunks at once. */
    public static final int WHOLE_CHUNKS = 64;
    /** The largest face offset of the patch area. */
    private static final int PATCH = Ring.PATCH_EDGE;

    private final PatternContext context;
    private final ChaosSettings settings;
    private final ChaosScenes scenes;
    private final long pillarSeed;

    public ChaosPattern(PatternContext context, ChaosSettings settings, ChaosScenes scenes) {
        this.context = context;
        this.settings = settings;
        this.scenes = scenes;
        this.pillarSeed = context.random().fromHashOf("aerolite:chaos").nextLong();
    }

    @Override
    public RingShape ring() {
        return this.context.ring();
    }

    @Override
    public float bias() {
        return this.context.bias();
    }

    @Override
    public int minChunk() {
        return this.context.minChunk();
    }

    @Override
    public int maxChunk() {
        return this.context.maxChunk();
    }

    @Override
    public SegmentSurface segmentAt(int chunk) {
        RingShape ring = this.ring();
        int first = ShaftMaze.CHUNK * chunk;
        int end = first + ShaftMaze.CHUNK + 1;
        List<ShaftMaze.Box> boxes = this.boxes(first, end - 1);
        SegmentSurface surface = new SegmentSurface(ring, chunk, chunk, boxes);
        List<Band> bands = this.blockOf(chunk);
        for (int r = 0; r < ring.rings(); r++) {
            Band source = bands.get(r);
            Band target = surface.band(r);
            for (int along = first; along < end; along++) {
                if (!source.insideAxis(along) || target.get(0, along) == BandKind.BOX) {
                    continue;
                }
                for (int index = 0; index < source.ringLength(); index++) {
                    BandKind kind = source.get(index, along);
                    if (kind.isPath()) {
                        target.set(index, along, kind);
                    }
                }
            }
        }
        for (ShaftMaze.Box box : boxes) {
            this.patch(bands.getFirst(), box, surface.patch(box.center()));
        }
        return surface;
    }

    /** The boxes that cover a block of the axis range, sorted by center. */
    private List<ShaftMaze.Box> boxes(int min, int max) {
        List<ShaftMaze.Box> boxes = new ArrayList<>(this.context.boxes().boxes(min - Ring.BOX, max + Ring.BOX));
        boxes.sort(Comparator.comparingInt(ShaftMaze.Box::center));
        return boxes;
    }

    /** Copies the patch area of a box from the band onto its patch surface. */
    private void patch(Band band, ShaftMaze.Box box, PatchSurface patch) {
        int[] indices = this.patchIndices(box);
        if (indices.length == 0) {
            return;
        }
        boolean shaft = box.patching() == ShaftMaze.Patching.SHAFT;
        for (int across = -PATCH; across <= PATCH; across++) {
            for (int offset = -PATCH; offset <= PATCH; offset++) {
                int along = box.center() + offset;
                if (!band.insideAxis(along)) {
                    continue;
                }
                BandKind kind = band.get(indices[across + PATCH], along);
                if (kind.isPath()) {
                    if (shaft) {
                        patch.set(offset, across, kind);
                    } else {
                        patch.set(across, offset, kind);
                    }
                }
            }
        }
    }

    /** The ring indices of the first ring under the patch area of a box, by the offset across the pillar, or none when the ring does not hold them. */
    private int[] patchIndices(ShaftMaze.Box box) {
        List<PatchColumn> columns = this.ring().patchColumns(ShaftMaze.normal(box), box.sign());
        int[] out = new int[2 * PATCH + 1];
        for (int across = -PATCH; across <= PATCH; across++) {
            PatchColumn column = columns.get(across + PATCH);
            if (column.ring() != 0) {
                return new int[0];
            }
            out[across + PATCH] = column.index();
        }
        return out;
    }

    /** The box mask of a window of one ring: the box rows, without the patch area on the first ring. */
    private ChaosScene.BoxMask boxMask(int ringIndex) {
        return (ringLength, axis0, rows) -> {
            boolean[] mask = new boolean[ringLength * rows];
            for (ShaftMaze.Box box : this.boxes(axis0, axis0 + rows - 1)) {
                int[] indices = ringIndex == 0 ? this.patchIndices(box) : new int[0];
                for (int y = 0; y < rows; y++) {
                    int offset = axis0 + y - box.center();
                    if (Math.abs(offset) > Ring.BOX) {
                        continue;
                    }
                    for (int r = 0; r < ringLength; r++) {
                        mask[r * rows + y] = true;
                    }
                    if (indices.length > 0 && Math.abs(offset) <= PATCH) {
                        for (int index : indices) {
                            mask[index * rows + y] = false;
                        }
                    }
                }
            }
            return mask;
        };
    }

    /** The bands of the block that holds the chunk, one per ring. */
    private List<Band> blockOf(int chunk) {
        int lo;
        int hi;
        if ((long) this.maxChunk() - this.minChunk() < WHOLE_CHUNKS) {
            lo = this.minChunk();
            hi = this.maxChunk() + 1;
        } else {
            // The side chunks hold the patch rows and the next first row.
            lo = BLOCK_CHUNKS * Math.floorDiv(chunk, BLOCK_CHUNKS) - 1;
            hi = lo + BLOCK_CHUNKS + 2;
        }
        int margin = ChaosParams.of(this.settings, this.ring().length(0)).margin();
        int windowMin = ShaftMaze.CHUNK * (lo - margin);
        int windowMax = ShaftMaze.CHUNK * (hi + margin);
        ChaosScenes.Key key = new ChaosScenes.Key(this.pillarSeed, this.settings, this.ring(), this.minChunk(), this.maxChunk(), lo, hi,
                this.boxes(windowMin, windowMax));
        int from = lo;
        int to = hi;
        return this.scenes.bands(key, () -> this.compute(from, to));
    }

    private List<Band> compute(int lo, int hi) {
        RingShape ring = this.ring();
        List<Band> out = new ArrayList<>(ring.rings());
        for (int r = 0; r < ring.rings(); r++) {
            ChaosParams params = ChaosParams.of(this.settings, ring.length(r));
            ChaosScene scene = new ChaosScene(params, new ChaosSeeds(this.pillarSeed, r), this.boxMask(r), this.minChunk(), this.maxChunk());
            out.add(scene.compute(lo, hi).band());
        }
        return List.copyOf(out);
    }

}
