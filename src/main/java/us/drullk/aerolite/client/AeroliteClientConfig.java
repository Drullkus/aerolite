package us.drullk.aerolite.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;

/**
 * The client config of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class AeroliteClientConfig {

    public static final int MIN_PANEL_WIDTH = 240;
    public static final int MAX_PANEL_WIDTH = 600;
    public static final int DEFAULT_PANEL_WIDTH = 360;

    private final ModConfigSpec spec;
    private final ModConfigSpec.IntValue panelWidth;

    public AeroliteClientConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        this.panelWidth = builder
                .comment("The width of the preview panel in ImGui units")
                .translation("aerolite.configuration.panelWidth")
                .defineInRange("panelWidth", DEFAULT_PANEL_WIDTH, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
        this.spec = builder.build();
    }

    @PostConstruct
    public void register() {
        ModList.get().getModContainerById(Aerolite.MODID).orElseThrow().registerConfig(ModConfig.Type.CLIENT, this.spec);
    }

    /** The panel width, or the default before the config file loads. */
    public int panelWidth() {
        return this.spec.isLoaded() ? this.panelWidth.get() : DEFAULT_PANEL_WIDTH;
    }

    /** Writes the panel width to the config file. */
    public void setPanelWidth(int width) {
        if (!this.spec.isLoaded()) {
            return;
        }
        this.panelWidth.set(Math.clamp(width, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH));
        this.panelWidth.save();
    }

}
