package us.drullk.aerolite.world.shaft.maze;

import us.drullk.aerolite.world.shaft.surface.PatchSurface;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.HashSet;
import java.util.Set;

/**
 * The maze as a surface pattern. Tree edges, carved nodes and crossings become grooves. Every other block stays wall.
 * A node carves only when an edge holds it.
 * A crossing runs along the ring from its ruling to the box, then notches through the bezel onto the patch border node.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class MazePattern implements SurfacePattern {

    private final ShaftMaze maze;

    public MazePattern(ShaftMaze maze) {
        this.maze = maze;
    }

    public ShaftMaze maze() {
        return this.maze;
    }

    @Override
    public RingShape ring() {
        return this.maze.ring();
    }

    @Override
    public float bias() {
        return this.maze.bias();
    }

    @Override
    public int minChunk() {
        return this.maze.minChunk();
    }

    @Override
    public int maxChunk() {
        return this.maze.maxChunk();
    }

    @Override
    public SegmentSurface segmentAt(int chunk) {
        return this.surface(this.maze.segmentAt(chunk));
    }

    /** The surface of one maze segment. */
    public SegmentSurface surface(ShaftMaze.Segment segment) {
        RingShape ring = this.maze.ring();
        SegmentSurface surface = new SegmentSurface(ring, segment.firstChunk(), segment.lastChunk(), segment.boxes());
        Set<MazeNode> present = new HashSet<>();
        for (MazeEdge edge : segment.edges()) {
            present.add(edge.a());
            present.add(edge.b());
        }
        for (int along : segment.rulings()) {
            for (int node = 0; node < ring.nodeCount(); node++) {
                if (present.contains(new MazeNode.Ruling(along, node))) {
                    Band band = surface.band(ring.ringOf(node));
                    band.set(ring.indexOf(node), along, BandKind.GROOVE);
                    band.grooveNodes().add(band.pos(ring.indexOf(node), along));
                }
            }
        }
        for (ShaftMaze.Box box : segment.boxes()) {
            for (int i = -Ring.PATCH_INDEX; i <= Ring.PATCH_INDEX; i++) {
                for (int j = -Ring.PATCH_INDEX; j <= Ring.PATCH_INDEX; j++) {
                    if (present.contains(new MazeNode.Patch(box.center(), i, j))) {
                        surface.patch(box.center()).set(2 * i, 2 * j, BandKind.GROOVE);
                    }
                }
            }
        }
        for (MazeEdge edge : segment.edges()) {
            this.edge(surface, ring, edge);
        }
        return surface;
    }

    private void edge(SegmentSurface surface, RingShape ring, MazeEdge edge) {
        if (edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b) {
            Band band = surface.band(ring.ringOf(a.node()));
            if (a.along() == b.along()) {
                band.set(ring.blockBetween(a.node(), b.node()), a.along(), BandKind.GROOVE);
            } else {
                band.set(ring.indexOf(a.node()), Math.min(a.along(), b.along()) + 1, BandKind.GROOVE);
            }
        } else if (edge.a() instanceof MazeNode.Patch a && edge.b() instanceof MazeNode.Patch b) {
            surface.patch(a.owner()).set(a.i() + b.i(), a.j() + b.j(), BandKind.GROOVE);
        } else {
            MazeNode.Ruling ruling = (MazeNode.Ruling) (edge.a() instanceof MazeNode.Ruling ? edge.a() : edge.b());
            MazeNode other = edge.a() instanceof MazeNode.Ruling ? edge.b() : edge.a();
            if (other instanceof MazeNode.Patch patch) {
                this.crossing(surface, ring, surface.patch(patch.owner()), patch.i(), patch.j(), ruling);
            } else if (other instanceof MazeNode.Foreign foreign) {
                PatchSurface face = surface.foreignPatch(foreign.owner());
                face.set(2 * foreign.i(), 2 * foreign.j(), BandKind.GROOVE);
                this.crossing(surface, ring, face, foreign.i(), foreign.j(), ruling);
            }
        }
    }

    /** Carves a crossing from a ruling node along the ring up to the box, then through the bezel of the face onto the patch border node. */
    private void crossing(SegmentSurface surface, RingShape ring, PatchSurface face, int i, int j, MazeNode.Ruling ruling) {
        int boxCenter = face.box().center();
        int side = ruling.along() < boxCenter ? -1 : 1;
        int start = Math.min(ruling.along(), boxCenter + side * Ring.BOX) + 1;
        int end = Math.max(ruling.along(), boxCenter + side * Ring.BOX) - 1;
        Band band = surface.band(ring.ringOf(ruling.node()));
        for (int along = start; along <= end; along++) {
            band.set(ring.indexOf(ruling.node()), along, BandKind.GROOVE);
        }
        boolean shaft = face.box().patching() == ShaftMaze.Patching.SHAFT;
        for (int step = 0; step < Ring.BEZEL; step++) {
            int offset = side * (Ring.BOX - step);
            if (shaft) {
                face.set(offset, 2 * j, BandKind.GROOVE);
            } else {
                face.set(2 * i, offset, BandKind.GROOVE);
            }
        }
    }

}
