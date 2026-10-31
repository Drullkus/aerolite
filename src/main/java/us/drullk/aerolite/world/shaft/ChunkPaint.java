package us.drullk.aerolite.world.shaft;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The paint of the shafts, columns and junctions of one chunk.
 * Volumes come first, then maze paths, then decay, then erosion, then shear.
 * Each shaft and column paints with its own variation. A junction box paints with the variation of its horizontal shaft.
 * Decay and erosion read the thresholds and the decay noise of the variation of each block.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
final class ChunkPaint {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** A patch block: its outward face direction and the variation of its junction box. */
    private record PatchBlock(Direction normal, ShaftVariation variation) {
    }

    /** A maze path block and the variation that gives its maze path block. */
    private record PaintedCarve(MazeCarver.Carve carve, ShaftVariation variation) {
    }

    private final MazeCarver carver;
    private final ShaftFrames frames;
    private final ErosionResistance resistance;
    private final ShaftLayout layout;
    private final ChunkAccess chunk;
    private final int minX;
    private final int minZ;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    /** The maze grid blocks of shafts and columns outside the junction boxes, with the variation of each block. */
    private final Long2ObjectMap<ShaftVariation> shaftGrid = new Long2ObjectOpenHashMap<>();
    /** The maze grid blocks of the patches. */
    private final Long2ObjectMap<PatchBlock> patchGrid = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PaintedCarve> carves = new Long2ObjectOpenHashMap<>();

    ChunkPaint(MazeCarver carver, ShaftFrames frames, ErosionResistance resistance, ShaftLayout layout, ChunkAccess chunk) {
        this.carver = carver;
        this.frames = frames;
        this.resistance = resistance;
        this.layout = layout;
        this.chunk = chunk;
        ChunkPos pos = chunk.getPos();
        this.minX = pos.getMinBlockX();
        this.minZ = pos.getMinBlockZ();
    }

    void paint() {
        int reach = Ring.BOX;
        List<ShaftLayout.Shaft> shafts = new ArrayList<>();
        shafts.addAll(this.layout.shafts(ShaftLayout.Axis.Z, this.minX - reach, this.minX + 15 + reach));
        shafts.addAll(this.layout.shafts(ShaftLayout.Axis.X, this.minZ - reach, this.minZ + 15 + reach));
        List<ShaftLayout.Column> columns = new ArrayList<>();
        for (ShaftLayout.Shaft zShaft : shafts) {
            if (zShaft.axis() != ShaftLayout.Axis.Z) {
                continue;
            }
            for (ShaftLayout.Shaft xShaft : shafts) {
                if (xShaft.axis() == ShaftLayout.Axis.X) {
                    this.layout.column(zShaft.cell(), xShaft.cell()).ifPresent(columns::add);
                }
            }
        }

        for (ShaftLayout.Shaft shaft : shafts) {
            this.fillShaft(shaft);
        }
        for (ShaftLayout.Column column : columns) {
            this.fillColumn(column);
        }
        for (ShaftLayout.Column column : columns) {
            this.fillBoxes(column);
        }

        for (ShaftLayout.Shaft shaft : shafts) {
            this.carveShaft(shaft);
        }
        for (ShaftLayout.Column column : columns) {
            this.carveColumn(column);
        }

        this.decay();
        this.erode(shafts, columns);
        for (ShaftLayout.Column column : columns) {
            this.shear(column);
        }
    }

    private boolean inChunk(BlockPos pos) {
        return pos.getX() >= this.minX && pos.getX() < this.minX + 16
                && pos.getZ() >= this.minZ && pos.getZ() < this.minZ + 16
                && pos.getY() >= this.layout.minY() && pos.getY() < this.layout.maxY();
    }

    private void set(BlockPos pos, BlockState state) {
        if (this.inChunk(pos)) {
            this.chunk.setBlockState(pos, state, false);
        }
    }

    private int alongMin(ShaftLayout.Axis axis) {
        return axis == ShaftLayout.Axis.X ? this.minX : this.minZ;
    }

    private void fillShaft(ShaftLayout.Shaft shaft) {
        ShaftFrame frame = this.frames.of(shaft);
        ShaftVariation variation = this.layout.variation(shaft);
        int alongMin = this.alongMin(shaft.axis());
        List<ShaftMaze.Box> boxes = this.layout.shaftMaze(shaft).boxesNear(alongMin, alongMin + 15);
        for (int along = alongMin; along < alongMin + 16; along++) {
            int at = along;
            boolean inBox = boxes.stream().anyMatch(box -> box.covers(at));
            this.fillSection(frame, along, !inBox, variation);
        }
    }

    private void fillColumn(ShaftLayout.Column column) {
        ShaftFrame frame = this.frames.of(column);
        ShaftVariation variation = this.layout.variation(column);
        for (int y = column.bottomEnd(); y < column.topEnd(); y++) {
            this.fillSection(frame, y, !this.inBox(column, y), variation);
        }
    }

    private boolean inBox(ShaftLayout.Column column, int y) {
        return this.junctionAt(column, y) != null;
    }

    /** The junction whose box spans the Y, or null. */
    @Nullable
    private ColumnJunction junctionAt(ShaftLayout.Column column, int y) {
        for (ColumnJunction junction : column.junctions()) {
            if (Math.abs(y - junction.center()) <= Ring.BOX) {
                return junction;
            }
        }
        return null;
    }

    /** The variation of a junction box, which is the variation of its horizontal shaft. */
    private ShaftVariation boxVariation(ShaftLayout.Column column, ColumnJunction junction) {
        return this.layout.variation(junction.alongX() ? column.xShaft() : column.zShaft());
    }

    /** Fills one 13x13 cross section. The outer layer joins the maze grid when {@code grid} is true. */
    private void fillSection(ShaftFrame frame, int along, boolean grid, ShaftVariation variation) {
        for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
            for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                BlockPos pos = frame.at(along, first, second);
                if (!this.inChunk(pos)) {
                    continue;
                }
                boolean outer = Math.max(Math.abs(first), Math.abs(second)) == Ring.OUTER;
                this.chunk.setBlockState(pos, outer ? variation.wall() : variation.interior(), false);
                if (outer && grid) {
                    this.shaftGrid.put(pos.asLong(), variation);
                }
            }
        }
    }

    private void fillBoxes(ShaftLayout.Column column) {
        for (ColumnJunction junction : column.junctions()) {
            ShaftVariation variation = this.boxVariation(column, junction);
            for (int dx = -Ring.BOX; dx <= Ring.BOX; dx++) {
                for (int dy = -Ring.BOX; dy <= Ring.BOX; dy++) {
                    for (int dz = -Ring.BOX; dz <= Ring.BOX; dz++) {
                        this.set(this.cursor.set(column.x() + dx, junction.center() + dy, column.z() + dz), variation.interior());
                    }
                }
            }
            MazeCarver.PatchFace columnFace = this.carver.columnPatchFace(column, junction.box());
            this.addPatchGrid(columnFace, variation);
            this.addPatchGrid(new MazeCarver.PatchFace(columnFace.frame(), -columnFace.sign(), columnFace.center()), variation);
        }
    }

    /** Adds the 13x13 inside of a patch face to the maze grid. The rim stays wall. */
    private void addPatchGrid(MazeCarver.PatchFace face, ShaftVariation variation) {
        for (int along = -Ring.OUTER; along <= Ring.OUTER; along++) {
            for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
                MazeCarver.Carve carve = face.carve(face.center() + along, first);
                if (this.inChunk(carve.surface())) {
                    this.patchGrid.put(carve.surface().asLong(), new PatchBlock(carve.normal(), variation));
                }
            }
        }
    }

    /** Carves the segments of a horizontal shaft. Its patches belong to its own junction boxes, so they share its variation. */
    private void carveShaft(ShaftLayout.Shaft shaft) {
        ShaftFrame frame = this.frames.of(shaft);
        ShaftMaze maze = this.layout.shaftMaze(shaft);
        ShaftVariation variation = this.layout.variation(shaft);
        int chunkAlong = Math.floorDiv(this.alongMin(shaft.axis()), ShaftMaze.CHUNK);
        ShaftMaze.Segment segment = maze.segmentAt(chunkAlong);
        this.carver.shaftSegment(frame, segment, carve -> this.carve(carve, variation));
        if (segment.firstChunk() == chunkAlong) {
            this.carver.shaftSegment(frame, maze.segmentAt(chunkAlong - 1), carve -> this.carve(carve, variation));
        }
    }

    /** Carves every segment of the column, since the column spans the build height of the chunk. */
    private void carveColumn(ShaftLayout.Column column) {
        ShaftMaze maze = this.layout.columnMaze(column);
        ShaftVariation variation = this.layout.variation(column);
        int chunk = maze.minChunk();
        while (chunk <= maze.maxChunk()) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            this.carver.columnSegment(column, segment, carve -> {
                int y = carve.surface().getY();
                if (column.spans(y)) {
                    ColumnJunction junction = carve.normal() == null ? null : this.junctionAt(column, y);
                    this.carve(carve, junction == null ? variation : this.boxVariation(column, junction));
                }
            });
            chunk = segment.lastChunk() + 1;
        }
    }

    private void carve(MazeCarver.Carve carve, ShaftVariation variation) {
        if (!this.inChunk(carve.surface())) {
            return;
        }
        this.chunk.setBlockState(carve.surface(), AIR, false);
        this.set(carve.beneath(), variation.path());
        long key = carve.surface().asLong();
        this.carves.put(key, new PaintedCarve(carve, variation));
        if (carve.normal() != null) {
            this.patchGrid.put(key, new PatchBlock(carve.normal(), variation));
        }
    }

    /**
     * Far negative decay noise keeps only maze walls. Far positive decay noise keeps only maze paths.
     * On a patch, the box block that decay exposes becomes the maze path block.
     */
    private void decay() {
        LongSet grid = new LongOpenHashSet(this.shaftGrid.keySet());
        grid.addAll(this.patchGrid.keySet());
        for (long key : grid) {
            BlockPos pos = BlockPos.of(key);
            PatchBlock patch = this.patchGrid.get(key);
            ShaftVariation variation = patch != null ? patch.variation() : this.shaftGrid.get(key);
            double threshold = variation.decayThreshold();
            double value = this.layout.decayNoise(variation).sample(pos.getX(), pos.getY(), pos.getZ());
            if (value >= -threshold && value <= threshold) {
                continue;
            }
            PaintedCarve painted = this.carves.get(key);
            if (painted != null && value < -threshold) {
                this.set(painted.carve().beneath(), AIR);
                if (patch != null) {
                    this.set(painted.carve().beneath().relative(patch.normal().getOpposite()), painted.variation().path());
                }
            } else if (painted == null && value > threshold) {
                this.set(pos, AIR);
                if (patch != null) {
                    this.set(pos.relative(patch.normal().getOpposite()), patch.variation().path());
                }
            }
        }
    }

    /**
     * Decay noise beyond the erosion threshold removes blocks from the surface toward the 3x3 core.
     * The noise, normalized from the erosion threshold to 1, sets how deep the erosion reaches.
     */
    private void erode(List<ShaftLayout.Shaft> shafts, List<ShaftLayout.Column> columns) {
        for (ShaftLayout.Shaft shaft : shafts) {
            ShaftFrame frame = this.frames.of(shaft);
            ShaftVariation variation = this.layout.variation(shaft);
            int alongMin = this.alongMin(shaft.axis());
            List<ShaftMaze.Box> boxes = this.layout.shaftMaze(shaft).boxesNear(alongMin, alongMin + 15);
            for (int along = alongMin; along < alongMin + 16; along++) {
                int at = along;
                if (boxes.stream().noneMatch(box -> box.covers(at))) {
                    this.erodeSection(frame, along, variation);
                }
            }
        }
        for (ShaftLayout.Column column : columns) {
            ShaftFrame frame = this.frames.of(column);
            ShaftVariation variation = this.layout.variation(column);
            for (int y = column.bottomEnd(); y < column.topEnd(); y++) {
                if (!this.inBox(column, y)) {
                    this.erodeSection(frame, y, variation);
                }
            }
            for (ColumnJunction junction : column.junctions()) {
                this.erodeBox(column, junction);
            }
        }
    }

    /** The share of the erodible layers that erosion removes at a block, from 0 to 1. */
    private double erosionReach(BlockPos pos, FractalNoise noise, double threshold) {
        double value = Math.abs(noise.sample(pos.getX(), pos.getY(), pos.getZ()));
        return value <= threshold ? 0 : (value - threshold) / (1 - threshold);
    }

    private void erodeSection(ShaftFrame frame, int along, ShaftVariation variation) {
        FractalNoise noise = this.layout.decayNoise(variation);
        for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
            for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                BlockPos pos = frame.at(along, first, second);
                if (this.inChunk(pos) && this.erosionReach(pos, noise, variation.erosionThreshold()) > this.resistance.section(first, second)) {
                    this.chunk.setBlockState(pos, AIR, false);
                }
            }
        }
    }

    private void erodeBox(ShaftLayout.Column column, ColumnJunction junction) {
        ShaftVariation variation = this.boxVariation(column, junction);
        FractalNoise noise = this.layout.decayNoise(variation);
        for (int dx = -Ring.BOX; dx <= Ring.BOX; dx++) {
            for (int dy = -Ring.BOX; dy <= Ring.BOX; dy++) {
                for (int dz = -Ring.BOX; dz <= Ring.BOX; dz++) {
                    BlockPos pos = new BlockPos(column.x() + dx, junction.center() + dy, column.z() + dz);
                    if (this.inChunk(pos) && this.erosionReach(pos, noise, variation.erosionThreshold()) > this.resistance.box(junction, dx, dy, dz)) {
                        this.chunk.setBlockState(pos, AIR, false);
                    }
                }
            }
        }
    }

    /** Near each column end, a threshold that rises toward the end removes column blocks. */
    private void shear(ShaftLayout.Column column) {
        FractalNoise noise = this.layout.shearNoise();
        ShaftFrame frame = this.frames.of(column);
        for (int depth = 0; depth < column.bottomShear(); depth++) {
            this.shearSection(frame, column, column.bottomEnd() + depth, depth, column.bottomShear(), noise);
        }
        for (int depth = 0; depth < column.topShear(); depth++) {
            this.shearSection(frame, column, column.topEnd() - 1 - depth, depth, column.topShear(), noise);
        }
    }

    private void shearSection(ShaftFrame frame, ShaftLayout.Column column, int y, int depth, int band, FractalNoise noise) {
        if (this.inBox(column, y)) {
            return;
        }
        double threshold = 1 - 2.0 * depth / band;
        for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
            for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                BlockPos pos = frame.at(y, first, second);
                if (this.inChunk(pos) && noise.sample(pos.getX(), pos.getY(), pos.getZ()) < threshold) {
                    this.chunk.setBlockState(pos, AIR, false);
                }
            }
        }
    }

}
