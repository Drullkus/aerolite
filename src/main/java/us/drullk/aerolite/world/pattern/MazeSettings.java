package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.List;

/**
 * The options of the maze pattern. The maze bias stays a field of the variation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param type     the maze type; its JSON key is {@code maze}, because the key {@code type} picks the pattern
 * @param strength the pattern strength from 0 to 1; any other edge takes the weight 1 minus the strength
 * @param seam     the root of a ribs maze
 * @param spacing  the node columns per groove of a lines maze, 1 or 2
 */
public record MazeSettings(Type type, float strength, Seam seam, int spacing) implements PatternSettings {

    public static final float MIN_STRENGTH = 0;
    public static final float MAX_STRENGTH = 1;
    public static final int MIN_SPACING = 1;
    public static final int MAX_SPACING = 2;

    /** The uniform maze of 0.3. */
    public static final MazeSettings DEFAULT = new MazeSettings(Type.UNIFORM, MIN_STRENGTH, Seam.RANDOM, MIN_SPACING);

    public static final MapCodec<MazeSettings> MAP_CODEC = AeroliteCodecs.sequenced(RecordCodecBuilder.mapCodec(instance -> instance.group(
            AeroliteCodecs.defaultedField(Type.CODEC, "maze", DEFAULT.type).forGetter(MazeSettings::type),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedFloat(MIN_STRENGTH, MAX_STRENGTH), "strength", DEFAULT.strength).forGetter(MazeSettings::strength),
            AeroliteCodecs.defaultedField(Seam.CODEC, "seam", DEFAULT.seam).forGetter(MazeSettings::seam),
            AeroliteCodecs.defaultedField(AeroliteCodecs.clampedInt(MIN_SPACING, MAX_SPACING), "spacing", DEFAULT.spacing).forGetter(MazeSettings::spacing)
    ).apply(instance, MazeSettings::new)), List.of("maze", "strength", "seam", "spacing"));

    @Override
    public PatternType patternType() {
        return PatternType.MAZES;
    }

    public MazeSettings withType(Type type) {
        return new MazeSettings(type, this.strength, this.seam, this.spacing);
    }

    public MazeSettings withStrength(float strength) {
        return new MazeSettings(this.type, strength, this.seam, this.spacing);
    }

    public MazeSettings withSeam(Seam seam) {
        return new MazeSettings(this.type, this.strength, seam, this.spacing);
    }

    public MazeSettings withSpacing(int spacing) {
        return new MazeSettings(this.type, this.strength, this.seam, spacing);
    }

    /** The maze types. */
    public enum Type implements StringRepresentable {
        UNIFORM("uniform"),
        SERPENTINE("serpentine"),
        RIBS("ribs"),
        LINES("lines"),
        GILBERT("gilbert");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String id;

        Type(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    /** The root of a ribs maze: random per segment, seeded per segment, or one ring position per pillar. */
    public enum Seam implements StringRepresentable {
        RANDOM("random"),
        SEEDED("seeded"),
        PILLAR("pillar");

        public static final Codec<Seam> CODEC = StringRepresentable.fromEnum(Seam::values);

        private final String id;

        Seam(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

}
