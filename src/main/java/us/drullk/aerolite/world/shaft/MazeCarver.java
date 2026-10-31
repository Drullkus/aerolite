package us.drullk.aerolite.world.shaft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.shaft.maze.MazeEdge;
import us.drullk.aerolite.world.shaft.maze.MazeNode;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns maze nodes and tree edges into maze path blocks.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
@Component
public final class MazeCarver {

    /**
     * One maze path block.
     *
     * @param surface the maze grid block, which becomes air
     * @param beneath the block under it, which becomes the maze path block
     * @param normal  the outward face direction when the block is on a patch
     */
    record Carve(BlockPos surface, BlockPos beneath, @Nullable Direction normal) {
    }

    /** A patch face: the frame of its horizontal shaft, the face sign and the junction center along that shaft. */
    record PatchFace(ShaftFrame frame, int sign, int center) {

        Carve carve(int along, int first) {
            return new Carve(
                    this.frame.at(along, first, Ring.BOX * this.sign),
                    this.frame.at(along, first, (Ring.BOX - 1) * this.sign),
                    this.frame.secondDirection(this.sign)
            );
        }

        Carve node(int i, int j) {
            return this.carve(this.center + 2 * i, 2 * j);
        }
    }

    private final Ring ring;
    private final ShaftFrames frames;

    public MazeCarver(@Autowired Ring ring, @Autowired ShaftFrames frames) {
        this.ring = ring;
        this.frames = frames;
    }

    Carve block(ShaftFrame frame, int along, int block) {
        int first = this.ring.first(block);
        int second = this.ring.second(block);
        return new Carve(frame.at(along, first, second), frame.at(along, this.clampInterior(first), this.clampInterior(second)), null);
    }

    private int clampInterior(int offset) {
        return Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, offset));
    }

    /** Carves the ruling nodes of the rulings. A corner node carves only when an edge holds it. */
    void rulings(ShaftFrame frame, int[] rulings, List<MazeEdge> edges, Consumer<Carve> out) {
        Set<MazeNode> present = new HashSet<>();
        for (MazeEdge edge : edges) {
            present.add(edge.a());
            present.add(edge.b());
        }
        for (int along : rulings) {
            for (int v = 0; v < Ring.NODES; v++) {
                if (!this.ring.isCorner(v) || present.contains(new MazeNode.Ruling(along, v))) {
                    out.accept(this.block(frame, along, 2 * v));
                }
            }
        }
    }

    void rulingEdge(ShaftFrame frame, MazeNode.Ruling a, MazeNode.Ruling b, Consumer<Carve> out) {
        if (a.along() == b.along()) {
            out.accept(this.block(frame, a.along(), this.ring.blockBetween(a.node(), b.node())));
        } else {
            out.accept(this.block(frame, Math.min(a.along(), b.along()) + 1, 2 * a.node()));
        }
    }

    /** Carves the edges and patch nodes of one horizontal shaft segment. */
    void shaftSegment(ShaftFrame frame, ShaftMaze.Segment segment, Consumer<Carve> out) {
        this.segment(frame, segment, box -> new PatchFace(frame, box.sign(), box.center()), out);
    }

    /** Carves the edges and column patch nodes of one column segment. */
    void columnSegment(ShaftLayout.Column column, ShaftMaze.Segment segment, Consumer<Carve> out) {
        this.segment(this.frames.of(column), segment, box -> this.columnPatchFace(column, box), out);
    }

    private void segment(ShaftFrame frame, ShaftMaze.Segment segment, Function<ShaftMaze.Box, PatchFace> faceOf, Consumer<Carve> out) {
        Map<Integer, PatchFace> faces = segment.boxes().stream().collect(Collectors.toMap(ShaftMaze.Box::center, faceOf));
        for (PatchFace face : faces.values()) {
            this.patchNodes(face, out);
        }
        this.rulings(frame, segment.rulings(), segment.edges(), out);
        this.edges(frame, segment.edges(), faces::get, out);
    }

    /** The column patch face of a junction box, in the frame of its horizontal shaft. */
    PatchFace columnPatchFace(ShaftLayout.Column column, ShaftMaze.Box box) {
        return box.patching() == ShaftMaze.Patching.COLUMN_ALONG_X
                ? new PatchFace(new ShaftFrame(ShaftFrame.Kind.ALONG_X, box.center(), column.z()), box.sign(), column.x())
                : new PatchFace(new ShaftFrame(ShaftFrame.Kind.ALONG_Z, box.center(), column.x()), box.sign(), column.z());
    }

    private void patchNodes(PatchFace face, Consumer<Carve> out) {
        for (int i = -Ring.PATCH_INDEX; i <= Ring.PATCH_INDEX; i++) {
            for (int j = -Ring.PATCH_INDEX; j <= Ring.PATCH_INDEX; j++) {
                out.accept(face.node(i, j));
            }
        }
    }

    /**
     * Carves tree edges. A ruling to patch edge is a crossing.
     *
     * @param frame the frame of the rulings
     * @param faces the patch face of each box, by box center
     */
    private void edges(ShaftFrame frame, List<MazeEdge> edges, Function<Integer, PatchFace> faces, Consumer<Carve> out) {
        for (MazeEdge edge : edges) {
            if (edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b) {
                this.rulingEdge(frame, a, b, out);
            } else if (edge.a() instanceof MazeNode.Patch a && edge.b() instanceof MazeNode.Patch b) {
                out.accept(faces.apply(a.owner()).carve(faces.apply(a.owner()).center() + a.i() + b.i(), a.j() + b.j()));
            } else {
                MazeNode.Patch patch = (MazeNode.Patch) (edge.a() instanceof MazeNode.Patch ? edge.a() : edge.b());
                MazeNode.Ruling ruling = (MazeNode.Ruling) (edge.a() instanceof MazeNode.Ruling ? edge.a() : edge.b());
                this.crossing(frame, faces.apply(patch.owner()), patch, ruling, out);
            }
        }
    }

    /**
     * Carves a crossing from a patch border node onto the nearest ruling.
     * It runs along the outer layer of the ruling's shaft, then notches through the bezel of the box face.
     */
    private void crossing(ShaftFrame frame, PatchFace face, MazeNode.Patch patch, MazeNode.Ruling ruling, Consumer<Carve> out) {
        out.accept(face.node(patch.i(), patch.j()));
        boolean column = frame.kind() == ShaftFrame.Kind.COLUMN;
        int boxCenter = column ? face.frame().center1() : face.center();
        int faceAlong = face.center() + 2 * patch.i();
        int faceFirst = 2 * patch.j();
        int ringBlock = 2 * ruling.node();
        int side = ruling.along() < boxCenter ? -1 : 1;
        int start = Math.min(ruling.along(), boxCenter + side * Ring.BOX) + 1;
        int end = Math.max(ruling.along(), boxCenter + side * Ring.BOX) - 1;
        for (int along = start; along <= end; along++) {
            out.accept(this.block(frame, along, ringBlock));
        }
        for (int step = 0; step < Ring.BEZEL; step++) {
            int offset = side * (Ring.BOX - step);
            out.accept(column ? face.carve(faceAlong, offset) : face.carve(boxCenter + offset, faceFirst));
        }
    }

}
