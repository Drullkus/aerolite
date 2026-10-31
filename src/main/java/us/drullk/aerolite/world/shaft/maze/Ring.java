package us.drullk.aerolite.world.shaft.maze;

import tamaized.beanification.Component;

/**
 * The maze grid around one shaft, seen as a ring of 48 blocks.
 * Block 0 is the corner at offset (-6, 6). The ring runs along the face where the second offset is 6.
 * Nodes sit on even blocks, so node {@code v} is block {@code 2v}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
@Component
public final class Ring {

    public static final int BLOCKS = 48;
    public static final int NODES = BLOCKS / 2;
    public static final int FACE_NODES = 6;
    /** The offset of the outer layer from the shaft center. */
    public static final int OUTER = 6;
    /** The offset of the junction box face from the junction center. */
    public static final int BOX = 7;
    /** The nodes along one side of a junction patch. */
    public static final int PATCH_NODES = 5;
    /** The largest patch node index. The indices run from minus this value to this value. */
    public static final int PATCH_INDEX = PATCH_NODES / 2;
    /** The largest face offset of the patch area. The patch border nodes sit on it. */
    public static final int PATCH_EDGE = 2 * PATCH_INDEX;
    /** The thickness of the bezel, the frame between the patch area and the box edge. */
    public static final int BEZEL = BOX - PATCH_EDGE;

    public boolean isCorner(int node) {
        return node % FACE_NODES == 0;
    }

    public int first(int block) {
        block = Math.floorMod(block, BLOCKS);
        if (block < 12) {
            return -OUTER + block;
        }
        if (block < 24) {
            return OUTER;
        }
        if (block < 36) {
            return OUTER - (block - 24);
        }
        return -OUTER;
    }

    public int second(int block) {
        block = Math.floorMod(block, BLOCKS);
        if (block < 12) {
            return OUTER;
        }
        if (block < 24) {
            return OUTER - (block - 12);
        }
        if (block < 36) {
            return -OUTER;
        }
        return -OUTER + (block - 36);
    }

    /** The ring block at the offset, or -1 when the offset is not on the outer layer. */
    public int block(int first, int second) {
        if (second == OUTER && first >= -OUTER && first < OUTER) {
            return first + OUTER;
        }
        if (first == OUTER && second > -OUTER && second <= OUTER) {
            return 12 + (OUTER - second);
        }
        if (second == -OUTER && first > -OUTER && first <= OUTER) {
            return 24 + (OUTER - first);
        }
        if (first == -OUTER && second >= -OUTER && second < OUTER) {
            return 36 + (second + OUTER);
        }
        return -1;
    }

    /** The node on the face where the second offset is {@code 6 * sign}, at the first offset {@code 2k}. */
    public int faceNode(int sign, int k) {
        return block(2 * k, OUTER * sign) / 2;
    }

    /** The ring block between two neighbor nodes. */
    public int blockBetween(int node1, int node2) {
        return Math.abs(node1 - node2) == 1 ? 2 * Math.min(node1, node2) + 1 : BLOCKS - 1;
    }

    public int oddBelow(int a) {
        return (a & 1) != 0 ? a : a - 1;
    }

    public int oddAbove(int a) {
        return (a & 1) != 0 ? a : a + 1;
    }

}
