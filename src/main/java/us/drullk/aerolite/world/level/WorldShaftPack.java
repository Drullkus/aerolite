package us.drullk.aerolite.world.level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.registries.DataMapLoader;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftDataMaps;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.WeightedVariation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Writes the generator settings of a world as the world data pack {@code aerolite_shafts}. The game enables the pack on the next world load.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class WorldShaftPack {

    /** The folder name of the pack in the world data pack folder. */
    public static final String NAME = "aerolite_shafts";
    /** The namespace of the variations that no shipped entry matches. */
    public static final String VARIATION_NAMESPACE = "aerolite_world";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final DimensionShaftValues values;

    public WorldShaftPack(@Autowired DimensionShaftValues values) {
        this.values = values;
    }

    /** Writes the pack unless the world holds a pack of that name. */
    public void writeOnce(MinecraftServer server) {
        Path target = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(NAME);
        if (Files.exists(target)) {
            return;
        }
        Map<String, JsonElement> files = this.files(server.registryAccess());
        // A partial pack stays outside the data pack folder.
        Path temporary = server.getWorldPath(LevelResource.ROOT).resolve(NAME + ".tmp");
        try {
            deleteTree(temporary);
            for (Map.Entry<String, JsonElement> file : files.entrySet()) {
                Path path = temporary.resolve(file.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(file.getValue()));
            }
            Files.createDirectories(target.getParent());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            Aerolite.LOGGER.info("Wrote the shaft settings of the world generators to the data pack {}. It loads on the next world load.", target);
        } catch (IOException exception) {
            Aerolite.LOGGER.error("Could not write the data pack {}", target, exception);
        }
    }

    /**
     * The files of the pack by path inside the pack. Each dimension whose Aerolite generator has settings for it, and no data map value, gets values.
     * The cloud height comes from the generator of the Overworld.
     */
    public Map<String, JsonElement> files(RegistryAccess registries) {
        Map<ResourceKey<Level>, DimensionSettings> dimensions = new LinkedHashMap<>();
        OptionalInt cloudHeight = OptionalInt.empty();
        Registry<LevelStem> stems = registries.registryOrThrow(Registries.LEVEL_STEM);
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> stem : stems.entrySet()) {
            if (!(stem.getValue().generator() instanceof AeroliteChunkGenerator generator)) {
                continue;
            }
            ResourceKey<Level> dimension = Registries.levelStemToLevel(stem.getKey());
            AeroliteSettings settings = generator.aeroliteSettings(registries);
            if (settings.dimensions().containsKey(dimension) && this.values.get(dimension).isEmpty()) {
                dimensions.put(dimension, settings.dimension(dimension));
            }
            if (stem.getKey().equals(LevelStem.OVERWORLD) || cloudHeight.isEmpty()) {
                cloudHeight = OptionalInt.of(settings.cloudHeight());
            }
        }
        return this.files(registries, dimensions, cloudHeight);
    }

    /**
     * The files of the pack for the given settings.
     * A variation equal to a shipped entry refers to that entry. Any other variation gets an entry in {@value #VARIATION_NAMESPACE}.
     */
    public Map<String, JsonElement> files(HolderLookup.Provider registries, Map<ResourceKey<Level>, DimensionSettings> dimensions, OptionalInt cloudHeight) {
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Map<ShaftVariation, ResourceKey<ShaftVariation>> shipped = new HashMap<>();
        registries.lookupOrThrow(ShaftRegistries.SHAFT_VARIATION).listElements()
                .forEach(holder -> shipped.putIfAbsent(holder.value(), holder.key()));

        Map<String, JsonElement> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", packMeta());
        JsonObject values = new JsonObject();
        dimensions.forEach((dimension, settings) -> {
            List<VariationList.Entry> entries = new ArrayList<>(settings.variations().size());
            Set<ResourceKey<ShaftVariation>> used = new HashSet<>();
            for (int index = 0; index < settings.variations().size(); index++) {
                WeightedVariation variation = settings.variations().get(index);
                ResourceKey<ShaftVariation> key = shipped.get(variation.variation());
                if (key == null || !used.add(key)) {
                    key = ResourceKey.create(ShaftRegistries.SHAFT_VARIATION, ResourceLocation.fromNamespaceAndPath(VARIATION_NAMESPACE,
                            dimension.location().getNamespace() + "/" + dimension.location().getPath() + "/" + index));
                    used.add(key);
                    files.put(entryPath(ShaftRegistries.SHAFT_VARIATION, key.location()), ShaftVariation.CODEC.encodeStart(ops, variation.variation()).getOrThrow());
                }
                entries.add(new VariationList.Entry(key, variation.weight()));
            }
            values.add(dimension.location().toString(), VariationList.CODEC.encodeStart(ops, new VariationList(entries)).getOrThrow());
            files.put(entryPath(ShaftRegistries.SHAFT_PLACEMENT, dimension.location()), ShaftPlacement.CODEC.encodeStart(ops, settings.placement()).getOrThrow());
        });
        files.put(dataMapPath(ShaftDataMaps.SHAFT_VARIATIONS.id(), Registries.LEVEL_STEM.location()), dataMapFile(values));
        cloudHeight.ifPresent(height -> {
            JsonObject cloudValues = new JsonObject();
            cloudValues.add(AeroliteWorldgen.OVERWORLD_TYPE.location().toString(), new JsonPrimitive(height));
            files.put(dataMapPath(ShaftDataMaps.CLOUD_HEIGHT.id(), Registries.DIMENSION_TYPE.location()), dataMapFile(cloudValues));
        });
        return files;
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

    private static JsonObject packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Aerolite shaft settings of this world");
        pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        return meta;
    }

    private static JsonObject dataMapFile(JsonObject values) {
        JsonObject file = new JsonObject();
        file.add("values", values);
        return file;
    }

    private static String entryPath(ResourceKey<? extends Registry<?>> registry, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + Registries.elementsDirPath(registry) + "/" + id.getPath() + ".json";
    }

    private static String dataMapPath(ResourceLocation type, ResourceLocation registry) {
        return "data/" + type.getNamespace() + "/" + DataMapLoader.PATH + "/" + DataMapLoader.getFolderLocation(registry) + "/" + type.getPath() + ".json";
    }

}
