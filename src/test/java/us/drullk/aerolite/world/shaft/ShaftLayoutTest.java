package us.drullk.aerolite.world.shaft;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.shaft.maze.ColumnMaze;

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

    private static ShaftLayout layout(long seed, int spacing) {
        AeroliteSettings settings = new AeroliteSettings(spacing, 0.25F, 256, 0.5F,
                AeroliteSettings.DEFAULT.interiorBlock(), AeroliteSettings.DEFAULT.wallBlock(), AeroliteSettings.DEFAULT.pathBlock());
        return new ShaftLayout(seed, "minecraft:overworld", MIN_Y, MAX_Y, settings);
    }

    @Test
    void slotsSplitBetweenTheAxes() {
        ShaftLayout layout = layout(1, 256);
        int[] x = layout.slots(ShaftLayout.Axis.X);
        int[] z = layout.slots(ShaftLayout.Axis.Z);
        assertEquals(12, x.length);
        assertEquals(12, z.length);
        int[] all = IntStream.concat(Arrays.stream(x), Arrays.stream(z)).sorted().toArray();
        assertTrue(Arrays.equals(IntStream.range(0, 24).toArray(), all));
    }

    @Test
    void neighborShaftsKeepSixteenBlocksApart() {
        for (int spacing : new int[]{32, 64, 256}) {
            ShaftLayout layout = layout(spacing, spacing);
            for (ShaftLayout.Axis axis : ShaftLayout.Axis.values()) {
                List<ShaftLayout.Shaft> shafts = layout.shafts(axis, -20 * spacing, 20 * spacing);
                for (int k = 1; k < shafts.size(); k++) {
                    int gap = shafts.get(k).position() - shafts.get(k - 1).position() - (2 * ShaftLayout.RADIUS + 1);
                    assertTrue(gap >= 16, "gap " + gap);
                }
            }
        }
    }

    @Test
    void shaftsStayInTheirSlot() {
        ShaftLayout layout = layout(3, 256);
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
        ShaftLayout layout = layout(4, 64);
        int found = 0;
        for (int xCell = -20; xCell < 20; xCell++) {
            for (int zCell = -20; zCell < 20; zCell++) {
                Optional<ShaftLayout.Column> column = layout.column(xCell, zCell);
                if (column.isPresent()) {
                    found++;
                    assertTrue(ColumnMaze.fits(column.get().junctions()));
                    assertTrue(column.get().bottomShear() >= ShaftLayout.MIN_SHEAR && column.get().bottomShear() <= ShaftLayout.MAX_SHEAR);
                }
            }
        }
        assertTrue(found > 0);
    }

    @Test
    void layoutIsPure() {
        assertEquals(layout(9, 256).shafts(ShaftLayout.Axis.X, -4000, 4000), layout(9, 256).shafts(ShaftLayout.Axis.X, -4000, 4000));
    }

}
