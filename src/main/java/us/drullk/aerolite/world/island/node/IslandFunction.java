package us.drullk.aerolite.world.island.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import us.drullk.aerolite.world.island.IslandCells;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;
import us.drullk.aerolite.world.island.variation.CellRolls;
import us.drullk.aerolite.world.island.variation.IslandPicks;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.PreviewOverride;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * The island function, the density function type {@code aerolite:island_variations}. It finds the cell of a block
 * and computes the density function of the variation of that cell with an {@link IslandContext}.
 * A cell outside the row 0, an Empty cell, a cell without a variation and a lost cell give the lowest value.
 *
 * <p>The lattice takes the Y scale {@code cell size / cell height}, the cell weight of the pick and rolls,
 * and the cell weight bounds of the variations that the list reaches. The noise is the seed channel, like the noise of {@link VoronoiIslands}.
 *
 * <p>The function resolves its list at its first use, not in its constructor, because a holder has no value during the registry load.
 * {@link #mapAll} maps the density function of each reached variation, so the random state wires their noises.
 * A mapped copy shares the resolved list. It also shares the lattice while the visitor keeps the noise holder.
 *
 * <p>The preview override is a transient field. The codec does not write it, and a mapped copy keeps it.
 * {@link #rebind} gives the island functions of a tree another lookup and another override. The preview build overlays the edits of the flow with it.
 *
 * <p>The function maps the graph of each variation without its {@code flat_cache} and {@code cache_2d} markers. Each such marker gives the value of its input.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandFunction implements DensityFunction.SimpleFunction, IslandCells {

    public static final MapCodec<IslandFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(IslandFunction::noise),
            Codec.intRange(VoronoiIslands.MIN_CELL, VoronoiIslands.MAX_CELL).fieldOf("cell_size").forGetter(IslandFunction::cellSize),
            Codec.intRange(VoronoiIslands.MIN_CELL, VoronoiIslands.MAX_CELL).fieldOf("cell_height").forGetter(IslandFunction::cellHeight),
            Codec.INT.fieldOf("layer_min_y").forGetter(IslandFunction::layerMinY),
            Codec.doubleRange(0.0, 1.0).fieldOf("jitter").forGetter(IslandFunction::jitter),
            ResourceKey.codec(IslandVariationRegistries.ISLAND_VARIATION_LIST).fieldOf("variations").forGetter(IslandFunction::variations),
            VariationLookup.CODEC.forGetter(IslandFunction::lookup)
    ).apply(instance, IslandFunction::new));
    public static final KeyDispatchDataCodec<IslandFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** The marker names that the island function drops inside the graph of a variation. */
    private static final Set<String> DROPPED_CACHES = Set.of("flat_cache", "cache_2d");
    /** The largest ring of the search for the nearest cell of the row 0. */
    private static final int MAX_RING = 64;

    private final DensityFunction.NoiseHolder noise;
    private final int cellSize;
    private final int cellHeight;
    private final int layerMinY;
    private final double jitter;
    private final ResourceKey<IslandVariationList> variations;
    private final VariationLookup lookup;
    private final PreviewOverride override;
    private final Resolved resolved;
    private final Seeded seeded;
    /** The density function of each reached variation, or null until the first use. An Empty variation has none. */
    @Nullable
    private volatile DensityFunction[] functions;

    public IslandFunction(DensityFunction.NoiseHolder noise, int cellSize, int cellHeight, int layerMinY, double jitter, ResourceKey<IslandVariationList> variations,
                          VariationLookup lookup) {
        this(noise, cellSize, cellHeight, layerMinY, jitter, variations, lookup, PreviewOverride.NONE, new Resolved(), new Seeded(), null);
    }

    private IslandFunction(DensityFunction.NoiseHolder noise, int cellSize, int cellHeight, int layerMinY, double jitter, ResourceKey<IslandVariationList> variations,
                           VariationLookup lookup, PreviewOverride override, Resolved resolved, Seeded seeded, @Nullable DensityFunction[] functions) {
        this.noise = noise;
        this.cellSize = cellSize;
        this.cellHeight = cellHeight;
        this.layerMinY = layerMinY;
        this.jitter = jitter;
        this.variations = variations;
        this.lookup = lookup;
        this.override = override;
        this.resolved = resolved;
        this.seeded = seeded;
        this.functions = functions;
    }

    public DensityFunction.NoiseHolder noise() {
        return this.noise;
    }

    public int cellSize() {
        return this.cellSize;
    }

    public int cellHeight() {
        return this.cellHeight;
    }

    public int layerMinY() {
        return this.layerMinY;
    }

    public double jitter() {
        return this.jitter;
    }

    /** The id of the island variation list. */
    public ResourceKey<IslandVariationList> variations() {
        return this.variations;
    }

    public VariationLookup lookup() {
        return this.lookup;
    }

    public PreviewOverride override() {
        return this.override;
    }

    /**
     * A copy with the preview override. The copy resolves the list again and maps the density functions of the variations from their holders.
     * So the caller gives the override before the random state wires the function.
     */
    public IslandFunction withOverride(PreviewOverride override) {
        return new IslandFunction(this.noise, this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.variations, this.lookup, override,
                new Resolved(), new Seeded(), null);
    }

    /** The function with the preview override in each island function of the tree. */
    public static DensityFunction withOverride(DensityFunction function, PreviewOverride override) {
        return function.mapAll(visited -> visited instanceof IslandFunction islands ? islands.withOverride(override) : visited);
    }

    /**
     * The visitor that gives each island function of a tree the lookup and the preview override.
     * An island function maps no variation graph for it, so the old lookup resolves no list.
     * The caller maps the tree before the random state wires it.
     */
    public static DensityFunction.Visitor rebind(VariationLookup lookup, PreviewOverride override) {
        return new Rebind(lookup, override);
    }

    /** The visitor of {@link #rebind}. It keeps each function, and an island function takes the lookup in its {@link #mapAll}. */
    private record Rebind(VariationLookup lookup, PreviewOverride override) implements DensityFunction.Visitor {

        @Override
        public DensityFunction apply(DensityFunction function) {
            return function;
        }
    }

    /** The resolved pick tree of the list and the override. */
    public IslandPicks picks() {
        return this.resolved.get(this);
    }

    /** The lattice of the seed of the noise. */
    @Override
    public VoronoiLattice lattice() {
        return this.seeded().lattice;
    }

    /** The rolls of the seed of the noise. */
    public CellRolls rolls() {
        return this.seeded().rolls;
    }

    /** The variation of a cell of the row 0, or null for another cell and for a cell without a variation. */
    @Nullable
    public IslandVariation variation(VoronoiLattice.Cell cell) {
        if (cell.iy() != 0) {
            return null;
        }
        int index = this.picks().pick(this.rolls(), cell.ix(), cell.iz());
        return index == IslandPicks.NONE ? null : this.picks().variation(index);
    }

    /** True while the cell holds an island: a cell of the row 0 with a variation that is not Empty, and not lost. */
    @Override
    public boolean hasIsland(VoronoiLattice.Cell cell) {
        IslandVariation variation = this.variation(cell);
        return variation != null && !variation.empty() && !this.lattice().lost(cell);
    }

    /**
     * The cell of the row 0 with the least power rule value at the point. A cell of the row 0 that holds the point is that cell.
     * The search runs over rings of lattice boxes around the box column of the point. It stops at the first ring
     * whose least possible power rule value is above the best value. Each ring of boxes lies farther along X or Z by one cell size.
     */
    public VoronoiLattice.Cell nearestRowCell(double x, double y, double z) {
        VoronoiLattice lattice = this.lattice();
        VoronoiLattice.Cell own = lattice.nearest(x, y, z);
        if (own.iy() == 0) {
            return own;
        }
        int bx = Mth.floor(x / this.cellSize);
        int bz = Mth.floor(z / this.cellSize);
        double reach = 0.5 + this.jitter / 2;
        double area = (double) this.cellSize * this.cellSize;
        double yScale = lattice.yScale();
        double low = (this.layerMinY + (0.5 - this.jitter / 2) * this.cellHeight) * yScale;
        double high = (this.layerMinY + (0.5 + this.jitter / 2) * this.cellHeight) * yScale;
        double py = y * yScale;
        double vertical = py < low ? low - py : py > high ? py - high : 0;
        double best = Double.POSITIVE_INFINITY;
        VoronoiLattice.Cell found = own;
        for (int ring = 0; ring <= MAX_RING; ring++) {
            double gap = Math.max(0, ring - reach) * this.cellSize;
            if (gap * gap + vertical * vertical - lattice.weightMax() * area > best) {
                break;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    VoronoiLattice.Cell cell = lattice.cell(bx + dx, 0, bz + dz);
                    double value = lattice.powerValue(cell, x, y, z);
                    if (value < best) {
                        best = value;
                        found = cell;
                    }
                }
            }
        }
        return found;
    }

    /** The variation whose biome source gives the biome of the point: the variation of the nearest cell of the row 0, or null. */
    @Nullable
    public IslandVariation biomeVariation(double x, double y, double z) {
        return this.variation(this.nearestRowCell(x, y, z));
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        State seeded = this.seeded();
        VoronoiLattice.Cell cell = seeded.lattice.nearest(context.blockX(), context.blockY(), context.blockZ());
        if (cell.iy() != 0) {
            return this.lowest();
        }
        IslandPicks picks = this.picks();
        int index = picks.pick(seeded.rolls, cell.ix(), cell.iz());
        if (index == IslandPicks.NONE) {
            return this.lowest();
        }
        IslandVariation variation = picks.variation(index);
        if (variation.empty() || seeded.lattice.lost(cell)) {
            return this.lowest();
        }
        double fill = picks.fill(seeded.rolls, index, cell.ix(), cell.iz());
        IslandContext islandContext = new IslandContext(context, seeded.lattice, cell, variation, seeded.distortions[index], seeded.rolls, fill);
        return this.functions()[index].compute(islandContext);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        if (visitor instanceof Rebind rebind) {
            return new IslandFunction(this.noise, this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.variations, rebind.lookup, rebind.override,
                    new Resolved(), new Seeded(), null);
        }
        DensityFunction[] current = this.functions();
        DensityFunction[] mapped = new DensityFunction[current.length];
        for (int i = 0; i < current.length; i++) {
            mapped[i] = current[i] == null ? null : current[i].mapAll(visitor);
        }
        DensityFunction.NoiseHolder visited = visitor.visitNoise(this.noise);
        Seeded seeded = visited == this.noise ? this.seeded : new Seeded();
        return visitor.apply(new IslandFunction(visited, this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.variations, this.lookup, this.override,
                this.resolved, seeded, mapped));
    }

    /** The value of a block without an island. */
    public double lowest() {
        return -2.0 * Math.max(this.cellSize, this.cellHeight);
    }

    /**
     * The lowest value and the least value of each variation function. Before the list resolves, no bound.
     * The bounds never resolve the list. Vanilla reads them during the registry load, and a lookup then adds a holder.
     * A holder of a missing list stays without a value, and the registry freeze fails on it.
     */
    @Override
    public double minValue() {
        DensityFunction[] functions = this.functions;
        if (functions == null) {
            return Double.NEGATIVE_INFINITY;
        }
        double min = this.lowest();
        for (DensityFunction function : functions) {
            if (function != null) {
                min = Math.min(min, function.minValue());
            }
        }
        return min;
    }

    /** The largest value of the lowest value and of each variation function. Before the list resolves, no bound. The bound never resolves the list. */
    @Override
    public double maxValue() {
        DensityFunction[] functions = this.functions;
        if (functions == null) {
            return Double.POSITIVE_INFINITY;
        }
        double max = this.lowest();
        for (DensityFunction function : functions) {
            if (function != null) {
                max = Math.max(max, function.maxValue());
            }
        }
        return max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private DensityFunction[] functions() {
        DensityFunction[] functions = this.functions;
        if (functions == null) {
            IslandPicks picks = this.picks();
            functions = new DensityFunction[picks.variations().size()];
            for (int i = 0; i < functions.length; i++) {
                IslandVariation variation = picks.variation(i);
                functions[i] = variation.densityFunction().map(holder -> new DensityFunctions.HolderHolder(holder).mapAll(IslandFunction::dropCache)).orElse(null);
            }
            this.functions = functions;
        }
        return functions;
    }

    /**
     * A {@code flat_cache} or {@code cache_2d} marker of a variation graph gives way to its input.
     * A noise chunk fills these caches without the island context, so they read no cell.
     */
    private static DensityFunction dropCache(DensityFunction function) {
        if (function instanceof DensityFunctions.MarkerOrMarked marker) {
            Object type = marker.type();
            if (type instanceof StringRepresentable named && DROPPED_CACHES.contains(named.getSerializedName())) {
                return marker.wrapped();
            }
        }
        return function;
    }

    private State seeded() {
        return this.seeded.get(this);
    }

    /** The resolved pick tree. Mapped copies share it. Each entry is a pure function of the list, the lookup and the override. */
    private static final class Resolved {

        @Nullable
        private volatile IslandPicks picks;

        private IslandPicks get(IslandFunction function) {
            IslandPicks found = this.picks;
            if (found == null) {
                synchronized (this) {
                    found = this.picks;
                    if (found == null) {
                        found = IslandPicks.resolve(function.variations, function.lookup, function.override);
                        this.picks = found;
                    }
                }
            }
            return found;
        }
    }

    /** The lattice, the rolls and the distortion of each variation for the seed of one noise holder. Mapped copies with that holder share it. */
    private static final class Seeded {

        @Nullable
        private volatile State state;

        private State get(IslandFunction function) {
            State found = this.state;
            if (found == null) {
                synchronized (this) {
                    found = this.state;
                    if (found == null) {
                        found = State.build(function);
                        this.state = found;
                    }
                }
            }
            return found;
        }
    }

    /** The seeded state of one noise holder. */
    private record State(VoronoiLattice lattice, CellRolls rolls, Distortion[] distortions) {

        private static State build(IslandFunction function) {
            IslandPicks picks = function.picks();
            long seed = VoronoiIslands.seedOf(function.noise);
            CellRolls rolls = new CellRolls(seed);
            double yScale = (double) function.cellSize / function.cellHeight;
            VoronoiLattice lattice = new VoronoiLattice(seed, function.cellSize, function.cellHeight, function.layerMinY, function.jitter, yScale,
                    (ix, iz) -> picks.cellWeight(rolls, ix, iz), picks.weightMin(), picks.weightMax());
            Distortion[] distortions = new Distortion[picks.variations().size()];
            for (int i = 0; i < distortions.length; i++) {
                IslandVariation variation = picks.variation(i);
                distortions[i] = new Distortion(seed, variation.distortion(), variation.distortionScale(), variation.distortionOctaves());
            }
            return new State(lattice, rolls, distortions);
        }
    }

}
