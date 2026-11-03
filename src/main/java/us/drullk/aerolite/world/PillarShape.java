package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The cross section of the pillars of a variation. Columns take the shape of their variation too.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum PillarShape implements StringRepresentable {
    PILLAR("pillar"),
    I_BEAM("i_beam"),
    BRIDGE("bridge");

    public static final Codec<PillarShape> CODEC = StringRepresentable.fromEnum(PillarShape::values);

    private final String id;

    PillarShape(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

}
