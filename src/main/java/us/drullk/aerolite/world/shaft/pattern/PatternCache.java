package us.drullk.aerolite.world.shaft.pattern;

import tamaized.beanification.Component;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;

import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Keeps the surface path draws and the finished chunk bands of the band patterns, so neighbor chunks do not draw them again.
 * A pattern object lives for one paint call. This component outlives it.
 *
 * <p>Each entry is a pure function of its key. The key holds the pattern options, a fingerprint of the pillar random,
 * the ring, the chunk and the junction boxes in reach. The cache drops the least recently used entry above its capacity.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class PatternCache {

    private static final int CAPACITY = 4096;

    /** What an entry holds. */
    public enum Kind {
        /** The surface paths of the last regions of a chunk. */
        LAST_PATHS,
        /** The surface paths of all regions of a chunk. */
        CHUNK_PATHS,
        /** The finished band of a chunk, without node sets. */
        CHUNK_BAND
    }

    /**
     * The key of one entry.
     *
     * @param pattern     the pattern class, its options and the ring description
     * @param fingerprint two values of the pillar random
     * @param kind        what the entry holds
     * @param ring        the ring of the shape
     * @param chunk       the chunk along the axis
     * @param boxes       the junction boxes that the entry can reach
     */
    public record Key(Object pattern, List<Long> fingerprint, Kind kind, int ring, int chunk, List<ShaftMaze.Box> boxes) {
    }

    private final Map<Key, Object> entries = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Object> eldest) {
            return this.size() > CAPACITY;
        }
    };

    public PatternCache() {
    }

    /** The value of the key. A missing value is computed outside the lock, so two threads can compute the same value. */
    @SuppressWarnings("unchecked")
    public <T> T get(Key key, Supplier<T> compute) {
        synchronized (this.entries) {
            Object value = this.entries.get(key);
            if (value != null) {
                return (T) value;
            }
        }
        T value = compute.get();
        this.put(key, value);
        return value;
    }

    /** The value of the key, or null. */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T find(Key key) {
        synchronized (this.entries) {
            return (T) this.entries.get(key);
        }
    }

    public void put(Key key, Object value) {
        synchronized (this.entries) {
            this.entries.put(key, value);
        }
    }

}
