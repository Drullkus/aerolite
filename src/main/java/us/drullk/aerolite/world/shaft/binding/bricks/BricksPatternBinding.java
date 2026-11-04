package us.drullk.aerolite.world.shaft.binding.bricks;

import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.binding.PatternBinding;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.pattern.PatternCache;
import us.drullk.aerolite.world.shaft.pattern.bricks.BricksPattern;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

/**
 * The binding of the bricks pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class BricksPatternBinding implements PatternBinding {

    private final PatternCache cache;

    public BricksPatternBinding(@Autowired PatternCache cache) {
        this.cache = cache;
    }

    @Override
    public Class<? extends PatternSettings> settings() {
        return BricksSettings.class;
    }

    @Override
    public SurfacePattern create(PatternContext context) {
        return new BricksPattern(context, (BricksSettings) context.settings(), this.cache);
    }

}
