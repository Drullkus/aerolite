package us.drullk.aerolite.client;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.IslandPack;
import us.drullk.aerolite.world.level.WorldShaftPack;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the preset that a recreated world takes from the folder names of its temporary data pack folder.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
class RecreatePresetTest {

    private final RecreatePreset recreate = new RecreatePreset();

    @Test
    void islandPackGivesTheIslandsPreset() {
        assertEquals(Optional.of(AeroliteWorldgen.ISLANDS_PRESET), this.recreate.preset(Set.of(IslandPack.NAME)));
        assertEquals(Optional.of(AeroliteWorldgen.ISLANDS_PRESET), this.recreate.preset(Set.of(IslandPack.NAME, WorldShaftPack.NAME, "other")));
    }

    @Test
    void pillarPackAloneGivesTheVoidPreset() {
        assertEquals(Optional.of(AeroliteWorldgen.PRESET), this.recreate.preset(Set.of(WorldShaftPack.NAME)));
        assertEquals(Optional.of(AeroliteWorldgen.PRESET), this.recreate.preset(Set.of(WorldShaftPack.NAME, "other")));
    }

    @Test
    void otherFoldersKeepTheWorldType() {
        assertEquals(Optional.empty(), this.recreate.preset(Set.of()));
        assertEquals(Optional.empty(), this.recreate.preset(Set.of("other", "aerolite_islands.tmp")));
    }

}
