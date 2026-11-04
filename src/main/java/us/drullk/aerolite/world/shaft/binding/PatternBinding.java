package us.drullk.aerolite.world.shaft.binding;

import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

/**
 * Makes the surface pattern of one pattern settings class.
 * Each binding is an unnamed bean. {@link PatternBindings} collects them.
 *
 * <p>Every binding class lives in a subpackage of {@code us.drullk.aerolite.world.shaft.binding}, one subpackage per pattern.
 * No other class and no test class lives under that package.
 * The directory scan of the collector initializes every class there during mod construction.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public interface PatternBinding {

    /** The settings class that this binding serves. */
    Class<? extends PatternSettings> settings();

    /** The pattern of one pillar. The settings of the context are an instance of {@link #settings()}. */
    SurfacePattern create(PatternContext context);

}
