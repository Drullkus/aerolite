package us.drullk.aerolite.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.client.preview.ItemIconAtlas;
import us.drullk.aerolite.client.preview.PreviewPanel;
import us.drullk.aerolite.client.preview.PreviewViewport;
import us.drullk.aerolite.network.CloudHeightSync;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

/**
 * Client events of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class AeroliteClient {

    private final PresetTools tools;
    private final CloudHeightHolder cloudHeight;
    private final CloudHeightSync cloudHeightSync;

    public AeroliteClient(@Autowired PresetSliders sliders, @Autowired PresetEdits edits, @Autowired BlockSearch blocks,
                          @Autowired ShaftPainter painter, @Autowired ShaftMazes mazes, @Autowired PreviewPanel panel,
                          @Autowired PreviewViewport viewport, @Autowired AeroliteClientConfig config, @Autowired ItemIconAtlas icons,
                          @Autowired CloudHeightHolder cloudHeight, @Autowired CloudHeightSync cloudHeightSync) {
        this.tools = new PresetTools(sliders, edits, blocks, painter, mazes, panel, viewport, config, icons);
        this.cloudHeight = cloudHeight;
        this.cloudHeightSync = cloudHeightSync;
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPresetEditors);
        modEventBus.addListener(this::registerDimensionEffects);
        this.cloudHeightSync.setReceiver(this.cloudHeight::set);
    }

    private void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(AeroliteWorldgen.OVERWORLD_EFFECTS, new AeroliteDimensionEffects(this.cloudHeight));
    }

    /** The customize button of the world creation menu opens the preset screen when the preset is active. */
    private void registerPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(AeroliteWorldgen.PRESET, (parent, context) -> new AerolitePresetScreen(parent, context, this.tools));
    }

}
