package us.drullk.aerolite.world.shaft.shape;

import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.CrossingCandidate;
import us.drullk.aerolite.world.shaft.surface.RingKind;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;

import java.util.ArrayList;
import java.util.List;

/**
 * The rings of the Bridge: two plates, 3 thick and 13 wide at |v| from 4 to 6, with 7 truss rows between them.
 *
 * <p>Ring 0 is the top plate at positive v. Ring 1 is the bottom plate. Each ring has 28 blocks:
 * the outer face (13), one side block, the inner face (13) and the other side block.
 * The 4 plate corners sit on even indices and are corner nodes.
 * The outer face carries grooves. The inner face is flush from u = -5 to 5: a groove there is a flush maze path block.
 *
 * <p>The trusses stand at u = 4 and -5 (red) and at u = 5 and -4 (green), two blocks wide per row, with a period of 8.
 * Row r is v = 6 - r. Both diagonals anchor in the inner plate layers at s mod 8 = 3 and 4.
 * A horizontal Bridge runs through its junction boxes in a hole, so the box does not cut its plates.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class BridgeRing extends TracedRing {

    public static final int LENGTH = 28;
    /** The offset |v| of the inner face of a plate. */
    public static final int PLATE_INNER = 4;
    /** The largest offset |u| of the flush inner face. */
    public static final int FLUSH_HALF = 5;
    /** The truss period along the axis. */
    public static final int PERIOD = 8;
    /** The offsets u of the red truss layers. */
    private static final int[] RED = {4, -5};
    /** The offsets u of the green truss layers. */
    private static final int[] GREEN = {5, -4};

    /** The Bridge with v on the first offset. A horizontal Bridge and a column of web plane X use it. */
    public static final BridgeRing STACK_FIRST = new BridgeRing(SectionAxis.FIRST);
    /** The Bridge with v on the second offset. A column of web plane Z uses it. */
    public static final BridgeRing STACK_SECOND = new BridgeRing(SectionAxis.SECOND);

    private final List<List<SectionOffset>> trusses = new ArrayList<>(PERIOD);

    private BridgeRing(SectionAxis stack) {
        super(stack, new int[][]{{-Ring.OUTER, Ring.OUTER}, {-Ring.OUTER, -Ring.OUTER}});
        for (int m = 0; m < PERIOD; m++) {
            List<SectionOffset> blocks = new ArrayList<>();
            for (int v = -PLATE_INNER; v <= PLATE_INNER; v++) {
                int row = Ring.OUTER - v;
                boolean red = m == Math.floorMod(5 - row, PERIOD) || m == Math.floorMod(6 - row, PERIOD);
                boolean green = m == Math.floorMod(row + 1, PERIOD) || m == Math.floorMod(row + 2, PERIOD);
                for (int u : RED) {
                    if (red) {
                        blocks.add(new SectionOffset(this.firstOf(u, v), this.secondOf(u, v)));
                    }
                }
                for (int u : GREEN) {
                    if (green) {
                        blocks.add(new SectionOffset(this.firstOf(u, v), this.secondOf(u, v)));
                    }
                }
            }
            this.trusses.add(List.copyOf(blocks));
        }
    }

    @Override
    protected boolean solid(int u, int v) {
        int au = Math.abs(u);
        int av = Math.abs(v);
        return au <= Ring.OUTER && av >= PLATE_INNER && av <= Ring.OUTER;
    }

    @Override
    protected double resistanceAt(int u, int v) {
        return this.plateResistance(u, v, true, 1);
    }

    @Override
    protected RingKind faceKind(int u, int v) {
        return Math.abs(u) == Ring.OUTER ? RingKind.PLATE_SIDE : RingKind.FACE;
    }

    @Override
    public boolean flush(int ring, int index) {
        return Math.abs(this.ringV(ring, index)) == PLATE_INNER && Math.abs(this.ringU(ring, index)) <= FLUSH_HALF;
    }

    @Override
    public List<SectionOffset> trusses(int along) {
        return this.trusses.get(Math.floorMod(along, PERIOD));
    }

    /** A column Bridge is cut by the solid box. A horizontal Bridge runs uncut through the hole. */
    @Override
    public boolean boxCutsRings(ShaftMaze.Box box) {
        return box.patching() != ShaftMaze.Patching.SHAFT;
    }

    @Override
    public boolean holeThroughBox() {
        return true;
    }

    /**
     * The plate sides toward the patch give the side corner node of each plate at v = 4 and v = -4.
     * The outer faces toward the patch give the 3 middle nodes of the near plate, and the same nodes of the far plate as foreign candidates.
     */
    @Override
    public List<CrossingCandidate> crossings(SectionAxis normal, int sign) {
        List<CrossingCandidate> candidates = new ArrayList<>();
        if (normal == this.cross()) {
            candidates.add(new CrossingCandidate(this.nodeUV(0, Ring.OUTER * sign, PLATE_INNER), PLATE_INNER / 2, false));
            candidates.add(new CrossingCandidate(this.nodeUV(1, Ring.OUTER * sign, -PLATE_INNER), -PLATE_INNER / 2, false));
        } else {
            int near = sign > 0 ? 0 : 1;
            for (int k = 1 - Ring.PATCH_INDEX; k < Ring.PATCH_INDEX; k++) {
                candidates.add(new CrossingCandidate(this.nodeUV(near, 2 * k, Ring.OUTER * sign), k, false));
            }
            for (int k = 1 - Ring.PATCH_INDEX; k < Ring.PATCH_INDEX; k++) {
                candidates.add(new CrossingCandidate(this.nodeUV(1 - near, 2 * k, -Ring.OUTER * sign), k, true));
            }
        }
        return candidates;
    }

    /** A horizontal Bridge gives exactly one candidate per junction: one plate, picked by the random of the box. */
    @Override
    public List<CrossingCandidate> crossings(SectionAxis normal, int sign, ShaftMaze.Box box, RandomSource random) {
        List<CrossingCandidate> candidates = this.crossings(normal, sign);
        if (box.patching() != ShaftMaze.Patching.SHAFT) {
            return candidates;
        }
        List<CrossingCandidate> own = candidates.stream().filter(candidate -> !candidate.foreign()).toList();
        return List.of(own.get(random.nextInt(own.size())));
    }

}
