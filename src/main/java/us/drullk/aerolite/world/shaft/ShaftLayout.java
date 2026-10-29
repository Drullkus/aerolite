package us.drullk.aerolite.world.shaft;

import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The placement of shafts, columns and junctions in one dimension. Every query is a pure function of the seed.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
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
     */
    public record Column(Shaft xShaft, Shaft zShaft, int xShaftSign, int zShaftSign, List<ColumnJunction> junctions, int bottomShear, int topShear) {

        public int x() {
            return this.zShaft.position();
        }

        public int z() {
            return this.xShaft.position();
        }
    }

    private final AeroliteSettings settings;
    private final int minY;
    private final int maxY;
    private final int[] slotsX;
    private final int[] slotsZ;
    private final MixedRandomFactory shaftRandom;
    private final MixedRandomFactory columnRandom;
    private final MixedRandomFactory shaftMazeRandom;
    private final MixedRandomFactory columnMazeRandom;
    private final FractalNoise decayNoise;
    private final FractalNoise shearNoise;

    /**
     * @param salt  a name that makes the layout of each dimension differ
     * @param maxY  the top build limit, exclusive
     */
    public ShaftLayout(long seed, String salt, int minY, int maxY, AeroliteSettings settings) {
        this.settings = settings;
        this.minY = minY;
        this.maxY = maxY;
        MixedRandomFactory root = new MixedRandomFactory(seed).fork("aerolite:shafts/" + salt);
        this.shaftRandom = root.fork("shaft");
        this.columnRandom = root.fork("column");
        this.shaftMazeRandom = root.fork("shaft_maze");
        this.columnMazeRandom = root.fork("column_maze");
        this.decayNoise = new FractalNoise(root.fromHashOf("decay"), DECAY_OCTAVES, settings.decayScale());
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

    public AeroliteSettings settings() {
        return this.settings;
    }

    public FractalNoise decayNoise() {
        return this.decayNoise;
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
        int spacing = this.settings.meanShaftSpacing();
        int margin = (this.settings.minShaftSpacing() + 1) / 2;
        RandomSource random = this.shaftRandom.at(axis.ordinal(), cell, 0);
        int position = cell * spacing + margin + random.nextInt(Math.max(1, spacing - 2 * margin + 1));
        int slot = slots[random.nextInt(slots.length)];
        int offset = 1 + random.nextInt(2);
        return Optional.of(new Shaft(axis, cell, position, slot, this.minY + SLOT * slot + offset + RADIUS));
    }

    /** The shafts of the axis whose centers lie in a range of the cross axis. */
    public List<Shaft> shafts(Axis axis, int min, int max) {
        List<Shaft> found = new ArrayList<>();
        int spacing = this.settings.meanShaftSpacing();
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
        if (random.nextFloat() >= this.settings.columnChance()) {
            return Optional.empty();
        }
        int xShaftSign = random.nextBoolean() ? 1 : -1;
        int zShaftSign = random.nextBoolean() ? 1 : -1;
        List<ColumnJunction> junctions = new ArrayList<>(List.of(
                new ColumnJunction(xShaft.get().centerY(), -xShaftSign, true),
                new ColumnJunction(zShaft.get().centerY(), -zShaftSign, false)
        ));
        junctions.sort(Comparator.comparingInt(ColumnJunction::center));
        int lowestBox = junctions.getFirst().center() - Ring.BOX;
        int highestBox = junctions.getLast().center() + Ring.BOX;
        int bottomShear = Math.min(MIN_SHEAR + random.nextInt(MAX_SHEAR - MIN_SHEAR + 1), Math.max(0, lowestBox - this.minY - SHEAR_MARGIN));
        int topShear = Math.min(MIN_SHEAR + random.nextInt(MAX_SHEAR - MIN_SHEAR + 1), Math.max(0, this.maxY - 1 - highestBox - SHEAR_MARGIN));
        return Optional.of(new Column(xShaft.get(), zShaft.get(), xShaftSign, zShaftSign, List.copyOf(junctions), bottomShear, topShear));
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

    /** The maze of one horizontal shaft. */
    public ShaftMaze shaftMaze(Shaft shaft) {
        ShaftMaze.BoxLookup boxes = (min, max) -> this.columnsOn(shaft, min, max).stream()
                .map(column -> shaft.axis() == Axis.X
                        ? new ShaftMaze.Box(column.x(), column.xShaftSign())
                        : new ShaftMaze.Box(column.z(), column.zShaftSign()))
                .toList();
        return new ShaftMaze(boxes, this.shaftMazeRandom.fork(shaft.axis().ordinal(), shaft.cell(), 0));
    }

    /** The maze of one column. Its chunks are the chunk sections from the bottom build limit to the top build limit. */
    public ShaftMaze columnMaze(Column column) {
        List<ShaftMaze.Box> boxes = column.junctions().stream().map(ColumnJunction::box).toList();
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return new ShaftMaze(
                lookup,
                this.columnMazeRandom.fork(column.zShaft().cell(), column.xShaft().cell(), 0),
                Math.floorDiv(this.minY, ShaftMaze.CHUNK),
                Math.floorDiv(this.maxY - 1, ShaftMaze.CHUNK)
        );
    }

}
