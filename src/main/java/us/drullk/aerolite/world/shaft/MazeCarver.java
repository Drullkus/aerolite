package us.drullk.aerolite.world.shaft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import us.drullk.aerolite.world.shaft.maze.ColumnMaze;
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
 */
final class MazeCarver {

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

    private MazeCarver() {
    }

    static Carve block(ShaftFrame frame, int along, int block) {
        int first = Ring.first(block);
        int second = Ring.second(block);
        return new Carve(frame.at(along, first, second), frame.at(along, clampInterior(first), clampInterior(second)), null);
    }

    private static int clampInterior(int offset) {
        return Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, offset));
    }

    /** Carves the ruling nodes of the rulings. A corner node carves only when an edge holds it. */
    static void rulings(ShaftFrame frame, int[] rulings, List<MazeEdge> edges, Consumer<Carve> out) {
        Set<MazeNode> present = new HashSet<>();
        for (MazeEdge edge : edges) {
            present.add(edge.a());
            present.add(edge.b());
        }
        for (int along : rulings) {
            for (int v = 0; v < Ring.NODES; v++) {
                if (!Ring.isCorner(v) || present.contains(new MazeNode.Ruling(along, v))) {
                    out.accept(block(frame, along, 2 * v));
                }
            }
        }
    }

    static void rulingEdge(ShaftFrame frame, MazeNode.Ruling a, MazeNode.Ruling b, Consumer<Carve> out) {
        if (a.along() == b.along()) {
            out.accept(block(frame, a.along(), Ring.blockBetween(a.node(), b.node())));
        } else {
            out.accept(block(frame, Math.min(a.along(), b.along()) + 1, 2 * a.node()));
        }
    }

    /** Carves the edges and patch nodes of one horizontal shaft segment. */
    static void shaftSegment(ShaftFrame frame, ShaftMaze.Segment segment, Consumer<Carve> out) {
        Map<Integer, PatchFace> faces = segment.boxes().stream()
                .collect(Collectors.toMap(ShaftMaze.Box::center, box -> new PatchFace(frame, box.sign(), box.center())));
        for (PatchFace face : faces.values()) {
            patchNodes(face, out);
        }
        rulings(frame, segment.rulings(), segment.edges(), out);
        edges(frame, segment.edges(), faces::get, out);
    }

    /** Carves the pieces and the column patches of one column. */
    static void column(ShaftLayout.Column column, ColumnMaze maze, Consumer<Carve> out) {
        ShaftFrame frame = ShaftFrame.of(column);
        for (ColumnMaze.Piece piece : maze.pieces()) {
            if (piece.isEmpty()) {
                continue;
            }
            int[] rulings = new int[(piece.last() - piece.first()) / 2 + 1];
            for (int u = 0; u < rulings.length; u++) {
                rulings[u] = piece.first() + 2 * u;
            }
            rulings(frame, rulings, piece.edges(), out);
            edges(frame, piece.edges(), owner -> null, out);
        }
        for (ColumnMaze.PatchTree patch : maze.patches()) {
            PatchFace face = columnPatchFace(column, patch.junction());
            patchNodes(face, out);
            edges(frame, patch.edges(), owner -> face, out);
        }
    }

    /** The column patch face of a junction, in the frame of its horizontal shaft. */
    static PatchFace columnPatchFace(ShaftLayout.Column column, ColumnMaze.Junction junction) {
        return junction.alongX()
                ? new PatchFace(new ShaftFrame(ShaftFrame.Kind.ALONG_X, junction.center(), column.z()), junction.sign(), column.x())
                : new PatchFace(new ShaftFrame(ShaftFrame.Kind.ALONG_Z, junction.center(), column.x()), junction.sign(), column.z());
    }

    private static void patchNodes(PatchFace face, Consumer<Carve> out) {
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
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
    private static void edges(ShaftFrame frame, List<MazeEdge> edges, Function<Integer, PatchFace> faces, Consumer<Carve> out) {
        for (MazeEdge edge : edges) {
            if (edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b) {
                rulingEdge(frame, a, b, out);
            } else if (edge.a() instanceof MazeNode.Patch a && edge.b() instanceof MazeNode.Patch b) {
                out.accept(faces.apply(a.owner()).carve(faces.apply(a.owner()).center() + a.i() + b.i(), a.j() + b.j()));
            } else {
                MazeNode.Patch patch = (MazeNode.Patch) (edge.a() instanceof MazeNode.Patch ? edge.a() : edge.b());
                MazeNode.Ruling ruling = (MazeNode.Ruling) (edge.a() instanceof MazeNode.Ruling ? edge.a() : edge.b());
                crossing(frame, faces.apply(patch.owner()), patch, ruling, out);
            }
        }
    }

    /**
     * Carves a crossing from a patch border node onto the nearest ruling.
     * It runs along the outer layer of the ruling's shaft, then steps up onto the box face.
     */
    private static void crossing(ShaftFrame frame, PatchFace face, MazeNode.Patch patch, MazeNode.Ruling ruling, Consumer<Carve> out) {
        out.accept(face.node(patch.i(), patch.j()));
        boolean column = frame.kind() == ShaftFrame.Kind.COLUMN;
        int boxCenter = column ? face.frame().center1() : face.center();
        int faceAlong = face.center() + 2 * patch.i();
        int faceFirst = 2 * patch.j();
        int ringBlock = 2 * ruling.node();
        if (ruling.along() < boxCenter) {
            for (int along = ruling.along() + 1; along < boxCenter - Ring.BOX; along++) {
                out.accept(block(frame, along, ringBlock));
            }
            out.accept(column ? face.carve(faceAlong, -Ring.BOX) : face.carve(boxCenter - Ring.BOX, faceFirst));
        } else {
            for (int along = boxCenter + Ring.BOX + 1; along < ruling.along(); along++) {
                out.accept(block(frame, along, ringBlock));
            }
            out.accept(column ? face.carve(faceAlong, Ring.BOX) : face.carve(boxCenter + Ring.BOX, faceFirst));
        }
    }

}
