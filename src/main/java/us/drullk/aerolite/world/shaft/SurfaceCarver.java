package us.drullk.aerolite.world.shaft;

import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.PatchSurface;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Turns the cells of a segment surface into world blocks.
 * A groove cell is a maze path: air at the outer block and the maze path block at each floor block.
 * A surface cell is the surface path block at the outer block. A wall cell and a box cell write nothing.
 * A groove cell on a flush ring index, such as the inner face of a Bridge plate, is the maze path block at the outer block.
 * A concave corner of the I-beam has two floor blocks, so its groove floor is an L.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class SurfaceCarver {

    /**
     * One maze path block.
     *
     * @param surface the maze grid block, which becomes air
     * @param floors  the blocks under it, which become the maze path block
     * @param normal  the outward face direction when the block is on a patch
     */
    record Carve(BlockPos surface, List<BlockPos> floors, @Nullable Direction normal) {
    }

    /** Receives the blocks of a segment surface. */
    interface Sink {

        void groove(Carve carve);

        /** A surface path block at the outer block. */
        void surface(BlockPos pos, @Nullable Direction normal);

        /** A flush maze path block at the outer block of a ring. */
        void flush(BlockPos pos);
    }

    /** A patch face: the frame of its horizontal shaft, the face sign and the junction center along that shaft. */
    record PatchFace(ShaftFrame frame, int sign, int center) {

        Carve carve(int along, int first) {
            return new Carve(
                    this.frame.at(along, first, Ring.BOX * this.sign),
                    List.of(this.frame.at(along, first, (Ring.BOX - 1) * this.sign)),
                    this.frame.secondDirection(this.sign)
            );
        }

        PatchFace opposite() {
            return new PatchFace(this.frame, -this.sign, this.center);
        }
    }

    private final ShaftFrames frames;

    public SurfaceCarver(@Autowired ShaftFrames frames) {
        this.frames = frames;
    }

    /** Writes one horizontal shaft segment. */
    void shaftSegment(ShaftFrame frame, RingShape ring, SegmentSurface surface, Sink sink) {
        this.segment(frame, ring, surface, box -> new PatchFace(frame, box.sign(), box.center()), sink);
    }

    /** Writes one column segment. */
    void columnSegment(ShaftLayout.Column column, RingShape ring, SegmentSurface surface, Sink sink) {
        this.segment(this.frames.of(column), ring, surface, box -> this.columnPatchFace(column, box), sink);
    }

    /** The column patch face of a junction box, in the frame of its horizontal shaft. */
    PatchFace columnPatchFace(ShaftLayout.Column column, ShaftMaze.Box box) {
        return box.patching() == ShaftMaze.Patching.COLUMN_ALONG_X
                ? new PatchFace(new ShaftFrame(ShaftFrame.Kind.ALONG_X, box.center(), column.z()), box.sign(), column.x())
                : new PatchFace(new ShaftFrame(ShaftFrame.Kind.ALONG_Z, box.center(), column.x()), box.sign(), column.z());
    }

    private void segment(ShaftFrame frame, RingShape ring, SegmentSurface surface, Function<ShaftMaze.Box, PatchFace> faceOf, Sink sink) {
        for (PatchSurface patch : surface.patches()) {
            PatchFace face = faceOf.apply(patch.box());
            this.patch(patch.foreign() ? face.opposite() : face, patch, sink);
        }
        for (int r = 0; r < ring.rings(); r++) {
            Band band = surface.band(r);
            for (BandKind kind : List.of(BandKind.GROOVE, BandKind.SURFACE)) {
                LongList cells = band.blocksOf(kind);
                for (int k = 0; k < cells.size(); k++) {
                    long cell = cells.getLong(k);
                    this.ringCell(frame, ring, r, BandPos.ring(cell), BandPos.axis(cell), kind, sink);
                }
            }
        }
    }

    private void ringCell(ShaftFrame frame, RingShape ring, int r, int index, int along, BandKind kind, Sink sink) {
        BlockPos surface = frame.at(along, ring.first(r, index), ring.second(r, index));
        if (kind == BandKind.SURFACE) {
            sink.surface(surface, null);
            return;
        }
        if (ring.flush(r, index)) {
            sink.flush(surface);
            return;
        }
        List<SectionOffset> offsets = ring.floors(r, index);
        List<BlockPos> floors = new ArrayList<>(offsets.size());
        for (SectionOffset offset : offsets) {
            floors.add(frame.at(along, offset.first(), offset.second()));
        }
        sink.groove(new Carve(surface, floors, null));
    }

    private void patch(PatchFace face, PatchSurface patch, Sink sink) {
        for (int a = -Ring.BOX; a <= Ring.BOX; a++) {
            for (int f = -Ring.BOX; f <= Ring.BOX; f++) {
                BandKind kind = patch.get(a, f);
                if (kind == BandKind.GROOVE) {
                    sink.groove(face.carve(face.center() + a, f));
                } else if (kind == BandKind.SURFACE && !PatchSurface.isBezel(a, f)) {
                    Carve carve = face.carve(face.center() + a, f);
                    sink.surface(carve.surface(), carve.normal());
                }
            }
        }
    }

}
