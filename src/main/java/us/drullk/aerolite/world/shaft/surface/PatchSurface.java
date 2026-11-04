package us.drullk.aerolite.world.shaft.surface;

import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

/**
 * The surface of one exposed face of a junction box: 15x15 cells around the face center.
 * The offset {@code a} runs along the horizontal pillar of the box. The offset {@code f} is the first offset of that pillar.
 * A new patch surface is all wall. A bezel cell is wall or groove. The paint never carves a wall cell.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class PatchSurface {

    public static final int SIZE = 2 * Ring.BOX + 1;
    private static final BandKind[] KINDS = BandKind.values();

    private final ShaftMaze.Box box;
    private final boolean foreign;
    private final byte[] kinds = new byte[SIZE * SIZE];

    /**
     * @param foreign true for the face opposite the patch of the box, which belongs to the other pillar
     */
    public PatchSurface(ShaftMaze.Box box, boolean foreign) {
        this.box = box;
        this.foreign = foreign;
    }

    public ShaftMaze.Box box() {
        return this.box;
    }

    public boolean foreign() {
        return this.foreign;
    }

    /** True when the cell lies in the bezel, outside the patch area. */
    public static boolean isBezel(int a, int f) {
        return Ring.BOX - Math.max(Math.abs(a), Math.abs(f)) < Ring.BEZEL;
    }

    private static int index(int a, int f) {
        if (Math.abs(a) > Ring.BOX || Math.abs(f) > Ring.BOX) {
            throw new IndexOutOfBoundsException("Cell (" + a + ", " + f + ") is outside the box face");
        }
        return (a + Ring.BOX) * SIZE + f + Ring.BOX;
    }

    public BandKind get(int a, int f) {
        return KINDS[this.kinds[index(a, f)]];
    }

    public void set(int a, int f, BandKind kind) {
        this.kinds[index(a, f)] = (byte) kind.ordinal();
    }

}
