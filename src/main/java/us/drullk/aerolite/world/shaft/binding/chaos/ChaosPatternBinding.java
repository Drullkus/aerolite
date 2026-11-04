package us.drullk.aerolite.world.shaft.binding.chaos;

import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.binding.PatternBinding;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.pattern.chaos.ChaosPattern;
import us.drullk.aerolite.world.shaft.pattern.chaos.ChaosScenes;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

/**
 * The binding of the chaos pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ChaosPatternBinding implements PatternBinding {

    private final ChaosScenes scenes;

    public ChaosPatternBinding(@Autowired ChaosScenes scenes) {
        this.scenes = scenes;
    }

    @Override
    public Class<? extends PatternSettings> settings() {
        return ChaosSettings.class;
    }

    @Override
    public SurfacePattern create(PatternContext context) {
        return new ChaosPattern(context, (ChaosSettings) context.settings(), this.scenes);
    }

}
