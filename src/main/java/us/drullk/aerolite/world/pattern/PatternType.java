package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The type of a surface pattern. Its id is the value of the key {@code type} of the pattern JSON.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum PatternType implements StringRepresentable {
    MAZES("aerolite:mazes"),
    BRICKS("aerolite:bricks"),
    TILES("aerolite:tiles"),
    SCALES("aerolite:scales"),
    CHAOS("aerolite:chaos");

    public static final Codec<PatternType> CODEC = StringRepresentable.fromEnum(PatternType::values);

    private final String id;

    PatternType(String id) {
        this.id = id;
    }

    /** The codec of the fields of this pattern type, without the key {@code type}. */
    public MapCodec<? extends PatternSettings> codec() {
        return switch (this) {
            case MAZES -> MazeSettings.MAP_CODEC;
            case BRICKS -> BricksSettings.MAP_CODEC;
            case TILES -> TilesSettings.MAP_CODEC;
            case SCALES -> ScalesSettings.MAP_CODEC;
            case CHAOS -> ChaosSettings.MAP_CODEC;
        };
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

}
