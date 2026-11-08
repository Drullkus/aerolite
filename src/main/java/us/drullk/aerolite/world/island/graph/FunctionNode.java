package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One box of a function graph or a router graph. A function node holds its values. The graph holds the links into its input pins.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public sealed interface FunctionNode {

    /** The input pin names in the JSON sequence. A child function node feeds each pin. */
    List<String> inputPins();

    /**
     * A function node of a known type. It holds one value per field that is not an input pin.
     * A bare constant is a {@code minecraft:constant} function node whose JSON is a bare number.
     */
    record Typed(FunctionNodeType type, Map<String, FieldValue> values, boolean bare) implements FunctionNode {

        public Typed {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            if (bare && !type.id().equals(FunctionNodeTypes.CONSTANT)) {
                throw new IllegalArgumentException("Only a constant has a bare form, not " + type.id());
            }
        }

        public Typed(FunctionNodeType type, Map<String, FieldValue> values) {
            this(type, values, false);
        }

        /** A constant whose JSON is a bare number. */
        public static Typed bareConstant(double value) {
            return new Typed(FunctionNodeTypes.constant(), Map.of("argument", new FieldValue.Number(value)), true);
        }

        @Override
        public List<String> inputPins() {
            List<String> pins = new ArrayList<>();
            for (FieldSpec field : this.type.fields()) {
                if (field.kind() == FieldKind.FUNCTION) {
                    pins.add(field.name());
                } else if (field.kind() == FieldKind.SPLINE && this.values.get(field.name()) instanceof FieldValue.Spline spline) {
                    spline.value().coordinatePins(field.name(), pins);
                }
            }
            return List.copyOf(pins);
        }

        public Typed with(String field, FieldValue value) {
            Map<String, FieldValue> changed = new LinkedHashMap<>(this.values);
            changed.put(field, value);
            return new Typed(this.type, changed, this.bare);
        }
    }

    /** A leaf that names a density function entry. The entry opens as its own function graph. */
    record Reference(ResourceLocation entry) implements FunctionNode {

        @Override
        public List<String> inputPins() {
            return List.of();
        }
    }

    /** A leaf of a type that no function node type describes. The model keeps its JSON as it is. */
    record Unknown(ResourceLocation typeId, JsonObject json) implements FunctionNode {

        public Unknown {
            json = json.deepCopy();
        }

        @Override
        public JsonObject json() {
            return this.json.deepCopy();
        }

        @Override
        public List<String> inputPins() {
            return List.of();
        }
    }

}
