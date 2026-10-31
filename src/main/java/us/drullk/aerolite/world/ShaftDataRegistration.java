package us.drullk.aerolite.world;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;

/**
 * Registers the shaft data registries and the data map types. The registries do not sync to the client.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class ShaftDataRegistration {

    public ShaftDataRegistration() {
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::newRegistries);
        modEventBus.addListener(this::dataMapTypes);
    }

    private void newRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(ShaftRegistries.SHAFT_VARIATION, ShaftVariation.CODEC);
        event.dataPackRegistry(ShaftRegistries.SHAFT_PLACEMENT, ShaftPlacement.CODEC);
        event.dataPackRegistry(ShaftRegistries.VARIATION_LIST, VariationList.CODEC);
    }

    private void dataMapTypes(RegisterDataMapTypesEvent event) {
        event.register(ShaftDataMaps.SHAFT_VARIATIONS);
        event.register(ShaftDataMaps.CLOUD_HEIGHT);
    }

}
