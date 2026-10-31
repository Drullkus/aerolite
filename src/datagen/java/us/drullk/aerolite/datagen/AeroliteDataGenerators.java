package us.drullk.aerolite.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;

/**
 * Adds the data providers of Aerolite to the data run.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_06-17_8d424d05-8940-4aa5-872e-7b2bd927cb73.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class AeroliteDataGenerators {

    private final AeroliteWorldgenEntries worldgenEntries;
    private final AeroliteShaftEntries shaftEntries;

    public AeroliteDataGenerators(@Autowired AeroliteWorldgenEntries worldgenEntries, @Autowired AeroliteShaftEntries shaftEntries) {
        this.worldgenEntries = worldgenEntries;
        this.shaftEntries = shaftEntries;
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::gatherData);
    }

    private void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        generator.addProvider(event.includeClient(), new AeroliteLanguageProvider(output));

        AeroliteWorldgenProvider worldgen = generator.addProvider(event.includeServer(), new AeroliteWorldgenProvider(output, event.getLookupProvider(), this.worldgenEntries.builder()));
        generator.addProvider(event.includeServer(), new AeroliteWorldPresetTagsProvider(output, worldgen.getRegistryProvider(), event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new AeroliteDataMapProvider(output, worldgen.getRegistryProvider()));
        generator.addProvider(event.includeServer(), new AeroliteVariationListProvider(output, this.shaftEntries.variationLists()));
    }

}
