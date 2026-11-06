package us.drullk.aerolite.world.level;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.AeroliteCommonConfig;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.ShaftDataMaps;
import us.drullk.aerolite.world.ShaftLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offers the default pillars to a new world of a dedicated server, because a dedicated server has no world creation menu.
 * The same claim and the same world pillar pack as the menu settings then apply.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-00_10881e45-2016-40f4-83b8-2c27043cd0f4.md}.
 */
@Component
public final class DedicatedDefaultPillars {

    private final AeroliteCommonConfig config;
    private final PendingShaftSettings pending;

    public DedicatedDefaultPillars(@Autowired AeroliteCommonConfig config, @Autowired PendingShaftSettings pending) {
        this.config = config;
        this.pending = pending;
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, this::onAboutToStart);
    }

    /** This listener runs before the claim of {@link PendingShaftSettings}. */
    private void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (server.isDedicatedServer() && this.config.applyDefaultPillars() && !server.getWorldData().overworldData().isInitialized()) {
            AeroliteSettings defaults = defaults(server.registryAccess());
            if (!defaults.dimensions().isEmpty()) {
                this.pending.offer(defaults);
            }
        }
    }

    /** The default pillars of each dimension of the world. The data map keeps the cloud height. */
    private static AeroliteSettings defaults(RegistryAccess registries) {
        Registry<DimensionType> types = registries.registryOrThrow(Registries.DIMENSION_TYPE);
        Integer cloudHeight = types.getData(ShaftDataMaps.CLOUD_HEIGHT, AeroliteWorldgen.OVERWORLD_TYPE);
        AeroliteSettings blank = new AeroliteSettings(cloudHeight == null ? AeroliteSettings.DEFAULT_CLOUD_HEIGHT : cloudHeight, ImmutableMap.of());
        List<ResourceKey<Level>> dimensions = new ArrayList<>();
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> stem : registries.registryOrThrow(Registries.LEVEL_STEM).entrySet()) {
            // An Aerolite generator gives its own pillar settings.
            if (!(stem.getValue().generator() instanceof AeroliteChunkGenerator)) {
                dimensions.add(Registries.levelStemToLevel(stem.getKey()));
            }
        }
        return ShaftLookup.of(registries).withDefaults(blank, dimensions);
    }

}
