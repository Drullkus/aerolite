package us.drullk.aerolite.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.IslandPack;
import us.drullk.aerolite.world.level.WorldShaftPack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Picks the Aerolite preset of a recreated world in the create world screen.
 * The recreate copies the world data packs into the temporary data pack folder of the screen.
 * A folder with the island pack gives the Islands preset. A folder with only the world pillar pack gives the Aerolite Void preset.
 * Any other folder keeps the world type of the screen. The detection runs once per screen, at its first init.
 * A screen that already shows that preset keeps its world type, so it keeps its dimensions.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
@Component(dist = Dist.CLIENT)
public final class RecreatePreset {

    /** The last create world screen that ran the detection. */
    @Nullable
    private CreateWorldScreen detected;

    public RecreatePreset() {
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onInit);
    }

    /** The preset for the folder names of a temporary data pack folder, or empty to keep the world type. */
    public Optional<ResourceKey<WorldPreset>> preset(Set<String> folders) {
        if (folders.contains(IslandPack.NAME)) {
            return Optional.of(AeroliteWorldgen.ISLANDS_PRESET);
        }
        if (folders.contains(WorldShaftPack.NAME)) {
            return Optional.of(AeroliteWorldgen.PRESET);
        }
        return Optional.empty();
    }

    private void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen) || screen == this.detected) {
            return;
        }
        this.detected = screen;
        Path folder = screen.tempDataPackDir;
        if (folder == null) {
            return;
        }
        this.preset(folders(folder)).ifPresent(key -> {
            WorldCreationUiState state = screen.getUiState();
            Holder<WorldPreset> shown = state.getWorldType().preset();
            if (shown != null && shown.is(key)) {
                return;
            }
            state.getNormalPresetList().stream()
                    .filter(entry -> entry.preset() != null && entry.preset().is(key))
                    .findFirst()
                    .ifPresentOrElse(state::setWorldType,
                            () -> Aerolite.LOGGER.warn("The preset {} of the recreated world is not in the world type list", key.location()));
        });
    }

    /** The names of the folders in a data pack folder. A folder that does not list gives no name. */
    private static Set<String> folders(Path folder) {
        try (Stream<Path> children = Files.list(folder)) {
            return children.filter(Files::isDirectory).map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException exception) {
            Aerolite.LOGGER.warn("The temporary data pack folder {} does not list", folder, exception);
            return Set.of();
        }
    }

}
