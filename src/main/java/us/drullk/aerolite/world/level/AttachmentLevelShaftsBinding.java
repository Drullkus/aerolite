package us.drullk.aerolite.world.level;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.attachment.AttachmentType;

import javax.annotation.Nullable;

/**
 * Stores the shaft data in an unsaved data attachment of the server level.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public final class AttachmentLevelShaftsBinding implements LevelShaftsBinding {

    private final AttachmentType<LevelShafts> type;

    public AttachmentLevelShaftsBinding(AttachmentType<LevelShafts> type) {
        this.type = type;
    }

    @Override
    public void bind(ServerLevel level, LevelShafts shafts) {
        level.setData(this.type, shafts);
    }

    @Override
    @Nullable
    public LevelShafts get(ServerLevel level) {
        return level.getExistingDataOrNull(this.type);
    }

}
