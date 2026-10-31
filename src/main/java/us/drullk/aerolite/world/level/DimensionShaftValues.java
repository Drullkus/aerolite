package us.drullk.aerolite.world.level;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.WithConditions;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DataMapLoader;
import net.neoforged.neoforge.registries.datamaps.DataMapEntry;
import net.neoforged.neoforge.registries.datamaps.DataMapFile;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.ShaftDataMaps;
import us.drullk.aerolite.world.VariationList;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the files of the data map {@code aerolite:shaft_variations} by dimension id, with the merge rules of the NeoForge data map loader.
 * The world creation screen applies data maps to a dimension registry without the dimensions of the new world.
 * So the values of the level stem registry are empty in the first session of a new world, and this reader keeps them by id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class DimensionShaftValues {

    /** The data map file path of {@code aerolite:shaft_variations}. */
    public static final ResourceLocation FILE = ShaftDataMaps.SHAFT_VARIATIONS.id().withPath(path -> DataMapLoader.PATH + "/"
            + DataMapLoader.getFolderLocation(Registries.LEVEL_STEM.location()) + "/" + path + ".json");

    private volatile Map<ResourceKey<LevelStem>, VariationList> values = Map.of();

    public DimensionShaftValues() {
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::addReloadListener);
    }

    /** The data map value of a dimension, if one exists. */
    public Optional<VariationList> get(ResourceKey<Level> dimension) {
        return Optional.ofNullable(this.values.get(ResourceKey.create(Registries.LEVEL_STEM, dimension.location())));
    }

    private void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    /**
     * Merges the files from the lowest pack to the highest pack.
     * A file with {@code replace} clears the earlier values. A later value replaces an earlier one. A removal drops a value.
     * A tag cannot resolve without the dimensions, so a tag entry is skipped.
     */
    public static Map<ResourceKey<LevelStem>, VariationList> merge(List<DataMapFile<VariationList, LevelStem>> files) {
        Map<ResourceKey<LevelStem>, VariationList> merged = new LinkedHashMap<>();
        for (DataMapFile<VariationList, LevelStem> file : files) {
            if (file.replace()) {
                merged.clear();
            }
            for (Map.Entry<Either<TagKey<LevelStem>, ResourceKey<LevelStem>>, Optional<WithConditions<DataMapEntry<VariationList>>>> value : file.values().entrySet()) {
                Optional<ResourceKey<LevelStem>> key = value.getKey().right();
                if (key.isEmpty()) {
                    Aerolite.LOGGER.warn("Skipped a tag in the data map {}", ShaftDataMaps.SHAFT_VARIATIONS.id());
                    continue;
                }
                value.getValue().ifPresent(entry -> merged.put(key.get(), entry.carrier().value()));
            }
            for (DataMapEntry.Removal<VariationList, LevelStem> removal : file.removals()) {
                removal.key().right().ifPresent(merged::remove);
            }
        }
        return Map.copyOf(merged);
    }

    /** Decodes the files of the data map. A file that fails to decode is skipped. */
    public static List<DataMapFile<VariationList, LevelStem>> read(DynamicOps<JsonElement> ops, List<Resource> resources) {
        Codec<DataMapFile<VariationList, LevelStem>> codec = DataMapFile.codec(Registries.LEVEL_STEM, ShaftDataMaps.SHAFT_VARIATIONS);
        List<DataMapFile<VariationList, LevelStem>> files = new ArrayList<>(resources.size());
        for (Resource resource : resources) {
            try (Reader reader = resource.openAsReader()) {
                files.add(codec.parse(ops, JsonParser.parseReader(reader)).getOrThrow());
            } catch (Exception exception) {
                Aerolite.LOGGER.error("Could not read the data map {} from {}", ShaftDataMaps.SHAFT_VARIATIONS.id(), resource.sourcePackId(), exception);
            }
        }
        return files;
    }

    private final class Listener extends SimplePreparableReloadListener<Map<ResourceKey<LevelStem>, VariationList>> {

        @Override
        protected Map<ResourceKey<LevelStem>, VariationList> prepare(ResourceManager manager, ProfilerFiller profiler) {
            return merge(read(this.makeConditionalOps(), manager.getResourceStack(FILE)));
        }

        @Override
        protected void apply(Map<ResourceKey<LevelStem>, VariationList> prepared, ResourceManager manager, ProfilerFiller profiler) {
            DimensionShaftValues.this.values = prepared;
        }

    }

}
