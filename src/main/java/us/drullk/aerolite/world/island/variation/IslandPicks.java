package us.drullk.aerolite.world.island.variation;

import net.minecraft.resources.ResourceKey;
import us.drullk.aerolite.Aerolite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The resolved pick tree of one island variation list, and the rolls of each cell of the row 0.
 * The pick is a pure function of the lattice seed, the cell and the list. It does not read the site.
 * The pick takes one seeded value per list depth and walks the entries in their sequence by weight. A weight of 0 never picks.
 * A sub-list picks again with the seeded value of the next depth.
 * An entry that names a missing entry, or a list on its own path, gives no island. The resolve logs one line per such list or variation.
 * A cell without a variation has the cell weight 0.5.
 * A preview override replaces the root of the pick or the rolls.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandPicks {

    /** The pick of a cell without a variation. */
    public static final int NONE = -1;
    /** The cell weight of a cell without a variation. */
    public static final double NO_VARIATION_WEIGHT = 0.5;

    private final List<ResourceKey<IslandVariation>> keys;
    private final List<IslandVariation> variations;
    private final Node root;
    private final PreviewOverride override;
    private final double weightMin;
    private final double weightMax;

    /** One node of the pick tree. */
    private sealed interface Node {
    }

    /** A variation by its index in the reached variations. */
    private record Leaf(int index) implements Node {
    }

    /** No island. */
    private record Missing() implements Node {
    }

    /** A list: the running weight sums of its entries and their nodes. */
    private record Branch(int[] sums, Node[] children) implements Node {

        private int total() {
            return this.sums[this.sums.length - 1];
        }
    }

    private IslandPicks(List<ResourceKey<IslandVariation>> keys, List<IslandVariation> variations, Node root, PreviewOverride override) {
        this.keys = List.copyOf(keys);
        this.variations = List.copyOf(variations);
        this.root = root;
        this.override = override;
        if (override.cellWeight().isPresent()) {
            this.weightMin = override.cellWeight().getAsDouble();
            this.weightMax = this.weightMin;
        } else {
            double[] bounds = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
            this.bounds(root, bounds);
            this.weightMin = bounds[0];
            this.weightMax = bounds[1];
        }
    }

    /**
     * Resolves the list through the lookup. The override replaces the root.
     *
     * @throws VariationLookup.Unbound during the registry load, before an entry has its value
     */
    public static IslandPicks resolve(ResourceKey<IslandVariationList> list, VariationLookup lookup, PreviewOverride override) {
        Resolver resolver = new Resolver(lookup);
        Node root;
        if (override.soloVariation() != null) {
            root = resolver.variation(override.soloVariation());
        } else {
            root = resolver.list(override.soloList() != null ? override.soloList() : list);
        }
        return new IslandPicks(new ArrayList<>(resolver.index.keySet()), resolver.variations, root, override);
    }

    /** The reached variations, each once, in the sequence of their first reach. */
    public List<IslandVariation> variations() {
        return this.variations;
    }

    /** The keys of the reached variations, in the sequence of {@link #variations()}. */
    public List<ResourceKey<IslandVariation>> keys() {
        return this.keys;
    }

    public IslandVariation variation(int index) {
        return this.variations.get(index);
    }

    public PreviewOverride override() {
        return this.override;
    }

    /** The least cell weight that a cell of the row 0 can roll. */
    public double weightMin() {
        return this.weightMin;
    }

    /** The largest cell weight that a cell of the row 0 can roll. */
    public double weightMax() {
        return this.weightMax;
    }

    /** The index of the variation of the cell of the row 0 in {@link #variations()}, or {@link #NONE}. */
    public int pick(CellRolls rolls, int ix, int iz) {
        Node node = this.root;
        int depth = 0;
        while (node instanceof Branch branch) {
            int target = (int) (rolls.pick(ix, iz, depth++) * branch.total());
            int entry = 0;
            while (branch.sums[entry] <= target) {
                entry++;
            }
            node = branch.children[entry];
        }
        return node instanceof Leaf leaf ? leaf.index : NONE;
    }

    /** The rolled cell weight of the cell of the row 0, or the pinned cell weight. */
    public double cellWeight(CellRolls rolls, int ix, int iz) {
        if (this.override.cellWeight().isPresent()) {
            return this.override.cellWeight().getAsDouble();
        }
        int index = this.pick(rolls, ix, iz);
        return index == NONE ? NO_VARIATION_WEIGHT : rolls.cellWeight(this.variations.get(index).cellWeight(), ix, iz);
    }

    /** The rolled fill of the cell of the row 0 with the variation of the index, or the pinned fill. */
    public double fill(CellRolls rolls, int index, int ix, int iz) {
        if (this.override.fill().isPresent()) {
            return this.override.fill().getAsDouble();
        }
        return rolls.fill(this.variations.get(index).fill(), ix, iz);
    }

    private void bounds(Node node, double[] bounds) {
        switch (node) {
            case Leaf leaf -> {
                UnitRange range = this.variations.get(leaf.index).cellWeight();
                bounds[0] = Math.min(bounds[0], range.min());
                bounds[1] = Math.max(bounds[1], range.max());
            }
            case Missing missing -> {
                bounds[0] = Math.min(bounds[0], NO_VARIATION_WEIGHT);
                bounds[1] = Math.max(bounds[1], NO_VARIATION_WEIGHT);
            }
            case Branch branch -> {
                for (Node child : branch.children) {
                    this.bounds(child, bounds);
                }
            }
        }
    }

    /** One resolve: the reached variations, the lists on the current path and the logged keys. */
    private static final class Resolver {

        private final VariationLookup lookup;
        private final Map<ResourceKey<IslandVariation>, Integer> index = new LinkedHashMap<>();
        private final List<IslandVariation> variations = new ArrayList<>();
        private final Deque<ResourceKey<IslandVariationList>> path = new ArrayDeque<>();
        private final Set<ResourceKey<?>> logged = new HashSet<>();

        private Resolver(VariationLookup lookup) {
            this.lookup = lookup;
        }

        private Node variation(ResourceKey<IslandVariation> key) {
            Integer known = this.index.get(key);
            if (known != null) {
                return new Leaf(known);
            }
            return this.lookup.variation(key).<Node>map(variation -> {
                this.index.put(key, this.variations.size());
                this.variations.add(variation);
                return new Leaf(this.variations.size() - 1);
            }).orElseGet(() -> {
                this.log(key, "The island variation {} does not exist, so its entries give no island");
                return new Missing();
            });
        }

        private Node list(ResourceKey<IslandVariationList> key) {
            if (this.path.contains(key)) {
                this.log(key, "The island variation list {} names itself through its entries, so that entry gives no island");
                return new Missing();
            }
            IslandVariationList list = this.lookup.list(key).orElse(null);
            if (list == null) {
                this.log(key, "The island variation list {} does not exist, so its entries give no island");
                return new Missing();
            }
            this.path.push(key);
            List<Integer> sums = new ArrayList<>();
            List<Node> children = new ArrayList<>();
            int total = 0;
            for (IslandVariationList.Entry entry : list.entries()) {
                if (entry.weight() <= 0) {
                    continue;
                }
                total += entry.weight();
                sums.add(total);
                children.add(entry.target().map(this::variation, this::list));
            }
            this.path.pop();
            if (total == 0) {
                return new Missing();
            }
            return new Branch(sums.stream().mapToInt(Integer::intValue).toArray(), children.toArray(Node[]::new));
        }

        private void log(ResourceKey<?> key, String message) {
            if (this.logged.add(key)) {
                Aerolite.LOGGER.warn(message, key.location());
            }
        }
    }

}
