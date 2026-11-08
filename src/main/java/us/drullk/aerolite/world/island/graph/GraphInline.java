package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Puts the JSON of edited density function entries in the place of each reference to them. The preview builds its terrain from the result.
 * The walk follows the density function places of {@link FunctionNodeTypes}: the input pins and the spline coordinates.
 * A reference to an entry without an edit stays a string, so the registry resolves it.
 * An entry that reaches itself through its own references is an error. A reached entry whose JSON is an error is an error.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class GraphInline {

    private static final String TYPE = "type";

    private GraphInline() {
    }

    /** The router with each reference to an edited entry replaced by the JSON of that entry. */
    public static DataResult<JsonObject> router(JsonObject router, Map<ResourceLocation, DataResult<JsonElement>> edited) {
        JsonObject result = new JsonObject();
        try {
            for (Map.Entry<String, JsonElement> field : router.entrySet()) {
                result.add(field.getKey(), function(field.getValue(), field.getKey(), edited, new ArrayDeque<>()));
            }
        } catch (InlineException exception) {
            return DataResult.error(exception::getMessage);
        }
        return DataResult.success(result);
    }

    /** One density function with each reference to an edited entry replaced by the JSON of that entry. */
    public static DataResult<JsonElement> function(JsonElement json, Map<ResourceLocation, DataResult<JsonElement>> edited) {
        try {
            return DataResult.success(function(json, FunctionGraph.OUTPUT, edited, new ArrayDeque<>()));
        } catch (InlineException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static JsonElement function(JsonElement json, String path, Map<ResourceLocation, DataResult<JsonElement>> edited, Deque<ResourceLocation> stack) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            ResourceLocation entry = ResourceLocation.tryParse(json.getAsString());
            DataResult<JsonElement> replacement = entry == null ? null : edited.get(entry);
            if (replacement == null) {
                return json.deepCopy();
            }
            if (stack.contains(entry)) {
                throw new InlineException(path, "The entry " + entry + " reaches itself through its references");
            }
            JsonElement entryJson = replacement.getOrThrow(message -> new InlineException(path, "The entry " + entry + " does not build: " + message));
            stack.push(entry);
            try {
                return function(entryJson, path, edited, stack);
            } finally {
                stack.pop();
            }
        }
        if (!json.isJsonObject()) {
            return json.deepCopy();
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement typeJson = object.get(TYPE);
        ResourceLocation typeId = typeJson != null && typeJson.isJsonPrimitive() ? ResourceLocation.tryParse(typeJson.getAsString()) : null;
        FunctionNodeType type = typeId == null ? null : FunctionNodeTypes.get(typeId);
        if (type == null) {
            return object.deepCopy();
        }
        JsonObject result = object.deepCopy();
        for (FieldSpec field : type.fields()) {
            JsonElement value = object.get(field.name());
            if (value == null) {
                continue;
            }
            String fieldPath = FunctionNodeGraph.childPath(path, field.name());
            if (field.kind() == FieldKind.FUNCTION) {
                result.add(field.name(), function(value, fieldPath, edited, stack));
            } else if (field.kind() == FieldKind.SPLINE) {
                result.add(field.name(), spline(value, fieldPath, edited, stack));
            }
        }
        return result;
    }

    private static JsonElement spline(JsonElement json, String path, Map<ResourceLocation, DataResult<JsonElement>> edited, Deque<ResourceLocation> stack) {
        if (!json.isJsonObject()) {
            return json.deepCopy();
        }
        JsonObject result = json.getAsJsonObject().deepCopy();
        JsonElement coordinate = result.get(SplineValue.COORDINATE);
        if (coordinate != null) {
            result.add(SplineValue.COORDINATE, function(coordinate, FunctionNodeGraph.childPath(path, SplineValue.COORDINATE), edited, stack));
        }
        JsonElement points = result.get("points");
        if (points != null && points.isJsonArray()) {
            JsonArray array = points.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                if (array.get(index).isJsonObject()) {
                    JsonObject point = array.get(index).getAsJsonObject();
                    JsonElement value = point.get("value");
                    if (value != null) {
                        point.add("value", spline(value, path + "/points/" + index + "/value", edited, stack));
                    }
                }
            }
        }
        return result;
    }

    /** A failure of the walk at one path. */
    private static final class InlineException extends RuntimeException {

        InlineException(String path, String message) {
            super(GraphProblem.format(path, message), null, false, false);
        }
    }

}
