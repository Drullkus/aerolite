package us.drullk.aerolite.world.shaft;

import tamaized.beanification.Component;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.RingShape;

/**
 * The erosion resistance of shaft and junction box blocks, from 0 at a surface to 1 at a core.
 * The ring description of each shape gives the resistance of its cross section. A truss block and a truss anchor have {@value #TRUSS}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ErosionResistance {

    /** The resistance of a truss block and of a truss anchor. */
    public static final double TRUSS = 0.5;

    /** The resistance of a box block of two Pillars. */
    public double box(ColumnJunction junction, int dx, int dy, int dz) {
        return this.box(junction, dx, dy, dz, PillarRing.INSTANCE, PillarRing.INSTANCE);
    }

    /** The resistance of a box block. It is the largest of the box resistance and the resistance of each shaft through the block. */
    public double box(ColumnJunction junction, int dx, int dy, int dz, RingShape column, RingShape horizontal) {
        double box = (double) (Ring.BOX - Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)))) / (Ring.BOX - 1);
        double columnResistance = column.resistance(dx, dz);
        double horizontalResistance = junction.alongX() ? horizontal.resistance(dy, dz) : horizontal.resistance(dy, dx);
        return Math.max(box, Math.max(columnResistance, horizontalResistance));
    }

    /** The resistance in a Pillar cross section, 0 outside the section and 1 at the 3x3 core. */
    public double section(int first, int second) {
        return PillarRing.INSTANCE.resistance(first, second);
    }

    /** The resistance of a block of the cross section of the ring at an axis position. A truss block takes {@value #TRUSS}. */
    public double section(RingShape ring, int along, int first, int second) {
        return ring.isTruss(along, first, second) ? TRUSS : ring.resistance(first, second);
    }

}
