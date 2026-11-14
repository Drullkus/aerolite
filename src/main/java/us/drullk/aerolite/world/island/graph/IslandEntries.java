package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The saved JSON of the density function entries, the island variations and the lists for a check.
 * An edit of the flow wins over the registry entry with the same id. A missing entry gives null.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public interface IslandEntries {

    /** The entries that find nothing. */
    IslandEntries NONE = new IslandEntries() {

        @Override
        @Nullable
        public JsonElement densityFunction(ResourceLocation id) {
            return null;
        }

        @Override
        @Nullable
        public JsonObject variation(ResourceLocation id) {
            return null;
        }

        @Override
        @Nullable
        public JsonObject list(ResourceLocation id) {
            return null;
        }
    };

    /** The saved JSON of a density function entry, or null. */
    @Nullable
    JsonElement densityFunction(ResourceLocation id);

    /** The JSON of an island variation, or null. */
    @Nullable
    JsonObject variation(ResourceLocation id);

    /** The JSON of an island variation list, or null. */
    @Nullable
    JsonObject list(ResourceLocation id);

}
