package us.drullk.aerolite.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;

/**
 * The Overworld effects of the preset. The clouds sit at the cloud height of the settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public class AeroliteDimensionEffects extends DimensionSpecialEffects.OverworldEffects {

    private final CloudHeightHolder cloudHeight;

    public AeroliteDimensionEffects(CloudHeightHolder cloudHeight) {
        this.cloudHeight = cloudHeight;
    }

    @Override
    public float getCloudHeight() {
        return this.cloudHeight.get();
    }

}
