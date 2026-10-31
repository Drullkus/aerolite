package us.drullk.aerolite.client.preview;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The world chunk positions of a preview region: the square of {@code 2 * radius + 1} chunks around a center chunk.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
public final class RegionChunks {

    private RegionChunks() {
    }

    /**
     * The chunk offsets of a region in spiral sequence: the center chunk, then each ring outward.
     * Within a ring, the chunks nearest the center come first. Chunks at the same distance keep the ring sequence.
     * Each ring sequence starts at its corner of least X and least Z.
     */
    public static ChunkPos[] spiral(int radius) {
        ChunkPos[] spiral = rings(radius);
        Arrays.sort(spiral, Comparator.comparingInt((ChunkPos pos) -> Math.max(Math.abs(pos.x), Math.abs(pos.z)))
                .thenComparingInt(pos -> pos.x * pos.x + pos.z * pos.z));
        return spiral;
    }

    private static ChunkPos[] rings(int radius) {
        int size = 2 * radius + 1;
        ChunkPos[] spiral = new ChunkPos[size * size];
        int next = 0;
        spiral[next++] = new ChunkPos(0, 0);
        for (int ring = 1; ring <= radius; ring++) {
            for (int x = -ring; x < ring; x++) {
                spiral[next++] = new ChunkPos(x, -ring);
            }
            for (int z = -ring; z < ring; z++) {
                spiral[next++] = new ChunkPos(ring, z);
            }
            for (int x = ring; x > -ring; x--) {
                spiral[next++] = new ChunkPos(x, ring);
            }
            for (int z = ring; z > -ring; z--) {
                spiral[next++] = new ChunkPos(-ring, z);
            }
        }
        return spiral;
    }

    /** True when the region around {@code center} holds the chunk. */
    public static boolean contains(ChunkPos center, int radius, int chunkX, int chunkZ) {
        return Math.abs(chunkX - center.x) <= radius && Math.abs(chunkZ - center.z) <= radius;
    }

    /** The chunks of the region that {@code present} does not hold, in spiral sequence from the center chunk. */
    public static List<ChunkPos> missing(ChunkPos center, int radius, LongSet present) {
        List<ChunkPos> missing = new ArrayList<>();
        for (ChunkPos offset : spiral(radius)) {
            ChunkPos pos = new ChunkPos(center.x + offset.x, center.z + offset.z);
            if (!present.contains(pos.toLong())) {
                missing.add(pos);
            }
        }
        return missing;
    }

    /** The index of a chunk in a region grid with the X index first. */
    public static int index(ChunkPos center, int radius, int chunkX, int chunkZ) {
        return (chunkX - center.x + radius) * (2 * radius + 1) + chunkZ - center.z + radius;
    }

}
