package us.drullk.aerolite.world.shaft;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.WeightedVariation;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.MazeEdges;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the placement of shafts and columns.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_18-08_4c0300c8-1a76-4b8e-86b0-0cb9daa7d32a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
class ShaftLayoutTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private final Ring ring = new Ring();
    private final ShaftMazes mazes = new ShaftMazes(this.ring, new MazeEdges(this.ring));

    private ShaftLayout layout(long seed, int minSpacing, int maxSpacing) {
        return new ShaftLayout(this.mazes, seed, Level.OVERWORLD, MIN_Y, MAX_Y, TestSettings.OVERWORLD.withPlacement(TestSettings.PLACEMENT.withShaftSpacing(minSpacing, maxSpacing)));
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
        int raisedEnds = 0;
        for (int xCell = -20; xCell < 20; xCell++) {
            for (int zCell = -20; zCell < 20; zCell++) {
                Optional<ShaftLayout.Column> column = layout.column(xCell, zCell);
                if (column.isPresent()) {
                    found++;
                    List<ColumnJunction> junctions = column.get().junctions();
                    assertTrue(Math.abs(column.get().xShaft().slot() - column.get().zShaft().slot()) >= 2);
                    int gap = junctions.getLast().center() - junctions.getFirst().center() - (2 * Ring.BOX + 1);
                    assertTrue(gap >= 16, "gap " + gap);
                    ShaftLayout.Column c = column.get();
                    assertTrue(c.bottomShear() <= ShaftLayout.MAX_SHEAR && c.topShear() <= ShaftLayout.MAX_SHEAR);
                    assertTrue(c.bottomShear() >= ShaftLayout.MIN_SHEAR && c.topShear() >= ShaftLayout.MIN_SHEAR);
                    assertTrue(c.bottomEnd() >= MIN_Y && c.topEnd() <= MAX_Y);
                    int belowBox = junctions.getFirst().center() - Ring.BOX - (c.bottomEnd() + c.bottomShear());
                    int aboveBox = (c.topEnd() - c.topShear()) - (junctions.getLast().center() + Ring.BOX + 1);
                    assertTrue(belowBox >= ShaftLayout.SHEAR_MARGIN && aboveBox >= ShaftLayout.SHEAR_MARGIN, belowBox + " " + aboveBox);
                    if (c.bottomEnd() > MIN_Y) {
                        raisedEnds++;
                    }
                }
            }
        }
        assertTrue(found > 0);
        assertTrue(raisedEnds > found / 2, "most column bottoms stand above the build limit");
    }

    /** The share of eligible crossings that get a column stays near the column chance on each single shaft. */
    @Test
    void columnShareMatchesTheChanceOnEachShaft() {
        DimensionSettings settings = TestSettings.OVERWORLD.withPlacement(TestSettings.PLACEMENT.withShaftSpacing(32, 80).withColumnChance(0.9F));
        ShaftLayout layout = new ShaftLayout(this.mazes, -777248163424996722L, Level.OVERWORLD, MIN_Y, MAX_Y, settings);
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

    private static final ShaftVariation NEVER = TestSettings.TUFF;
    private static final ShaftVariation HEAVY = TestSettings.STONE;
    private static final ShaftVariation LIGHT = new ShaftVariation(0.2F, TestSettings.DECAY_SCALE, TestSettings.DECAY_THRESHOLD, TestSettings.EROSION_THRESHOLD,
            Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState());

    private ShaftLayout pickLayout(long seed) {
        DimensionSettings settings = new DimensionSettings(TestSettings.PLACEMENT.withShaftSpacing(32, 64).withColumnChance(1),
                List.of(new WeightedVariation(0, NEVER), new WeightedVariation(3, HEAVY), new WeightedVariation(1, LIGHT)));
        return new ShaftLayout(this.mazes, seed, Level.OVERWORLD, MIN_Y, MAX_Y, settings);
    }

    private List<ShaftVariation> picks(ShaftLayout layout) {
        List<ShaftVariation> picks = new ArrayList<>();
        for (ShaftLayout.Axis axis : ShaftLayout.Axis.values()) {
            for (ShaftLayout.Shaft shaft : layout.shafts(axis, -20000, 20000)) {
                picks.add(layout.variation(shaft));
            }
        }
        for (int xCell = -30; xCell < 30; xCell++) {
            for (int zCell = -30; zCell < 30; zCell++) {
                layout.column(xCell, zCell).map(layout::variation).ifPresent(picks::add);
            }
        }
        return picks;
    }

    @Test
    void variationPickIsPure() {
        ShaftLayout layout = pickLayout(12);
        assertEquals(picks(layout), picks(layout), "A second pass over one layout gives other picks");
        assertEquals(picks(layout), picks(pickLayout(12)), "Two layouts of one seed give other picks");
    }

    @Test
    void variationWeightOfZeroNeverPicks() {
        List<ShaftVariation> picks = picks(pickLayout(13));
        assertTrue(picks.size() > 1000);
        assertFalse(picks.contains(NEVER));
        long heavy = picks.stream().filter(HEAVY::equals).count();
        long light = picks.stream().filter(LIGHT::equals).count();
        assertTrue(light > 0, "The light variation never picks");
        double share = heavy / (double) picks.size();
        assertTrue(share > 0.65 && share < 0.85, "share " + share);
    }

    @Test
    void mazeTakesTheBiasOfTheVariation() {
        ShaftLayout layout = pickLayout(14);
        for (ShaftLayout.Shaft shaft : layout.shafts(ShaftLayout.Axis.X, -4000, 4000)) {
            assertEquals(layout.variation(shaft).bias(), layout.shaftMaze(shaft).bias());
        }
    }

    @Test
    void unlistedDimensionHasNoShafts() {
        AeroliteSettings settings = TestSettings.SETTINGS;
        ResourceKey<Level> elsewhere = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aerolite", "elsewhere"));
        ShaftLayout unlisted = new ShaftLayout(this.mazes, 1, elsewhere, MIN_Y, MAX_Y, settings.dimension(elsewhere));
        assertFalse(unlisted.hasShafts());
        ShaftLayout nether = new ShaftLayout(this.mazes, 1, Level.NETHER, MIN_Y, MAX_Y, settings.dimension(Level.NETHER));
        assertTrue(nether.hasShafts());
        assertSame(TestSettings.NETHER, nether.variation(nether.shaft(ShaftLayout.Axis.X, 0).orElseThrow()));
        ShaftLayout overworld = new ShaftLayout(this.mazes, 1, Level.OVERWORLD, MIN_Y, MAX_Y, settings.dimension(Level.OVERWORLD));
        assertNotSame(TestSettings.NETHER, overworld.variation(overworld.shaft(ShaftLayout.Axis.X, 0).orElseThrow()));
    }

    /** Each decay scale has one noise. Two variations at one scale share it. */
    @Test
    void decayNoiseFollowsTheScaleOfTheVariation() {
        ShaftLayout layout = layout(3, 128, 384);
        ShaftVariation small = TestSettings.STONE.withDecayScale(32);
        ShaftVariation large = TestSettings.STONE.withDecayScale(1024);
        assertSame(layout.decayNoise(small), layout.decayNoise(TestSettings.TUFF.withDecayScale(32)));
        assertNotSame(layout.decayNoise(small), layout.decayNoise(large));
        int changes = 0;
        for (int x = 0; x < 64; x++) {
            if (Math.signum(layout.decayNoise(small).sample(x, 0, 0)) != Math.signum(layout.decayNoise(small).sample(x + 1, 0, 0))) {
                changes++;
            }
        }
        int largeChanges = 0;
        for (int x = 0; x < 64; x++) {
            if (Math.signum(layout.decayNoise(large).sample(x, 0, 0)) != Math.signum(layout.decayNoise(large).sample(x + 1, 0, 0))) {
                largeChanges++;
            }
        }
        assertTrue(changes > largeChanges, "The small scale noise changes sign no more often than the large scale noise");
    }

    /** A fingerprint of the shafts, the columns and their variations, from the defaults of version 0.2.1 with seed 3446441043220923556. */
    private static final Map<ResourceKey<Level>, String> DEFAULT_FINGERPRINTS = Map.of(
            Level.OVERWORLD, "c8a097a35c770874a5eb64285b21d3f2767d8dfe08dd1478c7ca80f69e4bdd5f",
            Level.NETHER, "f92fe707a8806b6191e47885f3fddd5b632ade2c7a2692d91ee1f5b90232e4c6",
            Level.END, "6ebbdc82d3606a2932d1ccb4fafb4d31b3bbe0c010596792a8e3ce88ef0d60e0"
    );

    /** The shipped data gives the same shafts as the defaults of version 0.2.1 for one seed. */
    @Test
    void shippedDefaultsKeepTheEarlierShafts() throws NoSuchAlgorithmException {
        for (Map.Entry<ResourceKey<Level>, String> expected : DEFAULT_FINGERPRINTS.entrySet()) {
            ResourceKey<Level> dimension = expected.getKey();
            int minY = dimension == Level.OVERWORLD ? -64 : 0;
            int maxY = dimension == Level.OVERWORLD ? 320 : 256;
            ShaftLayout layout = new ShaftLayout(this.mazes, 3446441043220923556L, dimension, minY, maxY, ShippedShaftData.lookup().defaults(dimension));
            StringBuilder print = new StringBuilder();
            for (ShaftLayout.Axis axis : ShaftLayout.Axis.values()) {
                for (ShaftLayout.Shaft shaft : layout.shafts(axis, -3000, 3000)) {
                    print.append(shaft).append(fingerprint(layout.variation(shaft))).append('\n');
                }
            }
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    int xCell = x;
                    int zCell = z;
                    layout.column(x, z).ifPresent(column -> print.append(xCell).append(',').append(zCell).append(',').append(column.bottomEnd()).append(',')
                            .append(column.topEnd()).append(fingerprint(layout.variation(column))).append('\n'));
                }
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(print.toString().getBytes(StandardCharsets.UTF_8));
            assertEquals(expected.getValue(), HexFormat.of().formatHex(hash), "The shafts of " + dimension.location() + " changed");
        }
    }

    private static String fingerprint(ShaftVariation variation) {
        return "|" + variation.bias() + "|" + variation.decayScale() + "|" + variation.decayThreshold() + "|" + variation.erosionThreshold()
                + "|" + variation.interior() + "|" + variation.wall() + "|" + variation.path();
    }

}
