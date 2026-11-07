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
import us.drullk.aerolite.world.VoidPresetBiomes;
import us.drullk.aerolite.world.island.IslandGenerators;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

/**
 * Client events of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@Component(dist = Dist.CLIENT)
public final class AeroliteClient {

    private final PresetTools tools;
    private final CloudHeightHolder cloudHeight;
    private final CloudHeightSync cloudHeightSync;
    private final VoidPresetBiomes presetBiomes;
    private final IslandGenerators islandGenerators;

    public AeroliteClient(@Autowired PresetSliders sliders, @Autowired PresetEdits edits, @Autowired BlockSearch blocks,
                          @Autowired ShaftPainter painter, @Autowired ShaftMazes mazes, @Autowired PreviewPanel panel,
                          @Autowired PreviewViewport viewport, @Autowired AeroliteClientConfig config, @Autowired ItemIconAtlas icons,
                          @Autowired CloudHeightHolder cloudHeight, @Autowired CloudHeightSync cloudHeightSync, @Autowired SettingsJson json,
                          @Autowired ShaftSettingsHolder holder, @Autowired VoidPresetBiomes presetBiomes,
                          @Autowired ProviderText providerText, @Autowired IslandGenerators islandGenerators) {
        this.tools = new PresetTools(sliders, edits, blocks, painter, mazes, panel, viewport, config, icons, json, holder, providerText);
        this.cloudHeight = cloudHeight;
        this.cloudHeightSync = cloudHeightSync;
        this.presetBiomes = presetBiomes;
        this.islandGenerators = islandGenerators;
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

    /** The Customize button of the world creation menu opens the Aerolite Void screen or the Aerolite Islands screen of the active preset. */
    private void registerPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(AeroliteWorldgen.PRESET, (parent, context) -> new VoidPresetScreen(parent, context, this.tools, this.presetBiomes));
        event.register(AeroliteWorldgen.ISLANDS_PRESET, (parent, context) -> new IslandsPresetScreen(parent, context, this.tools, this.presetBiomes, this.islandGenerators));
    }

    /** The components of the screens of the world creation flow. */
    public PresetTools tools() {
        return this.tools;
    }

}
