package us.drullk.aerolite;

import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;

/**
 * The common config of Aerolite. The client and the dedicated server read it before a world exists.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-00_10881e45-2016-40f4-83b8-2c27043cd0f4.md}.
 */
@Component
public final class AeroliteCommonConfig {

    public static final boolean DEFAULT_APPLY_DEFAULT_PILLARS = true;

    private final ModConfigSpec spec;
    private final ModConfigSpec.BooleanValue applyDefaultPillars;

    public AeroliteCommonConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        this.applyDefaultPillars = builder
                .comment("Gives each new world the default pillars. The world creation menu can change or remove them.")
                .translation("aerolite.configuration.applyDefaultPillars")
                .define("applyDefaultPillars", DEFAULT_APPLY_DEFAULT_PILLARS);
        this.spec = builder.build();
    }

    @PostConstruct
    public void register() {
        ModList.get().getModContainerById(Aerolite.MODID).orElseThrow().registerConfig(ModConfig.Type.COMMON, this.spec);
    }

    /** The switch of the default pillars, or the default before the config file loads. */
    public boolean applyDefaultPillars() {
        return this.spec.isLoaded() ? this.applyDefaultPillars.get() : DEFAULT_APPLY_DEFAULT_PILLARS;
    }

}
