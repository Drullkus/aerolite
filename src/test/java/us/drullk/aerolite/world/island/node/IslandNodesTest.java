package us.drullk.aerolite.world.island.node;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.PreviewOverride;
import us.drullk.aerolite.world.island.variation.UnitRange;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the island function and each island node against the lattice values, and the island body against {@code VoronoiIslands}.
 * Also checks the Empty cells, the cells outside the row 0, the codec of the island function, a node outside an island function,
 * the nearest cell of the row 0 and the preview override.
 * It checks the cell against the power rule with the Y scale, the carrier, and the bounds during the registry load.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandNodesTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));
    private static final BiomeSource BIOMES = new CheckerboardColumnBiomeSource(HolderSet.direct(), 2);
    private static final ResourceKey<IslandVariationList> LIST = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, id("test_islands"));
    private static final ResourceKey<IslandVariation> BASIC = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id("test_basic"));
    private static final ResourceKey<IslandVariation> EMPTY = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id("test_empty"));
    private static final int POINTS = 3000;
    private static final double TOLERANCE = 1.0E-9;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerolite", path);
    }

    /** An island function over a list of Empty at weight 1 and one island of the density function at weight 9. */
    private static IslandFunction islands(DensityFunction function, UnitRange cellWeight, int cellHeight, double distortion) {
        IslandVariation basic = IslandVariation.island(Holder.direct(function), BIOMES, cellWeight, new UnitRange(0.3, 0.9), distortion, 64, 3);
        IslandVariation empty = IslandVariation.empty(BIOMES, IslandVariation.DEFAULT_CELL_WEIGHT);
        VariationLookup lookup = VariationLookup.of(Map.of(BASIC, basic, EMPTY, empty), Map.of(LIST, new IslandVariationList(List.of(
                IslandVariationList.Entry.variation(EMPTY, 1),
                IslandVariationList.Entry.variation(BASIC, 9)))));
        return new IslandFunction(NOISE, 256, cellHeight, 0, 0.8, LIST, lookup);
    }

    private static double value(IslandFunction islands, double x, double y, double z) {
        return islands.compute(new DensityFunction.SinglePointContext((int) x, (int) y, (int) z));
    }

    /** Random block positions around the row 0. */
    private static int[][] points() {
        Random random = new Random(11);
        int[][] points = new int[POINTS][];
        for (int i = 0; i < POINTS; i++) {
            points[i] = new int[] {random.nextInt(-2048, 2048), random.nextInt(-160, 300), random.nextInt(-2048, 2048)};
        }
        return points;
    }

    /** Checks the node against the value that the lattice gives for the cell of each point with an island. */
    private static void assertNode(DensityFunction node, ToDoubleFunction<Sample> expected) {
        IslandFunction islands = islands(node, new UnitRange(0.2, 0.8), 160, 0);
        int checked = 0;
        for (int[] point : points()) {
            VoronoiLattice lattice = islands.lattice();
            VoronoiLattice.Cell cell = lattice.nearest(point[0], point[1], point[2]);
            double actual = value(islands, point[0], point[1], point[2]);
            if (!islands.hasIsland(cell)) {
                assertEquals(islands.lowest(), actual, "A cell without an island did not give the lowest value");
                continue;
            }
            checked++;
            assertEquals(expected.applyAsDouble(new Sample(islands, lattice, cell, point)), actual, TOLERANCE, () -> node + " differs from the lattice");
        }
        assertTrue(checked > POINTS / 10, "Too few points in islands");
    }

    /** One point in a cell with an island. */
    private record Sample(IslandFunction islands, VoronoiLattice lattice, VoronoiLattice.Cell cell, int[] point) {

        double x() {
            return this.point[0];
        }

        double y() {
            return this.point[1];
        }

        double z() {
            return this.point[2];
        }

        int index() {
            return this.islands.picks().pick(this.islands.rolls(), this.cell.ix(), this.cell.iz());
        }
    }

    @Test
    void cellGaugeIsTheLatticeGauge() {
        assertNode(IslandValue.Kind.CELL_GAUGE.function(), sample -> sample.lattice.evaluate(sample.cell, sample.x(), sample.y(), sample.z()).value());
    }

    @Test
    void wallDistanceIsTheLatticeWallDistance() {
        assertNode(IslandValue.Kind.WALL_DISTANCE.function(), sample -> sample.lattice.values(sample.cell, sample.x(), sample.y(), sample.z()).wallDistance());
    }

    @Test
    void cellRadiusIsTheLatticeCellRadius() {
        assertNode(IslandValue.Kind.CELL_RADIUS.function(), sample -> sample.lattice.cellRadius(sample.cell));
    }

    @Test
    void cellWeightIsTheLatticeCellWeight() {
        assertNode(IslandValue.Kind.CELL_WEIGHT.function(), sample -> sample.lattice.cellWeight(sample.cell));
    }

    @Test
    void cellFillIsTheRolledFill() {
        assertNode(IslandValue.Kind.CELL_FILL.function(), sample -> sample.islands.picks().fill(sample.islands.rolls(), sample.index(), sample.cell.ix(), sample.cell.iz()));
    }

    @Test
    void siteOffsetIsTheOffsetFromTheSite() {
        assertNode(new SiteOffset(Direction.Axis.X), sample -> sample.x() - sample.cell.siteX());
        assertNode(new SiteOffset(Direction.Axis.Y), sample -> sample.y() - sample.cell.siteY());
        assertNode(new SiteOffset(Direction.Axis.Z), sample -> sample.z() - sample.cell.siteZ());
    }

    @Test
    void siteDistanceIsTheDistanceFromTheSite() {
        assertNode(new SiteDistance(SiteDistance.Plane.XYZ), sample -> Math.sqrt(square(sample.x() - sample.cell.siteX())
                + square(sample.y() - sample.cell.siteY()) + square(sample.z() - sample.cell.siteZ())));
        assertNode(new SiteDistance(SiteDistance.Plane.XZ), sample -> Math.sqrt(square(sample.x() - sample.cell.siteX()) + square(sample.z() - sample.cell.siteZ())));
    }

    @Test
    void cellRandomIsOneValuePerCellAndChannel() {
        assertNode(new CellRandom(-2, 6, 3), sample -> -2 + 8 * sample.islands.rolls().random(3, sample.cell.ix(), sample.cell.iy(), sample.cell.iz()));
        IslandFunction first = islands(new CellRandom(0, 1, 0), IslandVariation.DEFAULT_CELL_WEIGHT, 256, 0);
        IslandFunction second = islands(new CellRandom(0, 1, 1), IslandVariation.DEFAULT_CELL_WEIGHT, 256, 0);
        int differences = 0;
        for (int ix = 0; ix < 30; ix++) {
            VoronoiLattice.Cell cell = first.lattice().cell(ix, 0, 0);
            if (first.hasIsland(cell)) {
                double a = value(first, cell.siteX(), cell.siteY(), cell.siteZ());
                double b = value(second, cell.siteX(), cell.siteY(), cell.siteZ());
                assertTrue(a >= 0 && a <= 1, "A cell random value left its bounds");
                if (a != b) {
                    differences++;
                }
            }
        }
        assertTrue(differences > 10, "Two channels gave the same values");
    }

    @Test
    void islandBodyIsTheFillMinusTheGaugeTimesTheCellRadius() {
        assertNode(IslandValue.Kind.ISLAND_BODY.function(), sample -> {
            double fill = sample.islands.picks().fill(sample.islands.rolls(), sample.index(), sample.cell.ix(), sample.cell.iz());
            return (fill - sample.lattice.evaluate(sample.cell, sample.x(), sample.y(), sample.z()).value()) * sample.lattice.cellRadius(sample.cell);
        });
    }

    @Test
    void islandBodyEqualsVoronoiIslandsAtDistortionZeroWeightHalfAndEqualCellSizes() {
        VoronoiIslands plain = new VoronoiIslands(NOISE, 256, 256, 0, 0.8, 0.3, 0.9, 0.0, 0.0, 64, 3);
        IslandVariation basic = IslandVariation.island(Holder.direct(IslandValue.Kind.ISLAND_BODY.function()), BIOMES, IslandVariation.DEFAULT_CELL_WEIGHT,
                new UnitRange(0.3, 0.9), 0, 64, 3);
        IslandFunction islands = new IslandFunction(NOISE, 256, 256, 0, 0.8, LIST,
                VariationLookup.of(Map.of(BASIC, basic), Map.of(LIST, new IslandVariationList(List.of(IslandVariationList.Entry.variation(BASIC, 1))))));
        int inside = 0;
        for (int[] point : points()) {
            double expected = plain.compute(point[0], point[1], point[2]);
            assertEquals(expected, value(islands, point[0], point[1], point[2]), () -> "The island body differs from VoronoiIslands at " + List.of(point[0], point[1], point[2]));
            if (expected > 0) {
                inside++;
            }
        }
        assertTrue(inside > 0, "No point lies inside an island");
    }

    @Test
    void distortionShiftsOnlyInsideTheCell() {
        IslandFunction flat = islands(IslandValue.Kind.ISLAND_BODY.function(), IslandVariation.DEFAULT_CELL_WEIGHT, 128, 0);
        IslandFunction distorted = islands(IslandValue.Kind.ISLAND_BODY.function(), IslandVariation.DEFAULT_CELL_WEIGHT, 128, 12);
        int differences = 0;
        for (int[] point : points()) {
            VoronoiLattice.Cell cell = flat.lattice().nearest(point[0], point[1], point[2]);
            assertEquals(cell, distorted.lattice().nearest(point[0], point[1], point[2]), "The distortion moved a wall");
            if (value(flat, point[0], point[1], point[2]) != value(distorted, point[0], point[1], point[2])) {
                differences++;
            }
        }
        assertTrue(differences > 0, "The distortion changed nothing");
    }

    @Test
    void emptyCellsAndOtherRowsGiveTheLowestValue() {
        IslandFunction islands = islands(IslandValue.Kind.ISLAND_BODY.function(), IslandVariation.DEFAULT_CELL_WEIGHT, 128, 0);
        int empty = 0;
        for (int ix = -10; ix <= 10; ix++) {
            for (int iz = -10; iz <= 10; iz++) {
                VoronoiLattice.Cell cell = islands.lattice().cell(ix, 0, iz);
                IslandVariation variation = islands.variation(cell);
                if (variation != null && variation.empty()) {
                    empty++;
                    assertEquals(islands.lowest(), value(islands, cell.siteX(), cell.siteY(), cell.siteZ()), "An Empty cell holds a block");
                }
                VoronoiLattice.Cell above = islands.lattice().cell(ix, 1, iz);
                assertEquals(islands.lowest(), value(islands, above.siteX(), above.siteY(), above.siteZ()), "A cell above the row 0 holds a block");
            }
        }
        assertTrue(empty > 0, "No Empty cell");
    }

    @Test
    void nodeOutsideAnIslandFunctionGivesZero() {
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(10, 20, 30);
        for (IslandValue.Kind kind : IslandValue.Kind.values()) {
            assertEquals(0, kind.function().compute(context), kind.path());
        }
        assertEquals(0, new SiteOffset(Direction.Axis.Y).compute(context));
        assertEquals(0, new SiteDistance(SiteDistance.Plane.XZ).compute(context));
        assertEquals(0, new CellRandom(2, 5, 0).compute(context));
    }

    @Test
    void codecWritesTheFieldsAndResolvesNothingWithoutRegistries() {
        JsonElement json = JsonParser.parseString("""
                {"type": "aerolite:island_variations", "noise": {"firstOctave": -7, "amplitudes": [1.0]}, "cell_size": 256, "cell_height": 128, "layer_min_y": 0, "jitter": 0.8,
                 "variations": "aerolite:overworld_islands"}
                """);
        DensityFunction function = DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(function instanceof IslandFunction);
        IslandFunction islands = (IslandFunction) function;
        assertEquals(json, DensityFunction.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, islands.withOverride(PreviewOverride.NONE.withFill(OptionalDouble.of(0.5)))).getOrThrow(),
                "The codec wrote the preview override");
        assertTrue(islands.picks().variations().isEmpty());
        assertEquals(islands.lowest(), value(islands, 100, 64, 100));
    }

    @Test
    void nearestRowCellHasTheLeastPowerRuleValue() {
        IslandFunction islands = islands(IslandValue.Kind.ISLAND_BODY.function(), new UnitRange(0.0, 1.0), 96, 0);
        VoronoiLattice lattice = islands.lattice();
        Random random = new Random(5);
        for (int i = 0; i < 400; i++) {
            double x = random.nextInt(-3000, 3000);
            double y = random.nextInt(-400, 700);
            double z = random.nextInt(-3000, 3000);
            VoronoiLattice.Cell found = islands.nearestRowCell(x, y, z);
            assertEquals(0, found.iy());
            double best = Double.POSITIVE_INFINITY;
            int bx = Math.floorDiv((int) x, 256);
            int bz = Math.floorDiv((int) z, 256);
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    best = Math.min(best, lattice.powerValue(lattice.cell(bx + dx, 0, bz + dz), x, y, z));
                }
            }
            assertEquals(best, lattice.powerValue(found, x, y, z), "The search missed the cell with the least power rule value");
        }
    }

    @Test
    void overrideSoloAndPinsChangeTheCells() {
        IslandFunction islands = islands(IslandValue.Kind.CELL_FILL.function(), IslandVariation.DEFAULT_CELL_WEIGHT, 128, 0);
        IslandFunction solo = islands.withOverride(PreviewOverride.NONE.withSolo(BASIC).withFill(OptionalDouble.of(0.45)));
        for (int ix = -6; ix <= 6; ix++) {
            VoronoiLattice.Cell cell = solo.lattice().cell(ix, 0, 0);
            assertTrue(solo.hasIsland(cell) || solo.lattice().lost(cell), "Solo left a cell without the island");
            if (solo.hasIsland(cell)) {
                assertEquals(0.45, value(solo, cell.siteX(), cell.siteY(), cell.siteZ()), "The pinned fill did not reach the node");
            }
        }
        assertNotEquals(PreviewOverride.NONE, solo.override());
    }

    @Test
    void cellFollowsThePowerRuleWithTheYScale() {
        IslandFunction islands = islands(IslandValue.Kind.ISLAND_BODY.function(), new UnitRange(0.1, 0.9), 64, 0);
        VoronoiLattice lattice = islands.lattice();
        double yScale = 256.0 / 64;
        Random random = new Random(21);
        for (int i = 0; i < 500; i++) {
            int x = random.nextInt(-2000, 2000);
            int y = random.nextInt(-100, 200);
            int z = random.nextInt(-2000, 2000);
            VoronoiLattice.Cell best = null;
            double bestValue = Double.POSITIVE_INFINITY;
            int bx = Math.floorDiv(x, 256);
            int by = Math.floorDiv(y, 64);
            int bz = Math.floorDiv(z, 256);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        VoronoiLattice.Cell cell = lattice.cell(bx + dx, by + dy, bz + dz);
                        double value = square(x - cell.siteX()) + square((y - cell.siteY()) * yScale) + square(z - cell.siteZ())
                                - lattice.cellWeight(cell) * 256 * 256;
                        if (value < bestValue) {
                            bestValue = value;
                            best = cell;
                        }
                    }
                }
            }
            assertEquals(best, lattice.nearest(x, y, z), "The cell of the island function does not follow the power rule with the Y scale");
        }
    }

    @Test
    void carrierComputesItsArgument() {
        IslandFunction islands = islands(IslandValue.Kind.ISLAND_BODY.function(), IslandVariation.DEFAULT_CELL_WEIGHT, 128, 0);
        DensityFunction argument = DensityFunctions.add(DensityFunctions.yClampedGradient(-64, 320, -1, 1), DensityFunctions.constant(0.25));
        IslandCarrier carrier = new IslandCarrier(islands, argument);
        assertEquals(argument.minValue(), carrier.minValue());
        assertEquals(argument.maxValue(), carrier.maxValue());
        for (int y = -64; y < 320; y += 7) {
            DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(300, y, -40);
            assertEquals(argument.compute(context), carrier.compute(context));
        }
        IslandCarrier mapped = (IslandCarrier) carrier.mapAll(function -> function);
        assertTrue(mapped.islands() instanceof IslandFunction, "The mapped carrier lost its island function");
    }

    @Test
    void boundsDuringTheRegistryLoadAddNoHolderOfAMissingList() {
        Map<ResourceKey<?>, MappedRegistry<?>> registries = new HashMap<>();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                MappedRegistry registry = registries.computeIfAbsent(key, missing -> new MappedRegistry((ResourceKey) missing, Lifecycle.stable()));
                return Optional.of(new RegistryOps.RegistryInfo<>(registry.asLookup(), registry.createRegistrationLookup(), Lifecycle.stable()));
            }
        });
        // The vanilla max function reads the bounds of its arguments when the codec builds it.
        JsonElement json = JsonParser.parseString("""
                {"type": "minecraft:max", "argument2": 0.5, "argument1": {"type": "aerolite:island_variations", "noise": {"firstOctave": -7, "amplitudes": [1.0]},
                 "cell_size": 256, "cell_height": 128, "layer_min_y": 0, "jitter": 0.8, "variations": "aerolite:missing_islands"}}
                """);
        DensityFunction function = DensityFunction.DIRECT_CODEC.parse(ops, json).getOrThrow();
        assertEquals(Double.POSITIVE_INFINITY, function.maxValue());
        registries.values().forEach(MappedRegistry::freeze);
    }

    private static double square(double value) {
        return value * value;
    }

}
