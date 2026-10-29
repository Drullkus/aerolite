package us.drullk.aerolite.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;

/**
 * Client events of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
@EventBusSubscriber(modid = Aerolite.MODID, value = Dist.CLIENT)
public final class AeroliteClient {

    private AeroliteClient() {
    }

    /** The customize button of the world creation menu opens the preset screen when the preset is active. */
    @SubscribeEvent
    public static void registerPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(AeroliteWorldgen.PRESET, AerolitePresetScreen::new);
    }

}
