package us.drullk.aerolite.world.island;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Turns the graphs of the island editor into noise generator settings, and loads the graphs from the dimensions of a world.
 * A build writes the router graph, puts the JSON of each edited entry in the place of each reference to it, and decodes the router.
 * The inline serves the preview only. A reference to an entry without an edit resolves through the registry.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
@Component
public final class GraphSettings {

    /** The router JSON of a build and its noise generator settings. */
    public record Built(JsonObject router, NoiseGeneratorSettings settings) {
    }

    public GraphSettings() {
    }

    /** The router graph of each preset dimension with a noise generator. Each output starts on dimension default. */
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
                    .flatMap(json -> GraphJson.readRouter(json, json).map(graph -> Map.entry(graph, json)))
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

    /**
     * The noise generator settings of the dimension with the router of its router graph. The other fields come from {@code base}.
     * A validator problem of the router graph or of a reached edited entry, or a decode error, is the error of the build.
     */
    public DataResult<Built> build(IslandGraphs graphs, ResourceKey<Level> dimension, NoiseGeneratorSettings base, RegistryAccess registries,
                                   Set<ResourceLocation> noises) {
        RouterGraph router = graphs.router(dimension);
        if (router == null) {
            return DataResult.error(() -> "The dimension " + dimension.location() + " has no router graph");
        }
        JsonObject shipped = graphs.shipped(dimension);
        List<GraphProblem> problems = GraphValidator.validate(router, shipped, noises);
        if (!problems.isEmpty()) {
            return DataResult.error(() -> problems.stream().map(GraphProblem::toString).collect(Collectors.joining("\n")));
        }
        Map<ResourceLocation, DataResult<JsonElement>> edited = new LinkedHashMap<>();
        graphs.edited().forEach((entry, graph) -> {
            List<GraphProblem> entryProblems = GraphValidator.validate(graph, noises);
            edited.put(entry, entryProblems.isEmpty()
                    ? GraphJson.write(graph)
                    : DataResult.error(() -> entryProblems.stream().map(GraphProblem::toString).collect(Collectors.joining("; "))));
        });
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return GraphJson.writeRouter(router, shipped)
                .flatMap(json -> GraphInline.router(json, edited))
                .flatMap(json -> NoiseRouter.CODEC.parse(ops, json).map(decoded -> new Built(json, withRouter(base, decoded))));
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
