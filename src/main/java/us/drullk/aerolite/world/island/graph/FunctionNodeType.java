package us.drullk.aerolite.world.island.graph;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One density function type as a function node type: its id and its fields in the JSON sequence of its vanilla codec.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public record FunctionNodeType(ResourceLocation id, List<FieldSpec> fields) {

    public FunctionNodeType {
        fields = List.copyOf(fields);
    }

    @Nullable
    public FieldSpec field(String name) {
        for (FieldSpec field : this.fields) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }

    /** True when the type has no field. A function node of this type is a leaf. */
    public boolean isLeaf() {
        return this.fields.isEmpty();
    }

}
