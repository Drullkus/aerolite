package us.drullk.aerolite.world.island;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.WorldDimensions;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.VoidPresetBiomes;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.GraphInline;
import us.drullk.aerolite.world.island.graph.GraphJson;
import us.drullk.aerolite.world.island.graph.GraphProblem;
import us.drullk.aerolite.world.island.graph.GraphValidator;
import us.drullk.aerolite.world.island.graph.IslandEntries;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.node.IslandFunction;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.PreviewOverride;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Turns the graphs of the island editor into noise generator settings, and loads the graphs from the dimensions of a world.
 * A build writes the router graph, puts the JSON of each edited entry in the place of each reference to it, and decodes the router.
 * The inline serves the preview only. A reference to an entry without an edit resolves through the registry.
 * The build also puts the edited island variations and lists in place of the registry ones. It applies a preview override.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@Component
public final class GraphSettings {

    private static final String DENSITY_FUNCTION = "density_function";

    /**
     * The router JSON of a build, the JSON of its island variations and lists, and its noise settings.
     *
     * @param variations the JSON of each reached island variation and list after the inline, by the keys {@code variations} and {@code lists}
     */
    public record Built(JsonObject router, JsonObject variations, NoiseGeneratorSettings settings) {
    }

    /** The reached island variations and lists of a build, decoded, and their JSON. */
    private record Overlay(Map<ResourceKey<IslandVariation>, IslandVariation> variations, Map<ResourceKey<IslandVariationList>, IslandVariationList> lists,
                           JsonObject json) {
    }

    public GraphSettings() {
    }

    /** The router graph of each preset dimension with a noise generator. Each output links to the function nodes of the shipped router. */
    public IslandGraphs start(WorldDimensions dimensions, RegistryAccess registries) {
        IslandGraphs graphs = new IslandGraphs();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        for (ResourceKey<LevelStem> stem : VoidPresetBiomes.DIMENSIONS) {
            LevelStem levelStem = dimensions.dimensions().get(stem);
            if (levelStem == null || !(levelStem.generator() instanceof NoiseBasedChunkGenerator generator)) {
                continue;
            }
            NoiseRouter.CODEC.encodeStart(ops, generator.generatorSettings().value().noiseRouter())
                    .flatMap(json -> json.isJsonObject() ? DataResult.success(json.getAsJsonObject()) : DataResult.error(() -> "The router is not an object"))
                    .flatMap(json -> GraphJson.readRouter(json).map(graph -> Map.entry(graph, json)))
                    .ifSuccess(loaded -> graphs.putRouter(Registries.levelStemToLevel(stem), loaded.getKey(), loaded.getValue()));
        }
        return graphs;
    }

    /** The function graph of a density function entry of the registry. */
    public DataResult<FunctionGraph> loadEntry(RegistryAccess registries, ResourceLocation entry) {
        Registry<DensityFunction> registry = registries.registryOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction function = registry.get(entry);
        if (function == null) {
            return DataResult.error(() -> "The density function entry " + entry + " does not exist");
        }
        return DensityFunction.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), function)
                .mapError(message -> "The density function entry " + entry + " does not encode: " + message)
                .flatMap(GraphJson::readFunction);
    }

    /** The entry loader over the registries. */
    public IslandGraphs.EntryLoader loader(RegistryAccess registries) {
        return entry -> this.loadEntry(registries, entry);
    }

    /** The ids of the noise registry. */
    public Set<ResourceLocation> noises(RegistryAccess registries) {
        return registries.registryOrThrow(Registries.NOISE).keySet().stream().collect(Collectors.toCollection(TreeSet::new));
    }

    /** The loader of the island variations and the island variation lists of the registries. Each entry encodes with registry ops. */
    public IslandGraphs.VariationLoader variationLoader(RegistryAccess registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        Optional<? extends Registry<IslandVariation>> variations = registries.registry(IslandVariationRegistries.ISLAND_VARIATION);
        Optional<? extends Registry<IslandVariationList>> lists = registries.registry(IslandVariationRegistries.ISLAND_VARIATION_LIST);
        return new IslandGraphs.VariationLoader() {

            @Override
            public DataResult<JsonObject> variation(ResourceLocation id) {
                return encode(variations, id, IslandVariation.CODEC, ops, "island variation");
            }

            @Override
            public DataResult<JsonObject> list(ResourceLocation id) {
                return encode(lists, id, IslandVariationList.CODEC, ops, "island variation list");
            }

            @Override
            public Set<ResourceLocation> variationIds() {
                return variations.<Set<ResourceLocation>>map(registry -> Set.copyOf(registry.keySet())).orElse(Set.of());
            }

            @Override
            public Set<ResourceLocation> listIds() {
                return lists.<Set<ResourceLocation>>map(registry -> Set.copyOf(registry.keySet())).orElse(Set.of());
            }
        };
    }

    /** The saved JSON of the entries, the variations and the lists of the graphs over the registries. */
    public IslandEntries entries(IslandGraphs graphs, RegistryAccess registries) {
        return graphs.entries(this.variationLoader(registries), this.loader(registries));
    }

    /** The noise generator settings of the dimension without a preview override. See {@link #build(IslandGraphs, ResourceKey, NoiseGeneratorSettings, RegistryAccess, Set, PreviewOverride)}. */
    public DataResult<Built> build(IslandGraphs graphs, ResourceKey<Level> dimension, NoiseGeneratorSettings base, RegistryAccess registries,
                                   Set<ResourceLocation> noises) {
        return this.build(graphs, dimension, base, registries, noises, PreviewOverride.NONE);
    }

    /**
     * The noise generator settings of the dimension with the router of its router graph. The other fields come from {@code base}.
     * A validator problem is the error of the build. So is a problem of a reached island variation or list, and a decode error.
     * A router that reaches an island function without a carrier on a climate output is also an error.
     * Each island function of the router reads its list through a lookup. The lookup puts the edited variations and lists in place.
     * A reached variation takes the JSON of each edited entry in the place of each reference to it.
     * Each island function also takes the preview override.
     */
    public DataResult<Built> build(IslandGraphs graphs, ResourceKey<Level> dimension, NoiseGeneratorSettings base, RegistryAccess registries,
                                   Set<ResourceLocation> noises, PreviewOverride override) {
        RouterGraph router = graphs.router(dimension);
        if (router == null) {
            return DataResult.error(() -> "The dimension " + dimension.location() + " has no router graph");
        }
        JsonObject shipped = graphs.shipped(dimension);
        List<GraphProblem> problems = GraphValidator.validate(router, shipped, noises);
        if (!problems.isEmpty()) {
            return DataResult.error(() -> joined(problems, "\n"));
        }
        Map<ResourceLocation, DataResult<JsonElement>> edited = new LinkedHashMap<>();
        graphs.edited().forEach((entry, graph) -> {
            List<GraphProblem> entryProblems = GraphValidator.validate(graph, noises);
            edited.put(entry, entryProblems.isEmpty()
                    ? GraphJson.write(graph)
                    : DataResult.error(() -> joined(entryProblems, "; ")));
        });
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        DataResult<JsonObject> written = GraphJson.writeRouter(router, shipped);
        if (written.isError()) {
            return written.map(ignored -> null);
        }
        JsonObject json = written.getOrThrow();
        IslandEntries entries = this.entries(graphs, registries);
        List<GraphProblem> islandProblems = new ArrayList<>(GraphValidator.carrier(json, entries));
        GraphValidator.IslandReach reach = GraphValidator.islands(GraphValidator.islandLists(json, entries), List.of(), entries);
        islandProblems.addAll(reach.problems());
        if (!islandProblems.isEmpty()) {
            return DataResult.error(() -> joined(islandProblems, "\n"));
        }
        return overlay(reach, entries, edited, ops).flatMap(overlay -> GraphInline.router(json, edited)
                .flatMap(inlined -> NoiseRouter.CODEC.parse(ops, inlined).map(decoded -> {
                    VariationLookup lookup = VariationLookup.overlay(overlay.variations(), overlay.lists(), VariationLookup.of(ops));
                    NoiseRouter rebound = decoded.mapAll(IslandFunction.rebind(lookup, override));
                    return new Built(inlined, overlay.json(), withRouter(base, rebound));
                })));
    }

    /**
     * The island variation list of the first island function node of each dimension of the graphs.
     * Done of the Islands screen gives each island biome source that list.
     */
    public Map<ResourceKey<Level>, ResourceKey<IslandVariationList>> islandLists(IslandGraphs graphs, IslandGraphs.EntryLoader loader) {
        Map<ResourceKey<Level>, ResourceKey<IslandVariationList>> lists = new LinkedHashMap<>();
        for (ResourceKey<Level> dimension : graphs.dimensions()) {
            graphs.islandList(dimension, loader).ifPresent(list -> lists.put(dimension, ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, list)));
        }
        return lists;
    }

    /**
     * The dimensions with the list of each island biome source set to the list of its dimension.
     * A dimension without a list stays as it is. So does a generator without an island biome source.
     */
    public WorldDimensions withIslandLists(WorldDimensions dimensions, Map<ResourceKey<Level>, ResourceKey<IslandVariationList>> lists) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        dimensions.dimensions().forEach((stem, levelStem) -> {
            ResourceKey<IslandVariationList> list = lists.get(Registries.levelStemToLevel(stem));
            if (list != null && levelStem.generator() instanceof NoiseBasedChunkGenerator generator
                    && generator.getBiomeSource() instanceof IslandBiomeSource source && !source.variations().equals(list)) {
                stems.put(stem, new LevelStem(levelStem.type(), new NoiseBasedChunkGenerator(source.withVariations(list), generator.generatorSettings())));
            } else {
                stems.put(stem, levelStem);
            }
        });
        return new WorldDimensions(stems);
    }

    /** Decodes each reached list and variation. A variation takes the JSON of each edited entry in the place of each reference to it. */
    private static DataResult<Overlay> overlay(GraphValidator.IslandReach reach, IslandEntries entries, Map<ResourceLocation, DataResult<JsonElement>> edited,
                                               RegistryOps<JsonElement> ops) {
        Map<ResourceKey<IslandVariation>, IslandVariation> variations = new HashMap<>();
        Map<ResourceKey<IslandVariationList>, IslandVariationList> lists = new HashMap<>();
        JsonObject variationJson = new JsonObject();
        JsonObject listJson = new JsonObject();
        for (ResourceLocation id : reach.lists()) {
            JsonObject json = Objects.requireNonNull(entries.list(id));
            DataResult<IslandVariationList> list = IslandVariationList.CODEC.parse(ops, json);
            if (list.isError()) {
                return DataResult.error(() -> GraphProblem.format(id.toString(), "The list does not decode: " + list.error().orElseThrow().message()));
            }
            lists.put(ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, id), list.getOrThrow());
            listJson.add(id.toString(), json);
        }
        for (ResourceLocation id : reach.variations()) {
            JsonObject json = Objects.requireNonNull(entries.variation(id));
            JsonElement function = json.get(DENSITY_FUNCTION);
            if (function != null) {
                DataResult<JsonElement> inlined = GraphInline.function(function, edited);
                if (inlined.isError()) {
                    return DataResult.error(() -> GraphProblem.format(id.toString(), "The density function does not build: " + inlined.error().orElseThrow().message()));
                }
                json.add(DENSITY_FUNCTION, inlined.getOrThrow());
            }
            DataResult<IslandVariation> variation = IslandVariation.CODEC.parse(ops, json);
            if (variation.isError()) {
                return DataResult.error(() -> GraphProblem.format(id.toString(), "The island variation does not decode: " + variation.error().orElseThrow().message()));
            }
            variations.put(ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id), variation.getOrThrow());
            variationJson.add(id.toString(), json);
        }
        JsonObject json = new JsonObject();
        json.add("variations", variationJson);
        json.add("lists", listJson);
        return DataResult.success(new Overlay(Map.copyOf(variations), Map.copyOf(lists), json));
    }

    /** The JSON object of one registry entry, encoded with registry ops. */
    private static <T> DataResult<JsonObject> encode(Optional<? extends Registry<T>> registry, ResourceLocation id, Codec<T> codec, RegistryOps<JsonElement> ops,
                                                     String kind) {
        T value = registry.map(found -> found.get(id)).orElse(null);
        if (value == null) {
            return DataResult.error(() -> "The " + kind + " " + id + " does not exist");
        }
        return codec.encodeStart(ops, value)
                .mapError(message -> "The " + kind + " " + id + " does not encode: " + message)
                .flatMap(json -> json instanceof JsonObject object ? DataResult.success(object) : DataResult.error(() -> "The " + kind + " " + id + " is no JSON object"));
    }

    private static String joined(List<GraphProblem> problems, String separator) {
        return problems.stream().map(GraphProblem::toString).collect(Collectors.joining(separator));
    }

    /** A copy of the generator settings with another router. */
    public static NoiseGeneratorSettings withRouter(NoiseGeneratorSettings settings, NoiseRouter router) {
        return new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                router,
                settings.surfaceRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                settings.aquifersEnabled(),
                settings.oreVeinsEnabled(),
                settings.useLegacyRandomSource()
        );
    }

}
