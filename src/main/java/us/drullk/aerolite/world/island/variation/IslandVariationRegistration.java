package us.drullk.aerolite.world.island.variation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;

/**
 * Registers the two island variation registries. They do not sync to the client.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@Component
public final class IslandVariationRegistration {

    public IslandVariationRegistration() {
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::newRegistries);
    }

    private void newRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(IslandVariationRegistries.ISLAND_VARIATION, IslandVariation.CODEC);
        event.dataPackRegistry(IslandVariationRegistries.ISLAND_VARIATION_LIST, IslandVariationList.CODEC);
    }

}
