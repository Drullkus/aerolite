package us.drullk.aerolite.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import us.drullk.aerolite.Aerolite;

/**
 * Adds the data providers of Aerolite to the data run.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_06-17_8d424d05-8940-4aa5-872e-7b2bd927cb73.md}.
 */
@EventBusSubscriber(modid = Aerolite.MODID)
public final class AeroliteDataGenerators {

    private AeroliteDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        generator.addProvider(event.includeClient(), new AeroliteLanguageProvider(output));
    }

}
