package us.drullk.aerolite.world.shaft.surface;

import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The surface of one segment of a pillar: one band per ring and one patch surface per patch face.
 * Each band covers the blocks of the segment chunks along the axis, plus the first block of the next chunk.
 * A connector edge at the segment end carves that block.
 * The rows that a junction box covers are box rows on every ring.
 * A pattern fills the surface. The paint reads it. A surface is mutable and not thread safe.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class SegmentSurface {

    private final int firstChunk;
    private final int lastChunk;
    private final List<ShaftMaze.Box> boxes;
    private final List<Band> bands = new ArrayList<>();
    private final Map<Integer, PatchSurface> patches = new LinkedHashMap<>();
    private final Map<Integer, PatchSurface> foreignPatches = new LinkedHashMap<>();

    /** An all-wall surface with the box rows set and one patch surface for each box. */
    public SegmentSurface(RingShape ring, int firstChunk, int lastChunk, List<ShaftMaze.Box> boxes) {
        this.firstChunk = firstChunk;
        this.lastChunk = lastChunk;
        this.boxes = List.copyOf(boxes);
        int axis0 = ShaftMaze.CHUNK * firstChunk;
        int length = ShaftMaze.CHUNK * (lastChunk - firstChunk + 1) + 1;
        for (int r = 0; r < ring.rings(); r++) {
            Band band = ring.band(r, axis0, length);
            for (ShaftMaze.Box box : boxes) {
                for (int along = box.center() - Ring.BOX; along <= box.center() + Ring.BOX; along++) {
                    if (band.insideAxis(along)) {
                        for (int index = 0; index < band.ringLength(); index++) {
                            band.set(index, along, BandKind.BOX);
                        }
                    }
                }
            }
            this.bands.add(band);
        }
        for (ShaftMaze.Box box : boxes) {
            this.patches.put(box.center(), new PatchSurface(box, false));
        }
    }

    public int firstChunk() {
        return this.firstChunk;
    }

    public int lastChunk() {
        return this.lastChunk;
    }

    /** The junction boxes of the segment. */
    public List<ShaftMaze.Box> boxes() {
        return this.boxes;
    }

    /** The band of one ring. */
    public Band band(int ring) {
        return this.bands.get(ring);
    }

    public List<Band> bands() {
        return List.copyOf(this.bands);
    }

    /** The patch surface of a box of the segment. */
    public PatchSurface patch(int boxCenter) {
        PatchSurface patch = this.patches.get(boxCenter);
        if (patch == null) {
            throw new IllegalArgumentException("The segment holds no box at " + boxCenter);
        }
        return patch;
    }

    /** The surface of the face opposite the patch of a box of the segment. It holds only the crossings onto the patch of the other pillar. */
    public PatchSurface foreignPatch(int boxCenter) {
        return this.foreignPatches.computeIfAbsent(boxCenter, center -> new PatchSurface(this.patch(center).box(), true));
    }

    /** The patch surfaces of the boxes, then the foreign patch surfaces. */
    public List<PatchSurface> patches() {
        List<PatchSurface> out = new ArrayList<>(this.patches.values());
        out.addAll(this.foreignPatches.values());
        return out;
    }

}
