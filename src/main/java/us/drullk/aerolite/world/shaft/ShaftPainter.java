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
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the shafts, columns and junctions of one chunk.
 * Volumes come first, then maze paths, then decay, then shear.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public final class ShaftPainter {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ShaftLayout layout;
    private final AeroliteSettings settings;
    private final ChunkAccess chunk;
    private final int minX;
    private final int minZ;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    /** The maze grid blocks of shafts and columns, outside the junction boxes. */
    private final LongSet shaftGrid = new LongOpenHashSet();
    /** The maze grid blocks of the patches, with the outward face direction. */
    private final Long2ObjectMap<Direction> patchGrid = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<MazeCarver.Carve> carves = new Long2ObjectOpenHashMap<>();

    private ShaftPainter(ShaftLayout layout, ChunkAccess chunk) {
        this.layout = layout;
        this.settings = layout.settings();
        this.chunk = chunk;
        ChunkPos pos = chunk.getPos();
        this.minX = pos.getMinBlockX();
        this.minZ = pos.getMinBlockZ();
    }

    public static void paint(ShaftLayout layout, ChunkAccess chunk) {
        new ShaftPainter(layout, chunk).paint();
    }

    private void paint() {
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
        ShaftFrame frame = ShaftFrame.of(shaft);
        int alongMin = this.alongMin(shaft.axis());
        List<ShaftMaze.Box> boxes = this.layout.shaftMaze(shaft).boxesNear(alongMin, alongMin + 15);
        for (int along = alongMin; along < alongMin + 16; along++) {
            int at = along;
            boolean inBox = boxes.stream().anyMatch(box -> box.covers(at));
            this.fillSection(frame, along, !inBox);
        }
    }

    private void fillColumn(ShaftLayout.Column column) {
        ShaftFrame frame = ShaftFrame.of(column);
        for (int y = this.layout.minY(); y < this.layout.maxY(); y++) {
            this.fillSection(frame, y, !inBox(column, y));
        }
    }

    private static boolean inBox(ShaftLayout.Column column, int y) {
        for (ColumnJunction junction : column.junctions()) {
            if (Math.abs(y - junction.center()) <= Ring.BOX) {
                return true;
            }
        }
        return false;
    }

    /** Fills one 13x13 cross section. The outer layer joins the maze grid when {@code grid} is true. */
    private void fillSection(ShaftFrame frame, int along, boolean grid) {
        for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
            for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                BlockPos pos = frame.at(along, first, second);
                if (!this.inChunk(pos)) {
                    continue;
                }
                boolean outer = Math.max(Math.abs(first), Math.abs(second)) == Ring.OUTER;
                this.chunk.setBlockState(pos, outer ? this.settings.wallBlock() : this.settings.interiorBlock(), false);
                if (outer && grid) {
                    this.shaftGrid.add(pos.asLong());
                }
            }
        }
    }

    private void fillBoxes(ShaftLayout.Column column) {
        for (ColumnJunction junction : column.junctions()) {
            for (int dx = -Ring.BOX; dx <= Ring.BOX; dx++) {
                for (int dy = -Ring.BOX; dy <= Ring.BOX; dy++) {
                    for (int dz = -Ring.BOX; dz <= Ring.BOX; dz++) {
                        this.set(this.cursor.set(column.x() + dx, junction.center() + dy, column.z() + dz), this.settings.interiorBlock());
                    }
                }
            }
            MazeCarver.PatchFace columnFace = MazeCarver.columnPatchFace(column, junction.box());
            this.addPatchGrid(columnFace);
            this.addPatchGrid(new MazeCarver.PatchFace(columnFace.frame(), -columnFace.sign(), columnFace.center()));
        }
    }

    /** Adds the 13x13 inside of a patch face to the maze grid. The rim stays wall. */
    private void addPatchGrid(MazeCarver.PatchFace face) {
        for (int along = -Ring.OUTER; along <= Ring.OUTER; along++) {
            for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
                MazeCarver.Carve carve = face.carve(face.center() + along, first);
                if (this.inChunk(carve.surface())) {
                    this.patchGrid.put(carve.surface().asLong(), carve.normal());
                }
            }
        }
    }

    private void carveShaft(ShaftLayout.Shaft shaft) {
        ShaftFrame frame = ShaftFrame.of(shaft);
        ShaftMaze maze = this.layout.shaftMaze(shaft);
        int chunkAlong = Math.floorDiv(this.alongMin(shaft.axis()), ShaftMaze.CHUNK);
        ShaftMaze.Segment segment = maze.segmentAt(chunkAlong);
        MazeCarver.shaftSegment(frame, segment, this::carve);
        if (segment.firstChunk() == chunkAlong) {
            MazeCarver.shaftSegment(frame, maze.segmentAt(chunkAlong - 1), this::carve);
        }
    }

    /** Carves every segment of the column, since the column spans the build height of the chunk. */
    private void carveColumn(ShaftLayout.Column column) {
        ShaftMaze maze = this.layout.columnMaze(column);
        int chunk = maze.minChunk();
        while (chunk <= maze.maxChunk()) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            MazeCarver.columnSegment(column, segment, this::carve);
            chunk = segment.lastChunk() + 1;
        }
    }

    private void carve(MazeCarver.Carve carve) {
        if (!this.inChunk(carve.surface())) {
            return;
        }
        this.chunk.setBlockState(carve.surface(), AIR, false);
        this.set(carve.beneath(), this.settings.pathBlock());
        long key = carve.surface().asLong();
        this.carves.put(key, carve);
        if (carve.normal() != null) {
            this.patchGrid.put(key, carve.normal());
        }
    }

    /**
     * Far negative decay noise keeps only maze walls. Far positive decay noise keeps only maze paths.
     * On a patch, the box block that decay exposes becomes the maze path block.
     */
    private void decay() {
        FractalNoise noise = this.layout.decayNoise();
        double threshold = this.settings.decayThreshold();
        LongSet grid = new LongOpenHashSet(this.shaftGrid);
        grid.addAll(this.patchGrid.keySet());
        for (long key : grid) {
            BlockPos pos = BlockPos.of(key);
            double value = noise.sample(pos.getX(), pos.getY(), pos.getZ());
            if (value >= -threshold && value <= threshold) {
                continue;
            }
            Direction normal = this.patchGrid.get(key);
            MazeCarver.Carve carve = this.carves.get(key);
            if (carve != null && value < -threshold) {
                this.set(carve.beneath(), AIR);
                if (normal != null) {
                    this.set(carve.beneath().relative(normal.getOpposite()), this.settings.pathBlock());
                }
            } else if (carve == null && value > threshold) {
                this.set(pos, AIR);
                if (normal != null) {
                    this.set(pos.relative(normal.getOpposite()), this.settings.pathBlock());
                }
            }
        }
    }

    /** Near a build limit, a threshold that rises toward the limit removes column blocks. */
    private void shear(ShaftLayout.Column column) {
        FractalNoise noise = this.layout.shearNoise();
        ShaftFrame frame = ShaftFrame.of(column);
        for (int depth = 0; depth < column.bottomShear(); depth++) {
            this.shearSection(frame, column, this.layout.minY() + depth, depth, column.bottomShear(), noise);
        }
        for (int depth = 0; depth < column.topShear(); depth++) {
            this.shearSection(frame, column, this.layout.maxY() - 1 - depth, depth, column.topShear(), noise);
        }
    }

    private void shearSection(ShaftFrame frame, ShaftLayout.Column column, int y, int depth, int band, FractalNoise noise) {
        if (inBox(column, y)) {
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
