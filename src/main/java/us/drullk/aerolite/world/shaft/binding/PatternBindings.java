package us.drullk.aerolite.world.shaft.binding;

import tamaized.beanification.Component;
import tamaized.beanification.Directory;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects the pattern bindings and picks the binding of each pattern settings class.
 * Settings without a binding take the maze binding with the uniform maze and log one line per settings class.
 *
 * <p>Every binding class lives in a subpackage of {@code us.drullk.aerolite.world.shaft.binding}, one subpackage per pattern.
 * No other class and no test class lives under that package.
 * The directory scan of the collector initializes every class there during mod construction.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class PatternBindings {

    // Beanification fills this list after construction. Its scan initializes every class of this package tree.
    @Directory(PatternBinding.class)
    private List<PatternBinding> bindings = List.of();
    private final Set<Class<?>> fallbacks = ConcurrentHashMap.newKeySet();

    public PatternBindings() {
    }

    /** A collector of the given bindings, for code that runs without the bean context. */
    public static PatternBindings of(List<PatternBinding> bindings) {
        PatternBindings collector = new PatternBindings();
        collector.bindings = List.copyOf(bindings);
        return collector;
    }

    public List<PatternBinding> bindings() {
        return this.bindings;
    }

    /** The pattern of the settings in the context. */
    public SurfacePattern create(PatternContext context) {
        PatternBinding binding = this.find(context.settings().getClass());
        if (binding != null) {
            return binding.create(context);
        }
        if (this.fallbacks.add(context.settings().getClass())) {
            Aerolite.LOGGER.warn("The pattern {} has no binding yet. It paints as the uniform maze.", context.settings().patternType().getSerializedName());
        }
        return this.binding(MazeSettings.class).create(context.withSettings(MazeSettings.DEFAULT));
    }

    /** The binding of a settings class. */
    public PatternBinding binding(Class<? extends PatternSettings> settings) {
        PatternBinding binding = this.find(settings);
        if (binding == null) {
            throw new IllegalStateException("No pattern binding serves " + settings.getSimpleName());
        }
        return binding;
    }

    @Nullable
    private PatternBinding find(Class<?> settings) {
        for (PatternBinding binding : this.bindings) {
            if (binding.settings() == settings) {
                return binding;
            }
        }
        return null;
    }

}
