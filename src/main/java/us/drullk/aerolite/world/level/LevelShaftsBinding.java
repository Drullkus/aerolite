package us.drullk.aerolite.world.level;

import net.minecraft.server.level.ServerLevel;

import tamaized.beanification.Bean;

import javax.annotation.Nullable;

/**
 * Stores the resolved shaft data on a server level. The level does not save it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public interface LevelShaftsBinding {

    /** Stores the shaft data on the level. The level load calls this before any chunk generates. */
    void bind(ServerLevel level, LevelShafts shafts);

    /** The shaft data of the level, or null when no shaft data is bound. */
    @Nullable
    LevelShafts get(ServerLevel level);


    /** Beanification finds a bean method by its name only, so the method takes no parameters. */
    @Bean
    static LevelShaftsBinding levelShaftsBinding() {
        return new AttachmentLevelShaftsBinding(LevelShaftsAttachment.TYPE);
    }

}
