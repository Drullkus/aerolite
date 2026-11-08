package us.drullk.aerolite.world.island;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Checks the island settings: the round trip through the island density function, the kept noise and the fill bounds.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
class IslandSettingsTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));

    @Test
    void defaultRoundTripsThroughTheFunction() {
        assertEquals(IslandSettings.DEFAULT, IslandSettings.of(IslandSettings.DEFAULT.function(NOISE)));
    }

    @Test
    void applyKeepsTheNoiseAndTheSeed() {
        VoronoiIslands shipped = IslandSettings.DEFAULT.function(NOISE);
        IslandSettings edited = IslandSettings.DEFAULT.withCellSize(512).withDistortionOctaves(5);
        VoronoiIslands applied = edited.apply(shipped);
        assertSame(shipped.noise(), applied.noise(), "The applied function lost the noise");
        assertEquals(shipped.lattice().seed(), applied.lattice().seed(), "The applied function changed the seed");
        assertEquals(edited, IslandSettings.of(applied));
        assertEquals(512, applied.cellSize());
        assertEquals(5, applied.distortionOctaves());
    }

    @Test
    void fillBoundsPushEachOther() {
        IslandSettings raised = IslandSettings.DEFAULT.withFillMin(0.95);
        assertEquals(0.95, raised.fillMin());
        assertEquals(0.95, raised.fillMax(), "The fill minimum did not push the maximum");
        IslandSettings lowered = IslandSettings.DEFAULT.withFillMax(0.1);
        assertEquals(0.1, lowered.fillMax());
        assertEquals(0.1, lowered.fillMin(), "The fill maximum did not push the minimum");
        assertEquals(IslandSettings.DEFAULT.fillMax(), IslandSettings.DEFAULT.withFillMin(0.5).fillMax(), "A fill minimum below the maximum moved it");
    }

}
