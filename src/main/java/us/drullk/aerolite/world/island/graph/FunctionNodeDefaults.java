package us.drullk.aerolite.world.island.graph;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The field values of a new function node. The vanilla density function codecs give no field a default value.
 * So a number takes zero, or the bound nearest zero when zero is out of range. A choice takes its first entry.
 * A noise field takes the noise id that the caller gives. A spline takes the constant spline zero.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class FunctionNodeDefaults {

    private FunctionNodeDefaults() {
    }

    /** A function node of the type with the default value of each field that is not an input pin. */
    public static FunctionNode.Typed create(FunctionNodeType type, ResourceLocation noise) {
        Map<String, FieldValue> values = new LinkedHashMap<>();
        for (FieldSpec field : type.fields()) {
            switch (field.kind()) {
                case FUNCTION -> {
                }
                case NUMBER -> values.put(field.name(), new FieldValue.Number(nearestZero(field)));
                case INTEGER -> values.put(field.name(), new FieldValue.Int((int) nearestZero(field)));
                case ENUM -> values.put(field.name(), new FieldValue.Choice(field.choices().getFirst()));
                case NOISE -> values.put(field.name(), FieldValue.Noise.of(noise));
                case SPLINE -> values.put(field.name(), new FieldValue.Spline(new SplineValue.Constant(0)));
            }
        }
        return new FunctionNode.Typed(type, values);
    }

    /** Zero clamped to the range of the field. */
    public static double nearestZero(FieldSpec field) {
        return Math.clamp(0, field.min(), field.max());
    }

}
