package us.drullk.aerolite.world.shaft;

import tamaized.beanification.Component;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;

/**
 * The erosion resistance of shaft and junction box blocks, from 0 at a surface to 1 at a core.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component
public final class ErosionResistance {

    /** The resistance of a box block. It is the largest of the box resistance and the resistance of each shaft through the block. */
    public double box(ColumnJunction junction, int dx, int dy, int dz) {
        double box = (double) (Ring.BOX - Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)))) / (Ring.BOX - 1);
        double column = this.section(dx, dz);
        double horizontal = junction.alongX() ? this.section(dy, dz) : this.section(dy, dx);
        return Math.max(box, Math.max(column, horizontal));
    }

    /** The resistance in a shaft cross section, 0 outside the section and 1 at the 3x3 core. */
    public double section(int first, int second) {
        int offset = Math.max(Math.abs(first), Math.abs(second));
        return offset > Ring.OUTER ? 0 : Math.min(1, (double) (Ring.OUTER - offset) / (Ring.OUTER - 1));
    }

}
