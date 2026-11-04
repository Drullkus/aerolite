package us.drullk.aerolite.world.shaft.binding.scales;

import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.binding.PatternBinding;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.pattern.PatternCache;
import us.drullk.aerolite.world.shaft.pattern.scales.ScalesPattern;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

/**
 * The binding of the scales pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ScalesPatternBinding implements PatternBinding {

    private final PatternCache cache;

    public ScalesPatternBinding(@Autowired PatternCache cache) {
        this.cache = cache;
    }

    @Override
    public Class<? extends PatternSettings> settings() {
        return ScalesSettings.class;
    }

    @Override
    public SurfacePattern create(PatternContext context) {
        return new ScalesPattern(context, (ScalesSettings) context.settings(), this.cache);
    }

}
