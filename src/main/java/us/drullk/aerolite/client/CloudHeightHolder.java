package us.drullk.aerolite.client;

import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;

/**
 * Holds the cloud height that the server sent last.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class CloudHeightHolder {

    private int cloudHeight = AeroliteSettings.DEFAULT.cloudHeight();

    public CloudHeightHolder() {
    }

    public int get() {
        return this.cloudHeight;
    }

    public void set(int cloudHeight) {
        this.cloudHeight = cloudHeight;
    }

}
