package us.drullk.aerolite.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.world.AeroliteWorldgen;

/**
 * Client events of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component(dist = Dist.CLIENT)
public final class AeroliteClient {

    private final PresetSliders sliders;

    public AeroliteClient(@Autowired PresetSliders sliders) {
        this.sliders = sliders;
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPresetEditors);
    }

    /** The customize button of the world creation menu opens the preset screen when the preset is active. */
    private void registerPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(AeroliteWorldgen.PRESET, (parent, context) -> new AerolitePresetScreen(parent, context, this.sliders));
    }

}
