package us.drullk.aerolite.world.shaft.shape;

import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.surface.CrossingCandidate;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * The ring of the I-beam: one ring of 64 blocks around the H, with 32 nodes.
 *
 * <p>The flanges are 13 wide and 4 thick, at |v| from 3 to 6. The web is 5 thick and 5 tall, at |u| and |v| up to 2.
 * The ring holds the hidden block of each concave corner, so the web nodes sit at v = 2, 0 and -2.
 * The 4 outer corners are corner nodes. The 4 inner flange corners are convex edges. The 4 concave corners are concave edges.
 * A groove across a concave corner leaves an L-shaped floor of two blocks.
 * Each flange keeps its two middle layers at resistance 1. The web keeps its middle layer at resistance 1.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class IBeamRing extends TracedRing {

    public static final int LENGTH = 64;
    /** The offset |v| of the inner face of a flange. */
    public static final int FLANGE_INNER = 3;
    /** The offset |u| of a web face. */
    public static final int WEB_HALF = 2;
    /** The offset |v| of the flange edge nodes that face a patch. */
    private static final int EDGE_NODE = 4;

    /** The I-beam with v on the first offset. A horizontal I-beam and a column of web plane X use it. */
    public static final IBeamRing STACK_FIRST = new IBeamRing(SectionAxis.FIRST);
    /** The I-beam with v on the second offset. A column of web plane Z uses it. */
    public static final IBeamRing STACK_SECOND = new IBeamRing(SectionAxis.SECOND);

    private IBeamRing(SectionAxis stack) {
        super(stack, new int[][]{{-Ring.OUTER, Ring.OUTER}});
    }

    @Override
    protected boolean solid(int u, int v) {
        int au = Math.abs(u);
        int av = Math.abs(v);
        return au <= Ring.OUTER && av <= Ring.OUTER && (av >= FLANGE_INNER || au <= WEB_HALF);
    }

    @Override
    protected double resistanceAt(int u, int v) {
        // Layers from a surface to the middle: 1 in a flange, 2 in the web.
        return Math.abs(v) >= FLANGE_INNER ? this.plateResistance(u, v, true, 1) : this.plateResistance(u, v, false, 2);
    }

    /**
     * A flange face toward the patch gives the 3 nodes of a Pillar face.
     * The flange edges toward the patch give the edge nodes at v = 4 and v = -4, onto the patch corner nodes. The web nodes give none.
     */
    @Override
    public List<CrossingCandidate> crossings(SectionAxis normal, int sign) {
        List<CrossingCandidate> candidates = new ArrayList<>();
        if (normal == this.stack()) {
            for (int k = 1 - Ring.PATCH_INDEX; k < Ring.PATCH_INDEX; k++) {
                candidates.add(new CrossingCandidate(this.nodeUV(0, 2 * k, Ring.OUTER * sign), k, false));
            }
        } else {
            for (int v : new int[]{-EDGE_NODE, EDGE_NODE}) {
                candidates.add(new CrossingCandidate(this.nodeUV(0, Ring.OUTER * sign, v), v / 2, false));
            }
        }
        return candidates;
    }

}
