package us.drullk.aerolite.world.shaft;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the placement of shafts and columns.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
class ShaftLayoutTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static ShaftLayout layout(long seed, int minSpacing, int maxSpacing) {
        return new ShaftLayout(seed, "minecraft:overworld", MIN_Y, MAX_Y, AeroliteSettings.DEFAULT.withShaftSpacing(minSpacing, maxSpacing));
    }

    @Test
    void slotsSplitBetweenTheAxesWithoutTheEdgeSlots() {
        ShaftLayout layout = layout(1, 128, 384);
        int[] x = layout.slots(ShaftLayout.Axis.X);
        int[] z = layout.slots(ShaftLayout.Axis.Z);
        assertEquals(11, x.length);
        assertEquals(11, z.length);
        int[] all = IntStream.concat(Arrays.stream(x), Arrays.stream(z)).sorted().toArray();
        assertTrue(Arrays.equals(IntStream.range(1, 23).toArray(), all));
    }

    @Test
    void neighborDistancesStayBetweenTheSpacingLimits() {
        int[][] limits = {{32, 32}, {32, 1024}, {128, 384}, {496, 512}, {1024, 1024}};
        for (int[] limit : limits) {
            ShaftLayout layout = layout(limit[0] + limit[1], limit[0], limit[1]);
            for (ShaftLayout.Axis axis : ShaftLayout.Axis.values()) {
                List<ShaftLayout.Shaft> shafts = layout.shafts(axis, -40 * limit[1], 40 * limit[1]);
                assertTrue(shafts.size() > 40);
                for (int k = 1; k < shafts.size(); k++) {
                    int distance = shafts.get(k).position() - shafts.get(k - 1).position();
                    assertTrue(distance >= limit[0] && distance <= limit[1], "distance " + distance);
                    assertTrue(distance - (2 * ShaftLayout.RADIUS + 1) >= 16);
                }
            }
        }
    }

    @Test
    void shaftsStayInTheirSlot() {
        ShaftLayout layout = layout(3, 128, 384);
        for (ShaftLayout.Axis axis : ShaftLayout.Axis.values()) {
            int[] slots = layout.slots(axis);
            for (ShaftLayout.Shaft shaft : layout.shafts(axis, -10000, 10000)) {
                int bottom = shaft.centerY() - ShaftLayout.RADIUS - MIN_Y;
                int slot = bottom / ShaftLayout.SLOT;
                int offset = bottom % ShaftLayout.SLOT;
                assertTrue(Arrays.stream(slots).anyMatch(s -> s == slot));
                assertTrue(offset == 1 || offset == 2);
            }
        }
    }

    @Test
    void columnsHaveRoomBetweenTheirJunctions() {
        ShaftLayout layout = layout(4, 64, 64);
        int found = 0;
        for (int xCell = -20; xCell < 20; xCell++) {
            for (int zCell = -20; zCell < 20; zCell++) {
                Optional<ShaftLayout.Column> column = layout.column(xCell, zCell);
                if (column.isPresent()) {
                    found++;
                    List<ColumnJunction> junctions = column.get().junctions();
                    assertTrue(Math.abs(column.get().xShaft().slot() - column.get().zShaft().slot()) >= 2);
                    int gap = junctions.getLast().center() - junctions.getFirst().center() - (2 * Ring.BOX + 1);
                    assertTrue(gap >= 16, "gap " + gap);
                    assertTrue(column.get().bottomShear() <= ShaftLayout.MAX_SHEAR && column.get().topShear() <= ShaftLayout.MAX_SHEAR);
                    int belowBox = junctions.getFirst().center() - Ring.BOX - MIN_Y - column.get().bottomShear();
                    int aboveBox = MAX_Y - 1 - column.get().topShear() - (junctions.getLast().center() + Ring.BOX);
                    assertTrue(belowBox >= ShaftLayout.SHEAR_MARGIN && aboveBox >= ShaftLayout.SHEAR_MARGIN);
                }
            }
        }
        assertTrue(found > 0);
    }

    /** The share of eligible crossings that get a column stays near the column chance on each single shaft. */
    @Test
    void columnShareMatchesTheChanceOnEachShaft() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withShaftSpacing(32, 80).withColumnChance(0.9F);
        ShaftLayout layout = new ShaftLayout(-777248163424996722L, "minecraft:overworld", MIN_Y, MAX_Y, settings);
        List<ShaftLayout.Shaft> crossings = layout.shafts(ShaftLayout.Axis.X, -6000, 6000);
        for (ShaftLayout.Shaft shaft : layout.shafts(ShaftLayout.Axis.Z, -1500, 1500)) {
            int eligible = 0;
            int columns = 0;
            for (ShaftLayout.Shaft crossing : crossings) {
                if (Math.abs(shaft.slot() - crossing.slot()) < 2) {
                    continue;
                }
                eligible++;
                if (layout.column(shaft.cell(), crossing.cell()).isPresent()) {
                    columns++;
                }
            }
            double share = columns / (double) eligible;
            assertTrue(share > 0.8 && share < 0.97, "share " + share + " on " + shaft);
        }
    }

    @Test
    void layoutIsPure() {
        assertEquals(layout(9, 128, 384).shafts(ShaftLayout.Axis.X, -4000, 4000), layout(9, 128, 384).shafts(ShaftLayout.Axis.X, -4000, 4000));
    }

}
