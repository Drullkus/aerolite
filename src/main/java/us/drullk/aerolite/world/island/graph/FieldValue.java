package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The value of one field of a function node that is not an input pin. A {@link FieldKind#FUNCTION} field holds no value; a link feeds it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public sealed interface FieldValue {

    /** The value of a {@link FieldKind#NUMBER} field. */
    record Number(double value) implements FieldValue {
    }

    /** The value of a {@link FieldKind#INTEGER} field. */
    record Int(int value) implements FieldValue {
    }

    /** The value of a {@link FieldKind#ENUM} field. */
    record Choice(String value) implements FieldValue {
    }

    /** The value of a {@link FieldKind#NOISE} field: a noise id, or an inline noise object that the model keeps as it is. */
    record Noise(@Nullable ResourceLocation id, @Nullable JsonObject inline) implements FieldValue {

        public Noise {
            if ((id == null) == (inline == null)) {
                throw new IllegalArgumentException("A noise value holds an id or an inline object");
            }
            inline = inline == null ? null : inline.deepCopy();
        }

        public static Noise of(ResourceLocation id) {
            return new Noise(id, null);
        }

        @Override
        @Nullable
        public JsonObject inline() {
            return this.inline == null ? null : this.inline.deepCopy();
        }
    }

    /** The value of a {@link FieldKind#SPLINE} field. Its coordinates are input pins, not part of the value. */
    record Spline(SplineValue value) implements FieldValue {
    }

    /** The value of a {@link FieldKind#VARIATION_LIST} field: the id of an island variation list. */
    record VariationList(ResourceLocation id) implements FieldValue {
    }

}
