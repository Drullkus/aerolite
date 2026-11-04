package us.drullk.aerolite.world.shaft.surface;

import us.drullk.aerolite.world.shaft.maze.Ring;

import java.util.ArrayList;
import java.util.List;

/**
 * The ring of the Pillar: one ring of 48 blocks around the 13x13 square.
 * Block 0 is the corner at offset (-6, 6). The ring runs along the face where the second offset is 6.
 * The four corners are corner nodes. A floor is the outer block clamped into the 11x11 interior.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class PillarRing implements RingShape {

    /** The Pillar ring. It holds no state that changes. */
    public static final PillarRing INSTANCE = new PillarRing();

    public static final int LENGTH = 48;
    private static final int SIDE = LENGTH / 4;

    private final int[] first = new int[LENGTH];
    private final int[] second = new int[LENGTH];
    private final RingKind[] kinds = new RingKind[LENGTH];
    private final List<List<SectionOffset>> floors = new ArrayList<>(LENGTH);
    private final List<SectionBlock> section;

    private PillarRing() {
        for (int index = 0; index < LENGTH; index++) {
            int step = index % SIDE;
            switch (index / SIDE) {
                case 0 -> this.set(index, -Ring.OUTER + step, Ring.OUTER);
                case 1 -> this.set(index, Ring.OUTER, Ring.OUTER - step);
                case 2 -> this.set(index, Ring.OUTER - step, -Ring.OUTER);
                default -> this.set(index, -Ring.OUTER, -Ring.OUTER + step);
            }
            this.kinds[index] = step == 0 ? RingKind.CORNER_NODE : RingKind.FACE;
            this.floors.add(List.of(new SectionOffset(clampInterior(this.first[index]), clampInterior(this.second[index]))));
        }
        List<SectionBlock> blocks = new ArrayList<>();
        for (int f = -Ring.OUTER; f <= Ring.OUTER; f++) {
            for (int s = -Ring.OUTER; s <= Ring.OUTER; s++) {
                blocks.add(new SectionBlock(f, s, Math.max(Math.abs(f), Math.abs(s)) == Ring.OUTER));
            }
        }
        this.section = List.copyOf(blocks);
    }

    private void set(int index, int first, int second) {
        this.first[index] = first;
        this.second[index] = second;
    }

    private static int clampInterior(int offset) {
        return Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, offset));
    }

    @Override
    public int rings() {
        return 1;
    }

    @Override
    public int length(int ring) {
        return LENGTH;
    }

    @Override
    public int first(int ring, int index) {
        return this.first[Math.floorMod(index, LENGTH)];
    }

    @Override
    public int second(int ring, int index) {
        return this.second[Math.floorMod(index, LENGTH)];
    }

    @Override
    public int index(int ring, int first, int second) {
        for (int index = 0; index < LENGTH; index++) {
            if (this.first[index] == first && this.second[index] == second) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public RingKind kind(int ring, int index) {
        return this.kinds[Math.floorMod(index, LENGTH)];
    }

    @Override
    public List<SectionOffset> floors(int ring, int index) {
        return this.floors.get(Math.floorMod(index, LENGTH));
    }

    @Override
    public List<SectionBlock> section() {
        return this.section;
    }

    /** The three nodes between the face corners that face the patch, at the cross offsets -2, 0 and 2. No foreign candidate. */
    @Override
    public List<CrossingCandidate> crossings(SectionAxis normal, int sign) {
        List<CrossingCandidate> candidates = new ArrayList<>();
        for (int k = 1 - Ring.PATCH_INDEX; k < Ring.PATCH_INDEX; k++) {
            int index = normal == SectionAxis.SECOND ? this.index(0, 2 * k, Ring.OUTER * sign) : this.index(0, Ring.OUTER * sign, 2 * k);
            candidates.add(new CrossingCandidate(index / 2, k, false));
        }
        return candidates;
    }

    @Override
    public int nodeCount() {
        return LENGTH / 2;
    }

    @Override
    public int firstNode(int ring) {
        return ring * LENGTH / 2;
    }

    @Override
    public int ringOf(int node) {
        return 0;
    }

    @Override
    public int indexOf(int node) {
        return 2 * node;
    }

    @Override
    public int node(int ring, int index) {
        return Math.floorMod(index, LENGTH) / 2;
    }

    @Override
    public boolean hasAxisEdge(int node) {
        return this.kinds[2 * node] != RingKind.CORNER_NODE;
    }

}
