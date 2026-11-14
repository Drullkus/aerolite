package us.drullk.aerolite.world.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.Level;
import tamaized.beanification.Component;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.FunctionNodeGraph;
import us.drullk.aerolite.world.island.graph.GraphJson;
import us.drullk.aerolite.world.island.graph.GraphLayout;
import us.drullk.aerolite.world.island.graph.GraphProblem;
import us.drullk.aerolite.world.island.graph.GraphValidator;
import us.drullk.aerolite.world.island.graph.IslandEntries;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.graph.Sidecar;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the graphs of the island editor as the world data pack {@value #NAME}, and reads such a pack back into the graphs.
 * A changed router graph writes the noise settings entry of its dimension under the id that the generator names.
 * An edited function graph writes its density function entry under its own id. So the pack overrides the shipped entry.
 * An edited function graph whose saved JSON equals its start JSON writes no file.
 * Each written graph gets a sidecar under {@value #SIDECAR}. A changed router graph also keeps its dimension default router there.
 * Each written entry with a start JSON also keeps that start JSON there. So a restored entry compares against the start of the first flow.
 * Each changed island variation and list goes to its registry folder under its own id. Its start JSON goes along.
 * The game reads no registry from {@value #SIDECAR}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@Component
public final class IslandPack {

    /** The folder name of the pack in a data pack folder. */
    public static final String NAME = "aerolite_islands";
    /** The id of the pack in a pack repository over a data pack folder. */
    public static final String PACK_ID = "file/" + NAME;
    /** The data folder of the sidecars. It is no registry folder. */
    public static final String SIDECAR = "aerolite_sidecar";
    public static final String PACK_META = "pack.mcmeta";

    private static final String NOISE_ROUTER = "noise_router";
    private static final String ROUTER_SIDECAR = "noise_router";
    private static final String ENTRY_SIDECAR = "density_function";
    private static final String DIMENSION_DEFAULT = "dimension_default";
    private static final String START_JSON = "start_json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern DENSITY_FUNCTION_FILE = Pattern.compile(
            "data/([^/]+)/" + Pattern.quote(Registries.elementsDirPath(Registries.DENSITY_FUNCTION)) + "/(.+)\\.json");

    /** The noise settings entry that the generator of a dimension names, and its JSON. */
    public record NoiseSettingsEntry(ResourceLocation id, JsonObject json) {
    }

    public IslandPack() {
    }

    /**
     * The files of the pack by path inside the pack. The map is empty when no graph differs from its start.
     * A router graph differs when its saved router differs from its dimension default router.
     * An edited function graph differs when its saved JSON differs from its start JSON. An entry without a start JSON differs.
     * A validator problem, a save error or a changed router graph without a noise settings entry is the error of the result.
     */
    public DataResult<Map<String, JsonElement>> files(IslandGraphs graphs, Map<ResourceKey<Level>, NoiseSettingsEntry> settings,
                                                      @Nullable Set<ResourceLocation> noises) {
        return this.files(graphs, settings, noises, null);
    }

    /**
     * The files of the pack like {@link #files(IslandGraphs, Map, Set)}, with the island variations and lists.
     * An edited variation or list goes to its registry folder under its own id. A JSON equal to its start JSON writes no file.
     * Its start JSON goes under {@value #SIDECAR}. With entries, a problem of an edited variation or list is the error of the result.
     *
     * @param entries the entries that the island checks read, or null to skip the island checks
     */
    public DataResult<Map<String, JsonElement>> files(IslandGraphs graphs, Map<ResourceKey<Level>, NoiseSettingsEntry> settings,
                                                      @Nullable Set<ResourceLocation> noises, @Nullable IslandEntries entries) {
        Map<String, JsonElement> files = new LinkedHashMap<>();
        if (entries != null) {
            List<GraphProblem> problems = GraphValidator.islands(graphs.editedLists().keySet(), graphs.editedVariations().keySet(), entries).problems();
            if (!problems.isEmpty()) {
                return DataResult.error(() -> "The island variations and lists have problems: " + joined(problems));
            }
        }
        for (ResourceKey<Level> dimension : graphs.dimensions()) {
            RouterGraph router = graphs.router(dimension);
            if (router == null) {
                continue;
            }
            JsonObject shipped = graphs.shipped(dimension);
            List<GraphProblem> problems = GraphValidator.validate(router, shipped, noises);
            if (!problems.isEmpty()) {
                return DataResult.error(() -> "The router graph of " + dimension.location() + " has problems: " + joined(problems));
            }
            DataResult<JsonObject> written = GraphJson.writeRouter(router, shipped);
            if (written.isError()) {
                return written.map(ignored -> Map.<String, JsonElement>of());
            }
            JsonObject json = written.getOrThrow();
            if (json.equals(shipped)) {
                continue;
            }
            NoiseSettingsEntry entry = settings.get(dimension);
            if (entry == null) {
                return DataResult.error(() -> "The generator of " + dimension.location() + " names no noise settings entry");
            }
            JsonObject file = entry.json().deepCopy();
            file.add(NOISE_ROUTER, json);
            files.put(entryPath(Registries.NOISE_SETTINGS, entry.id()), file);
            files.put(routerSidecarPath(dimension), sidecarJson(graphs.sidecar(new IslandGraphs.GraphId.Router(dimension), router)));
            files.put(dimensionDefaultPath(dimension), shipped);
        }
        for (Map.Entry<ResourceLocation, FunctionGraph> edited : graphs.edited().entrySet()) {
            ResourceLocation id = edited.getKey();
            List<GraphProblem> problems = GraphValidator.validate(edited.getValue(), noises);
            if (!problems.isEmpty()) {
                return DataResult.error(() -> "The entry " + id + " has problems: " + joined(problems));
            }
            DataResult<JsonElement> written = GraphJson.write(edited.getValue());
            if (written.isError()) {
                return written.map(ignored -> Map.<String, JsonElement>of());
            }
            JsonElement json = written.getOrThrow();
            JsonElement start = graphs.startJson(id);
            if (json.equals(start)) {
                continue;
            }
            files.put(entryPath(Registries.DENSITY_FUNCTION, id), json);
            files.put(entrySidecarPath(id), sidecarJson(graphs.sidecar(new IslandGraphs.GraphId.Entry(id), edited.getValue())));
            if (start != null) {
                files.put(startJsonPath(id), start);
            }
        }
        changed(files, graphs.editedVariations(), graphs::variationStartJson, IslandVariationRegistries.ISLAND_VARIATION);
        changed(files, graphs.editedLists(), graphs::listStartJson, IslandVariationRegistries.ISLAND_VARIATION_LIST);
        if (files.isEmpty()) {
            return DataResult.success(Map.of());
        }
        Map<String, JsonElement> pack = new LinkedHashMap<>();
        pack.put(PACK_META, packMeta());
        pack.putAll(files);
        return DataResult.success(pack);
    }

    /**
     * Puts the content of a pack into graphs that start from registries with that pack.
     * A dimension with a dimension default router in the pack loads its router graph again, with that router as its dimension default.
     * Each output of that router graph links to the function nodes of the router of the pack.
     * Each density function entry of the pack becomes an edited function graph. Each sidecar gives the positions of its graph.
     * Each island variation and list of the pack becomes an edit of the flow. Its start JSON comes along.
     * A router sidecar also gives the position of the router box. The start JSON file of an entry becomes its start JSON.
     */
    public void restore(Map<String, JsonElement> files, IslandGraphs graphs) {
        for (ResourceKey<Level> dimension : graphs.dimensions()) {
            JsonElement base = files.get(dimensionDefaultPath(dimension));
            if (base == null || !base.isJsonObject()) {
                continue;
            }
            JsonObject current = graphs.shipped(dimension);
            DataResult<RouterGraph> router = GraphJson.readRouter(current);
            router.ifError(error -> Aerolite.LOGGER.warn("The island pack router of {} does not load: {}", dimension.location(), error.message()));
            router.ifSuccess(graph -> {
                graphs.putRouter(dimension, graph, base.getAsJsonObject());
                applySidecar(files.get(routerSidecarPath(dimension)), graph, graphs.positions(new IslandGraphs.GraphId.Router(dimension)), true);
            });
        }
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            Matcher matcher = DENSITY_FUNCTION_FILE.matcher(file.getKey());
            ResourceLocation id = matcher.matches() ? ResourceLocation.tryBuild(matcher.group(1), matcher.group(2)) : null;
            if (id == null) {
                continue;
            }
            DataResult<FunctionGraph> graph = GraphJson.readFunction(file.getValue());
            graph.ifError(error -> Aerolite.LOGGER.warn("The island pack entry {} does not load: {}", id, error.message()));
            graph.ifSuccess(loaded -> {
                graphs.restoreEdited(id, loaded);
                JsonElement start = files.get(startJsonPath(id));
                if (start != null) {
                    graphs.setStartJson(id, start);
                }
                applySidecar(files.get(entrySidecarPath(id)), loaded, graphs.positions(new IslandGraphs.GraphId.Entry(id)), false);
            });
        }
        restoreEntries(files, IslandVariationRegistries.ISLAND_VARIATION, graphs::restoreVariation, graphs::setVariationStartJson);
        restoreEntries(files, IslandVariationRegistries.ISLAND_VARIATION_LIST, graphs::restoreList, graphs::setListStartJson);
    }

    /** Puts each entry whose JSON differs from its start JSON into the files, with its start JSON. */
    private static void changed(Map<String, JsonElement> files, Map<ResourceLocation, JsonObject> edited, Function<ResourceLocation, JsonObject> start,
                                ResourceKey<? extends Registry<?>> registry) {
        edited.forEach((id, json) -> {
            JsonObject startJson = start.apply(id);
            if (json.equals(startJson)) {
                return;
            }
            files.put(entryPath(registry, id), json);
            if (startJson != null) {
                files.put(startJsonPath(registry, id), startJson);
            }
        });
    }

    /** Puts each JSON object of the registry folder of the pack into the graphs, with its start JSON. */
    private static void restoreEntries(Map<String, JsonElement> files, ResourceKey<? extends Registry<?>> registry, BiConsumer<ResourceLocation, JsonObject> restore,
                                       BiConsumer<ResourceLocation, JsonObject> setStart) {
        Pattern pattern = Pattern.compile("data/([^/]+)/" + Pattern.quote(Registries.elementsDirPath(registry)) + "/(.+)\\.json");
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            Matcher matcher = pattern.matcher(file.getKey());
            ResourceLocation id = matcher.matches() ? ResourceLocation.tryBuild(matcher.group(1), matcher.group(2)) : null;
            if (id == null) {
                continue;
            }
            if (!(file.getValue() instanceof JsonObject json)) {
                Aerolite.LOGGER.warn("The island pack entry {} of {} is no JSON object", id, registry.location());
                continue;
            }
            restore.accept(id, json);
            if (files.get(startJsonPath(registry, id)) instanceof JsonObject start) {
                setStart.accept(id, start);
            }
        }
    }

    /** The JSON files of a pack folder by path inside the pack. A missing folder gives an empty map. A bad file is left out. */
    public Map<String, JsonElement> read(Path pack) {
        Map<String, JsonElement> files = new TreeMap<>();
        if (!Files.isDirectory(pack)) {
            return files;
        }
        try (Stream<Path> paths = Files.walk(pack)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = pack.relativize(path).toString().replace('\\', '/');
                try {
                    files.put(relative, JsonParser.parseString(Files.readString(path)));
                } catch (IOException | JsonParseException exception) {
                    Aerolite.LOGGER.warn("The island pack file {} does not read", path, exception);
                }
            }
        } catch (IOException exception) {
            Aerolite.LOGGER.warn("The island pack {} does not list", pack, exception);
        }
        return files;
    }

    /**
     * Writes the files as the pack {@value #NAME} in the data pack folder. An empty map removes the pack.
     * The pack first goes to a temporary folder next to the data pack folder, then moves into place. Gives true when the folder changed.
     * A failed write removes the temporary folder.
     */
    public boolean write(Path dataPacks, Map<String, JsonElement> files) throws IOException {
        Path target = dataPacks.resolve(NAME);
        if (files.isEmpty()) {
            boolean existed = Files.exists(target);
            deleteTree(target);
            return existed;
        }
        // A partial pack stays outside the data pack folder.
        Path temporary = dataPacks.resolveSibling(dataPacks.getFileName() + "-" + NAME + ".tmp");
        try {
            deleteTree(temporary);
            for (Map.Entry<String, JsonElement> file : files.entrySet()) {
                Path path = temporary.resolve(file.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(file.getValue()));
            }
            deleteTree(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            try {
                deleteTree(temporary);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
        return true;
    }

    /** The path inside the pack of a registry entry. */
    public static String entryPath(ResourceKey<? extends Registry<?>> registry, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + Registries.elementsDirPath(registry) + "/" + id.getPath() + ".json";
    }

    /** The path inside the pack of the sidecar of a density function entry. */
    public static String entrySidecarPath(ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + SIDECAR + "/" + ENTRY_SIDECAR + "/" + id.getPath() + ".json";
    }

    /** The path inside the pack of the start JSON of a density function entry. */
    public static String startJsonPath(ResourceLocation id) {
        return "data/" + Aerolite.MODID + "/" + SIDECAR + "/" + START_JSON + "/" + id.getNamespace() + "/" + id.getPath() + ".json";
    }

    /** The path inside the pack of the start JSON of an island variation or an island variation list. */
    public static String startJsonPath(ResourceKey<? extends Registry<?>> registry, ResourceLocation id) {
        return "data/" + Aerolite.MODID + "/" + SIDECAR + "/" + START_JSON + "/" + registry.location().getPath() + "/" + id.getNamespace() + "/" + id.getPath() + ".json";
    }

    /** The path inside the pack of the sidecar of the router graph of a dimension. */
    public static String routerSidecarPath(ResourceKey<Level> dimension) {
        return "data/" + Aerolite.MODID + "/" + SIDECAR + "/" + ROUTER_SIDECAR + "/" + dimension.location().getPath() + ".json";
    }

    /** The path inside the pack of the dimension default router of a dimension. */
    public static String dimensionDefaultPath(ResourceKey<Level> dimension) {
        return "data/" + Aerolite.MODID + "/" + SIDECAR + "/" + DIMENSION_DEFAULT + "/" + dimension.location().getPath() + ".json";
    }

    /** Puts the positions of a sidecar into a graph. Only a router graph takes the field {@code router}. */
    private static void applySidecar(@Nullable JsonElement json, FunctionNodeGraph graph, Map<Integer, Sidecar.Position> positions, boolean router) {
        if (json == null) {
            return;
        }
        Sidecar.CODEC.parse(JsonOps.INSTANCE, json)
                .ifError(error -> Aerolite.LOGGER.warn("An island pack sidecar does not load: {}", error.message()))
                .ifSuccess(sidecar -> {
                    positions.clear();
                    positions.putAll(sidecar.apply(graph));
                    if (router) {
                        sidecar.router().ifPresent(box -> positions.put(GraphLayout.BOX, box));
                    }
                });
    }

    private static JsonElement sidecarJson(Sidecar sidecar) {
        return Sidecar.CODEC.encodeStart(JsonOps.INSTANCE, sidecar).getOrThrow();
    }

    private static String joined(List<GraphProblem> problems) {
        return problems.stream().map(GraphProblem::toString).collect(Collectors.joining("; "));
    }

    private static JsonObject packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Aerolite island graphs of this world");
        pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        return meta;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

}
