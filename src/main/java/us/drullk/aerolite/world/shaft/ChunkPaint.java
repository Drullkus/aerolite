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
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionBlock;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The paint of the shafts, columns and junctions of one chunk.
 * Volumes come first, then the surface patterns, then decay, then erosion, then shear.
 * The ring description of the shape of each shaft and column gives its cross section and its outer blocks.
 * A surface path block decays like a maze wall.
 * Each shaft and column paints with its own variation. A junction box paints with the variation of its horizontal shaft.
 * The bezel of each patch face is the junction block. The patch area inside it is the maze wall block over the box.
 * Decay and erosion read the thresholds and the decay noise of the variation of each block.
 * The maze grid of the chunk holds the grid blocks up to two blocks outside the chunk. Their maze path blocks and decay writes can lie in the chunk.
 * Each provider samples with a random seeded by the world seed and the block position.
 * Every step writes to a buffer of the finished shaft blocks. The chunk gets only the solid blocks of the buffer.
 * So a block of the chunk stays where the finished shaft is air.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-01_7b2c6d29-0ea3-4452-a1db-06e54416a4d5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ChunkPaint {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The blocks beyond a chunk edge whose maze grid blocks write into the chunk. A patch decay writes two blocks inward. */
    private static final int GRID_MARGIN = 2;
    /** The blocks of one 16x16x16 buffer section. */
    private static final int SECTION_BLOCKS = 16 * 16 * 16;

    /** A patch block: its outward face direction and the variation of its junction box. */
    private record PatchBlock(Direction normal, ShaftVariation variation) {
    }

    /** A maze path block and the variation that gives its maze path block. */
    private record PaintedCarve(SurfaceCarver.Carve carve, ShaftVariation variation) {
    }

    private final SurfaceCarver carver;
    private final ShaftFrames frames;
    private final ErosionResistance resistance;
    private final ShaftLayout layout;
    private final ChunkAccess chunk;
    private final int minX;
    private final int minZ;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    /** The provider random. Each sample reseeds it from the block position. */
    private final XoroshiroRandomSource random = new XoroshiroRandomSource(0L);
    /** The maze grid blocks of shafts and columns outside the junction boxes, with the variation of each block. */
    private final Long2ObjectMap<ShaftVariation> shaftGrid = new Long2ObjectOpenHashMap<>();
    /** The maze grid blocks of the patches. */
    private final Long2ObjectMap<PatchBlock> patchGrid = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PaintedCarve> carves = new Long2ObjectOpenHashMap<>();
    /** The painted blocks of the chunk by 16-block band from the bottom build limit, air included. A null entry is unpainted. */
    private final BlockState[][] buffer;

    ChunkPaint(SurfaceCarver carver, ShaftFrames frames, ErosionResistance resistance, ShaftLayout layout, ChunkAccess chunk) {
        this.carver = carver;
        this.frames = frames;
        this.resistance = resistance;
        this.layout = layout;
        this.chunk = chunk;
        ChunkPos pos = chunk.getPos();
        this.minX = pos.getMinBlockX();
        this.minZ = pos.getMinBlockZ();
        this.buffer = new BlockState[Math.ceilDiv(layout.maxY() - layout.minY(), 16)][];
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
        this.flush();
    }

    /** Writes each painted block that is not air to the chunk. */
    private void flush() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int band = 0; band < this.buffer.length; band++) {
            BlockState[] section = this.buffer[band];
            if (section == null) {
                continue;
            }
            for (int index = 0; index < SECTION_BLOCKS; index++) {
                BlockState state = section[index];
                if (state != null && !state.isAir()) {
                    pos.set(this.minX + (index & 15), this.layout.minY() + band * 16 + (index >> 8), this.minZ + (index >> 4 & 15));
                    this.chunk.setBlockState(pos, state, false);
                }
            }
        }
    }

    private boolean inChunk(BlockPos pos) {
        return pos.getX() >= this.minX && pos.getX() < this.minX + 16
                && pos.getZ() >= this.minZ && pos.getZ() < this.minZ + 16
                && pos.getY() >= this.layout.minY() && pos.getY() < this.layout.maxY();
    }

    /** True when a write of the maze grid block at {@code pos} can lie in the chunk. */
    private boolean nearChunk(BlockPos pos) {
        return pos.getX() >= this.minX - GRID_MARGIN && pos.getX() < this.minX + 16 + GRID_MARGIN
                && pos.getZ() >= this.minZ - GRID_MARGIN && pos.getZ() < this.minZ + 16 + GRID_MARGIN
                && pos.getY() >= this.layout.minY() && pos.getY() < this.layout.maxY();
    }

    /** Writes the block to the buffer. Only the flush writes to the chunk. */
    private void set(BlockPos pos, BlockState state) {
        if (!this.inChunk(pos)) {
            return;
        }
        int height = pos.getY() - this.layout.minY();
        BlockState[] section = this.buffer[height >> 4];
        if (section == null) {
            section = new BlockState[SECTION_BLOCKS];
            this.buffer[height >> 4] = section;
        }
        section[(pos.getX() - this.minX) | (pos.getZ() - this.minZ) << 4 | (height & 15) << 8] = state;
    }

    /** Writes the block of the provider at the position to the buffer. */
    private void set(BlockPos pos, BlockStateProvider provider) {
        if (!this.inChunk(pos)) {
            return;
        }
        if (!(provider instanceof SimpleStateProvider)) {
            this.random.setSeed(this.layout.blockSeed(pos));
        }
        this.set(pos, provider.getState(this.random, pos));
    }

    private int alongMin(ShaftLayout.Axis axis) {
        return axis == ShaftLayout.Axis.X ? this.minX : this.minZ;
    }

    private void fillShaft(ShaftLayout.Shaft shaft) {
        ShaftFrame frame = this.frames.of(shaft);
        ShaftVariation variation = this.layout.variation(shaft);
        RingShape ring = this.layout.ring(shaft);
        int alongMin = this.alongMin(shaft.axis());
        List<ShaftMaze.Box> boxes = this.layout.boxesNear(shaft, alongMin, alongMin + 15);
        for (int along = alongMin; along < alongMin + 16; along++) {
            int at = along;
            boolean inBox = boxes.stream().anyMatch(box -> box.covers(at));
            this.fillSection(frame, ring, along, !inBox, variation);
        }
    }

    private void fillColumn(ShaftLayout.Column column) {
        ShaftFrame frame = this.frames.of(column);
        ShaftVariation variation = this.layout.variation(column);
        RingShape ring = this.layout.ring(column);
        for (int y = column.bottomEnd(); y < column.topEnd(); y++) {
            this.fillSection(frame, ring, y, !this.inBox(column, y), variation);
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

    /** Fills one cross section. The outer layer joins the maze grid when {@code grid} is true. */
    private void fillSection(ShaftFrame frame, RingShape ring, int along, boolean grid, ShaftVariation variation) {
        for (SectionBlock block : ring.section()) {
            BlockPos pos = frame.at(along, block.first(), block.second());
            this.set(pos, block.outer() ? variation.wall() : variation.interior());
            if (block.outer() && grid && this.nearChunk(pos)) {
                this.shaftGrid.put(pos.asLong(), variation);
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
            SurfaceCarver.PatchFace columnFace = this.carver.columnPatchFace(column, junction.box());
            this.fillPatchFace(columnFace, variation);
            this.fillPatchFace(columnFace.opposite(), variation);
        }
    }

    /** Paints one patch face and adds all of it to the maze grid. The bezel joins the grid as maze wall. */
    private void fillPatchFace(SurfaceCarver.PatchFace face, ShaftVariation variation) {
        for (int along = -Ring.BOX; along <= Ring.BOX; along++) {
            for (int first = -Ring.BOX; first <= Ring.BOX; first++) {
                SurfaceCarver.Carve carve = face.carve(face.center() + along, first);
                boolean bezel = Ring.BOX - Math.max(Math.abs(along), Math.abs(first)) < Ring.BEZEL;
                this.set(carve.surface(), bezel ? variation.junction() : variation.wall());
                if (this.nearChunk(carve.surface())) {
                    this.patchGrid.put(carve.surface().asLong(), new PatchBlock(carve.normal(), variation));
                }
            }
        }
    }

    /** Paints the segment surfaces of a horizontal shaft. Its patches belong to its own junction boxes, so they share its variation. */
    private void carveShaft(ShaftLayout.Shaft shaft) {
        ShaftFrame frame = this.frames.of(shaft);
        SurfacePattern pattern = this.layout.shaftMaze(shaft);
        ShaftVariation variation = this.layout.variation(shaft);
        SurfaceCarver.Sink sink = this.sink(variation, variation);
        int chunkAlong = Math.floorDiv(this.alongMin(shaft.axis()), ShaftMaze.CHUNK);
        SegmentSurface segment = pattern.segmentAt(chunkAlong);
        this.carver.shaftSegment(frame, pattern.ring(), segment, sink);
        if (segment.firstChunk() == chunkAlong) {
            this.carver.shaftSegment(frame, pattern.ring(), pattern.segmentAt(chunkAlong - 1), sink);
        }
    }

    /** Paints every segment surface of the column, since the column spans the build height of the chunk. */
    private void carveColumn(ShaftLayout.Column column) {
        SurfacePattern pattern = this.layout.columnMaze(column);
        ShaftVariation variation = this.layout.variation(column);
        SurfaceCarver.Sink sink = new SurfaceCarver.Sink() {
            @Override
            public void groove(SurfaceCarver.Carve carve) {
                int y = carve.surface().getY();
                if (column.spans(y)) {
                    ChunkPaint.this.carve(carve, ChunkPaint.this.variationAt(column, y, carve.normal(), variation));
                }
            }

            @Override
            public void surface(BlockPos pos, @Nullable Direction normal) {
                if (column.spans(pos.getY())) {
                    ChunkPaint.this.set(pos, ChunkPaint.this.variationAt(column, pos.getY(), normal, variation).surface());
                }
            }
        };
        int chunk = pattern.minChunk();
        while (chunk <= pattern.maxChunk()) {
            SegmentSurface segment = pattern.segmentAt(chunk);
            this.carver.columnSegment(column, pattern.ring(), segment, sink);
            chunk = segment.lastChunk() + 1;
        }
    }

    /** The variation of a column block: the box variation on a patch face, else the column variation. */
    private ShaftVariation variationAt(ShaftLayout.Column column, int y, @Nullable Direction normal, ShaftVariation variation) {
        ColumnJunction junction = normal == null ? null : this.junctionAt(column, y);
        return junction == null ? variation : this.boxVariation(column, junction);
    }

    /** A sink that paints ring blocks with one variation and patch blocks with the other. */
    private SurfaceCarver.Sink sink(ShaftVariation ring, ShaftVariation patch) {
        return new SurfaceCarver.Sink() {
            @Override
            public void groove(SurfaceCarver.Carve carve) {
                ChunkPaint.this.carve(carve, carve.normal() == null ? ring : patch);
            }

            @Override
            public void surface(BlockPos pos, @Nullable Direction normal) {
                ChunkPaint.this.set(pos, (normal == null ? ring : patch).surface());
            }
        };
    }

    /** Writes the blocks of one maze path that lie in the chunk. Its maze grid block can lie in the next chunk. */
    private void carve(SurfaceCarver.Carve carve, ShaftVariation variation) {
        if (!this.nearChunk(carve.surface())) {
            return;
        }
        this.set(carve.surface(), AIR);
        for (BlockPos floor : carve.floors()) {
            this.set(floor, variation.path());
        }
        long key = carve.surface().asLong();
        this.carves.put(key, new PaintedCarve(carve, variation));
        if (carve.normal() != null) {
            this.patchGrid.put(key, new PatchBlock(carve.normal(), variation));
        }
    }

    /**
     * Far negative decay noise keeps only maze walls. Far positive decay noise keeps only maze paths.
     * A removed maze wall exposes the block beneath it. On a patch, a removed maze path exposes the maze path block.
     * A surface path block is not a carve, so it decays like a maze wall.
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
                for (BlockPos floor : painted.carve().floors()) {
                    this.set(floor, AIR);
                    if (patch != null) {
                        this.set(floor.relative(patch.normal().getOpposite()), painted.variation().path());
                    }
                }
            } else if (painted == null && value > threshold) {
                this.set(pos, AIR);
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
            RingShape ring = this.layout.ring(shaft);
            int alongMin = this.alongMin(shaft.axis());
            List<ShaftMaze.Box> boxes = this.layout.boxesNear(shaft, alongMin, alongMin + 15);
            for (int along = alongMin; along < alongMin + 16; along++) {
                int at = along;
                if (boxes.stream().noneMatch(box -> box.covers(at))) {
                    this.erodeSection(frame, ring, along, variation);
                }
            }
        }
        for (ShaftLayout.Column column : columns) {
            ShaftFrame frame = this.frames.of(column);
            ShaftVariation variation = this.layout.variation(column);
            RingShape ring = this.layout.ring(column);
            for (int y = column.bottomEnd(); y < column.topEnd(); y++) {
                if (!this.inBox(column, y)) {
                    this.erodeSection(frame, ring, y, variation);
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

    private void erodeSection(ShaftFrame frame, RingShape ring, int along, ShaftVariation variation) {
        FractalNoise noise = this.layout.decayNoise(variation);
        for (SectionBlock block : ring.section()) {
            BlockPos pos = frame.at(along, block.first(), block.second());
            if (this.inChunk(pos) && this.erosionReach(pos, noise, variation.erosionThreshold()) > this.resistance.section(block.first(), block.second())) {
                this.set(pos, AIR);
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
                        this.set(pos, AIR);
                    }
                }
            }
        }
    }

    /** Near each column end, a threshold that rises toward the end removes column blocks. */
    private void shear(ShaftLayout.Column column) {
        FractalNoise noise = this.layout.shearNoise();
        ShaftFrame frame = this.frames.of(column);
        RingShape ring = this.layout.ring(column);
        for (int depth = 0; depth < column.bottomShear(); depth++) {
            this.shearSection(frame, ring, column, column.bottomEnd() + depth, depth, column.bottomShear(), noise);
        }
        for (int depth = 0; depth < column.topShear(); depth++) {
            this.shearSection(frame, ring, column, column.topEnd() - 1 - depth, depth, column.topShear(), noise);
        }
    }

    private void shearSection(ShaftFrame frame, RingShape ring, ShaftLayout.Column column, int y, int depth, int band, FractalNoise noise) {
        if (this.inBox(column, y)) {
            return;
        }
        double threshold = 1 - 2.0 * depth / band;
        for (SectionBlock block : ring.section()) {
            BlockPos pos = frame.at(y, block.first(), block.second());
            if (this.inChunk(pos) && noise.sample(pos.getX(), pos.getY(), pos.getZ()) < threshold) {
                this.set(pos, AIR);
            }
        }
    }

}
