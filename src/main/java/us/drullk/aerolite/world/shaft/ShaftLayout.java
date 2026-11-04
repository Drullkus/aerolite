package us.drullk.aerolite.world.shaft;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.WeightedVariation;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The placement of shafts, columns and junctions in one dimension. Every query is a pure function of the seed.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ShaftLayout {

    public static final int SLOT = 16;
    /** The half width of a shaft. */
    public static final int RADIUS = 6;
    public static final int MIN_SHEAR = 8;
    public static final int MAX_SHEAR = 64;
    /** The full column that stays between a shear band and the outermost junction box. */
    public static final int SHEAR_MARGIN = 8;
    static final int DECAY_OCTAVES = 4;
    static final int SHEAR_OCTAVES = 3;
    static final double SHEAR_SCALE = 16;

    /** The direction that a horizontal shaft runs. */
    public enum Axis {
        X,
        Z
    }

    /**
     * One horizontal shaft.
     *
     * @param cell     the cell index of the shaft on the cross axis
     * @param position the cross axis coordinate of the shaft center
     * @param slot     the index of the slot that holds the shaft, counted from the bottom build limit
     */
    public record Shaft(Axis axis, int cell, int position, int slot, int centerY) {
    }

    /**
     * One column at a crossing of an X shaft and a Z shaft in plan view.
     *
     * @param xShaftSign the face sign of the shaft patch on the junction with the X shaft
     * @param zShaftSign the face sign of the shaft patch on the junction with the Z shaft
     * @param junctions  the two junctions in ascending sequence of Y
     * @param bottomEnd  the lowest Y of the column
     * @param topEnd     the Y above the highest block of the column
     * @param bottomShear the height of the shear band above the bottom end
     * @param topShear   the height of the shear band below the top end
     */
    public record Column(Shaft xShaft, Shaft zShaft, int xShaftSign, int zShaftSign, List<ColumnJunction> junctions,
                         int bottomEnd, int topEnd, int bottomShear, int topShear) {

        public boolean spans(int y) {
            return y >= this.bottomEnd && y < this.topEnd;
        }

        public int x() {
            return this.zShaft.position();
        }

        public int z() {
            return this.xShaft.position();
        }
    }

    private final ShaftMazes mazes;
    private final ShaftPlacement placement;
    private final List<WeightedVariation> variations;
    private final int totalWeight;
    private final int minY;
    private final int maxY;
    private final int[] slotsX;
    private final int[] slotsZ;
    private final MixedRandomFactory shaftRandom;
    private final MixedRandomFactory columnRandom;
    private final MixedRandomFactory shaftMazeRandom;
    private final MixedRandomFactory columnMazeRandom;
    private final MixedRandomFactory shaftVariationRandom;
    private final MixedRandomFactory columnVariationRandom;
    private final MixedRandomFactory root;
    /** The randoms of the block state providers, one per block position. */
    private final MixedRandomFactory blockRandom;
    /** One decay noise per decay scale. Every noise starts from the same seed hash. */
    private final Map<Integer, FractalNoise> decayNoises = new ConcurrentHashMap<>();
    private final FractalNoise shearNoise;

    /**
     * @param dimension the dimension; its name makes the layout of each dimension differ
     * @param maxY      the top build limit, exclusive
     */
    public ShaftLayout(ShaftMazes mazes, long seed, ResourceKey<Level> dimension, int minY, int maxY, DimensionSettings settings) {
        this.mazes = mazes;
        this.placement = settings.placement();
        this.variations = settings.variations();
        this.totalWeight = this.variations.stream().mapToInt(WeightedVariation::weight).sum();
        this.minY = minY;
        this.maxY = maxY;
        MixedRandomFactory root = new MixedRandomFactory(seed).fork("aerolite:shafts/" + dimension.location());
        this.root = root;
        this.shaftRandom = root.fork("shaft");
        this.columnRandom = root.fork("column");
        this.shaftMazeRandom = root.fork("shaft_maze");
        this.columnMazeRandom = root.fork("column_maze");
        this.shaftVariationRandom = root.fork("shaft_variation");
        this.columnVariationRandom = root.fork("column_variation");
        this.blockRandom = root.fork("block");
        this.shearNoise = new FractalNoise(root.fromHashOf("shear"), SHEAR_OCTAVES, SHEAR_SCALE);

        // The bottom slot and the top slot hold no shaft.
        int slotCount = Math.max(0, (maxY - minY) / SLOT - 2);
        int[] slots = new int[slotCount];
        for (int k = 0; k < slotCount; k++) {
            slots[k] = k + 1;
        }
        RandomSource shuffle = root.fromHashOf("slots");
        for (int k = slotCount - 1; k > 0; k--) {
            int other = shuffle.nextInt(k + 1);
            int swap = slots[k];
            slots[k] = slots[other];
            slots[other] = swap;
        }
        this.slotsX = Arrays.stream(slots, 0, slotCount / 2).sorted().toArray();
        this.slotsZ = Arrays.stream(slots, slotCount / 2, slotCount).sorted().toArray();
    }

    public ShaftPlacement placement() {
        return this.placement;
    }

    /** True when the dimension has at least one variation with a positive weight. */
    public boolean hasShafts() {
        return this.totalWeight > 0;
    }

    /** The variation of one horizontal shaft. */
    public ShaftVariation variation(Shaft shaft) {
        return this.pick(this.shaftVariationRandom.at(shaft.axis().ordinal(), shaft.cell(), 0));
    }

    /** The variation of one column. */
    public ShaftVariation variation(Column column) {
        return this.pick(this.columnVariationRandom.at(column.zShaft().cell(), column.xShaft().cell(), 0));
    }

    /** Picks a variation in proportion to its weight. A weight of 0 never picks. */
    private ShaftVariation pick(RandomSource random) {
        if (this.totalWeight <= 0) {
            throw new IllegalStateException("The dimension has no shaft variation with a positive weight");
        }
        int value = random.nextInt(this.totalWeight);
        for (WeightedVariation variation : this.variations) {
            value -= variation.weight();
            if (value < 0) {
                return variation.variation();
            }
        }
        throw new IllegalStateException("The weights do not sum to the total weight");
    }

    /** The seed of the provider random at a block. Two chunks that paint one block use the same seed. */
    public long blockSeed(BlockPos pos) {
        return this.blockRandom.seedAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The decay noise at the decay scale of a variation. */
    public FractalNoise decayNoise(ShaftVariation variation) {
        return this.decayNoises.computeIfAbsent(variation.decayScale(),
                scale -> new FractalNoise(this.root.fromHashOf("decay"), DECAY_OCTAVES, scale));
    }

    public FractalNoise shearNoise() {
        return this.shearNoise;
    }

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }

    /** The slots of the axis, in ascending sequence. */
    public int[] slots(Axis axis) {
        return (axis == Axis.X ? this.slotsX : this.slotsZ).clone();
    }

    /** The shaft in one cell of the cross axis, or empty when the axis has no slot. */
    public Optional<Shaft> shaft(Axis axis, int cell) {
        int[] slots = axis == Axis.X ? this.slotsX : this.slotsZ;
        if (slots.length == 0) {
            return Optional.empty();
        }
        // A margin of half the minimum spacing keeps every neighbor distance between the minimum and the maximum.
        int spacing = this.placement.meanShaftSpacing();
        int margin = (this.placement.minShaftSpacing() + 1) / 2;
        RandomSource random = this.shaftRandom.at(axis.ordinal(), cell, 0);
        int position = cell * spacing + margin + random.nextInt(Math.max(1, spacing - 2 * margin + 1));
        int slot = slots[random.nextInt(slots.length)];
        int offset = 1 + random.nextInt(2);
        return Optional.of(new Shaft(axis, cell, position, slot, this.minY + SLOT * slot + offset + RADIUS));
    }

    /** The shafts of the axis whose centers lie in a range of the cross axis. */
    public List<Shaft> shafts(Axis axis, int min, int max) {
        List<Shaft> found = new ArrayList<>();
        int spacing = this.placement.meanShaftSpacing();
        for (int cell = Math.floorDiv(min, spacing); cell <= Math.floorDiv(max, spacing); cell++) {
            this.shaft(axis, cell).filter(shaft -> shaft.position() >= min && shaft.position() <= max).ifPresent(found::add);
        }
        return found;
    }

    /** The column where the Z shaft of cell {@code xCell} crosses the X shaft of cell {@code zCell}, if one stands there. */
    public Optional<Column> column(int xCell, int zCell) {
        Optional<Shaft> zShaft = this.shaft(Axis.Z, xCell);
        Optional<Shaft> xShaft = this.shaft(Axis.X, zCell);
        if (zShaft.isEmpty() || xShaft.isEmpty()) {
            return Optional.empty();
        }
        // One empty slot between the two shafts keeps the junction boxes apart.
        if (Math.abs(xShaft.get().slot() - zShaft.get().slot()) < 2) {
            return Optional.empty();
        }
        RandomSource random = this.columnRandom.at(xCell, zCell, 0);
        if (random.nextFloat() >= this.placement.columnChance()) {
            return Optional.empty();
        }
        int xShaftSign = random.nextBoolean() ? 1 : -1;
        int zShaftSign = random.nextBoolean() ? 1 : -1;
        List<ColumnJunction> junctions = new ArrayList<>(List.of(
                new ColumnJunction(xShaft.get().centerY(), -xShaftSign, true),
                new ColumnJunction(zShaft.get().centerY(), -zShaftSign, false)
        ));
        junctions.sort(Comparator.comparingInt(ColumnJunction::center));
        // Each end leaves room for the shortest shear band and the margin to the box.
        int lowestBox = junctions.getFirst().center() - Ring.BOX;
        int highestBox = junctions.getLast().center() + Ring.BOX;
        int bottomEnd = this.between(random, this.minY, Math.max(this.minY, lowestBox - SHEAR_MARGIN - MIN_SHEAR));
        int topEnd = this.between(random, Math.min(this.maxY, highestBox + 1 + SHEAR_MARGIN + MIN_SHEAR), this.maxY);
        int bottomShear = Math.min(this.between(random, MIN_SHEAR, MAX_SHEAR), Math.max(0, lowestBox - SHEAR_MARGIN - bottomEnd));
        int topShear = Math.min(this.between(random, MIN_SHEAR, MAX_SHEAR), Math.max(0, topEnd - (highestBox + 1) - SHEAR_MARGIN));
        return Optional.of(new Column(xShaft.get(), zShaft.get(), xShaftSign, zShaftSign, List.copyOf(junctions), bottomEnd, topEnd, bottomShear, topShear));
    }

    private int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** The columns on one horizontal shaft whose centers lie in a range along the shaft axis. */
    public List<Column> columnsOn(Shaft shaft, int min, int max) {
        Axis crossAxis = shaft.axis() == Axis.X ? Axis.Z : Axis.X;
        List<Column> found = new ArrayList<>();
        for (Shaft crossing : this.shafts(crossAxis, min, max)) {
            Optional<Column> column = shaft.axis() == Axis.X
                    ? this.column(crossing.cell(), shaft.cell())
                    : this.column(shaft.cell(), crossing.cell());
            column.ifPresent(found::add);
        }
        return found;
    }

    /** The junction boxes of one horizontal shaft whose centers lie in a range along the shaft axis. */
    private ShaftMaze.BoxLookup shaftBoxes(Shaft shaft) {
        return (min, max) -> this.columnsOn(shaft, min, max).stream()
                .map(column -> shaft.axis() == Axis.X
                        ? new ShaftMaze.Box(column.x(), column.xShaftSign())
                        : new ShaftMaze.Box(column.z(), column.zShaftSign()))
                .toList();
    }

    /** The junction boxes of one horizontal shaft that cover at least one block of a range along the shaft axis. */
    public List<ShaftMaze.Box> boxesNear(Shaft shaft, int min, int max) {
        return this.shaftBoxes(shaft).boxes(min - Ring.BOX, max + Ring.BOX);
    }

    /** The ring description of the shape of one horizontal shaft. */
    public RingShape ring(Shaft shaft) {
        return this.mazes.ring(this.variation(shaft).shape());
    }

    /** The ring description of the shape of one column. */
    public RingShape ring(Column column) {
        return this.mazes.ring(this.variation(column).shape());
    }

    /** The surface pattern of one horizontal shaft, from the pattern and the shape of its variation. */
    public SurfacePattern shaftMaze(Shaft shaft) {
        return this.mazes.pattern(this.variation(shaft), this.shaftBoxes(shaft), this.shaftMazeRandom.fork(shaft.axis().ordinal(), shaft.cell(), 0),
                Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1);
    }

    /** The surface pattern of one column. Its chunks are the chunk sections from the bottom build limit to the top build limit. */
    public SurfacePattern columnMaze(Column column) {
        List<ShaftMaze.Box> boxes = column.junctions().stream().map(ColumnJunction::box).toList();
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return this.mazes.pattern(
                this.variation(column),
                lookup,
                this.columnMazeRandom.fork(column.zShaft().cell(), column.xShaft().cell(), 0),
                Math.floorDiv(this.minY, ShaftMaze.CHUNK),
                Math.floorDiv(this.maxY - 1, ShaftMaze.CHUNK)
        );
    }

}
