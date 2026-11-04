package us.drullk.aerolite.world.shaft.pattern.chaos;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.path.Band;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Keeps the recent chaos bands, so the chunks of one block of chunks and the chunks on both sides of a pillar share one computation.
 * A band in the store is never written after its computation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ChaosScenes {

    private static final int CAPACITY = 256;

    /**
     * The identity of one computed block of chunks.
     *
     * @param pillarSeed the seed of the pillar
     * @param settings   the chaos settings
     * @param ring       the ring description
     * @param minChunk   the first chunk of the pillar
     * @param maxChunk   the last chunk of the pillar
     * @param lo         the first chunk of the block
     * @param hi         the first chunk after the block
     * @param boxes      the boxes near the window
     */
    public record Key(long pillarSeed, ChaosSettings settings, RingShape ring, int minChunk, int maxChunk, int lo, int hi, List<ShaftMaze.Box> boxes) {
    }

    private final Cache<Key, List<Band>> store = CacheBuilder.newBuilder().maximumSize(CAPACITY).build();

    public ChaosScenes() {
    }

    /** The bands of one block, one per ring, from the store or from the computation. */
    public List<Band> bands(Key key, Supplier<List<Band>> compute) {
        try {
            return this.store.get(key, compute::get);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The chaos bands of " + key + " failed", e.getCause());
        }
    }

}
