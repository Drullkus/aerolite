package us.drullk.aerolite.client;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.VoidPresetBiomes;
import us.drullk.aerolite.world.island.GraphSettings;
import us.drullk.aerolite.world.island.IslandPack;
import us.drullk.aerolite.world.island.graph.IslandGraphs;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the island pack into the temporary data pack folder of a create world screen and reloads its world creation context.
 * The reload re-reads the dimensions against the new registries, so the generators name the noise settings of the pack.
 * The world creation copies that folder into the world, so the first session already uses the pack.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
@Component(dist = Dist.CLIENT)
public final class IslandPackApplier {

    private final IslandPack pack;
    private final GraphSettings graphSettings;

    public IslandPackApplier(@Autowired IslandPack pack, @Autowired GraphSettings graphSettings) {
        this.pack = pack;
        this.graphSettings = graphSettings;
    }

    /**
     * Writes or removes the pack for the graphs, then reloads the context when the folder changed.
     * A pack that does not build keeps the folder as it is and logs the error.
     */
    public void apply(CreateWorldScreen screen, IslandGraphs graphs) {
        WorldCreationContext context = screen.getUiState().getSettings();
        Map<String, JsonElement> files = this.pack.files(graphs, this.noiseSettings(context), this.graphSettings.noises(context.worldgenLoadContext()))
                .ifError(error -> Aerolite.LOGGER.error("The island pack does not build: {}", error.message()))
                .result().orElse(null);
        if (files == null) {
            return;
        }
        WorldDataConfiguration current = context.dataConfiguration();
        Pair<Path, PackRepository> selection = screen.getDataPackSelectionSettings(current);
        if (selection == null) {
            return;
        }
        Path folder = selection.getFirst();
        Map<String, JsonElement> previous = this.pack.read(folder.resolve(IslandPack.NAME));
        boolean changed;
        try {
            changed = this.pack.write(folder, files);
        } catch (IOException exception) {
            Aerolite.LOGGER.error("The write of the island pack to {} failed", folder, exception);
            return;
        }
        if (!changed) {
            return;
        }
        Aerolite.LOGGER.info("{} the island pack in {}. The world creation context reloads.", files.isEmpty() ? "Removed" : "Wrote", folder);
        PackRepository repository = selection.getSecond();
        repository.reload();
        List<String> enabled = new ArrayList<>(current.dataPacks().getEnabled());
        enabled.remove(IslandPack.PACK_ID);
        if (!files.isEmpty()) {
            // The last enabled pack wins, so the island pack overrides the mod data.
            enabled.add(IslandPack.PACK_ID);
        }
        List<String> disabled = repository.getAvailableIds().stream().filter(id -> !enabled.contains(id)).toList();
        WorldDataConfiguration configuration = new WorldDataConfiguration(new DataPackConfig(enabled, disabled), current.enabledFeatures());
        screen.applyNewPackConfig(repository, configuration, ignored -> this.onFailure(screen, folder, previous));
    }

    /** The vanilla failure screen calls back here. The folder gets the pack of the unchanged context again. */
    private void onFailure(CreateWorldScreen screen, Path folder, Map<String, JsonElement> previous) {
        Aerolite.LOGGER.error("The world creation context did not load the island pack. The island pack in {} returns to its state before Done", folder);
        try {
            this.pack.write(folder, previous);
        } catch (IOException exception) {
            Aerolite.LOGGER.error("The return of the island pack in {} failed", folder, exception);
        }
        Minecraft.getInstance().setScreen(screen);
    }

    /** The noise settings entry that the generator of each preset dimension names, encoded with the registries of the context. */
    private Map<ResourceKey<Level>, IslandPack.NoiseSettingsEntry> noiseSettings(WorldCreationContext context) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.worldgenLoadContext());
        Map<ResourceKey<Level>, IslandPack.NoiseSettingsEntry> entries = new LinkedHashMap<>();
        for (ResourceKey<LevelStem> stem : VoidPresetBiomes.DIMENSIONS) {
            LevelStem levelStem = context.selectedDimensions().dimensions().get(stem);
            if (levelStem == null || !(levelStem.generator() instanceof NoiseBasedChunkGenerator generator)) {
                continue;
            }
            generator.generatorSettings().unwrapKey().ifPresent(key -> NoiseGeneratorSettings.DIRECT_CODEC
                    .encodeStart(ops, generator.generatorSettings().value())
                    .ifError(error -> Aerolite.LOGGER.error("The noise settings {} do not encode: {}", key.location(), error.message()))
                    .ifSuccess(json -> entries.put(Registries.levelStemToLevel(stem), new IslandPack.NoiseSettingsEntry(key.location(), json.getAsJsonObject()))));
        }
        return entries;
    }

    /** The island pack in the temporary data pack folder of the screen, or an empty map. */
    public Map<String, JsonElement> existing(@Nullable CreateWorldScreen screen) {
        Path folder = screen == null ? null : screen.tempDataPackDir;
        if (folder == null || !Files.isDirectory(folder.resolve(IslandPack.NAME))) {
            return Map.of();
        }
        return this.pack.read(folder.resolve(IslandPack.NAME));
    }

}
