package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps graphs to and from the vanilla density function JSON. It works on Gson trees with the field descriptions of {@link FunctionNodeTypes}.
 * A bare number is a constant, a bare string is a reference node, an object holds its type id under the key {@code type}.
 * A save writes the key {@code type} first, then the fields in the sequence of the vanilla codec.
 * A load keeps a value out of its vanilla range. {@link GraphValidator} reports it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class GraphJson {

    private static final String TYPE = "type";

    private GraphJson() {
    }

    /** Loads one density function JSON tree as a function graph. */
    public static DataResult<FunctionGraph> readFunction(JsonElement json) {
        FunctionGraph graph = new FunctionGraph();
        try {
            graph.setOutput(read(graph, json, FunctionGraph.OUTPUT));
        } catch (GraphJsonException exception) {
            return DataResult.error(exception::getMessage);
        }
        return DataResult.success(graph);
    }

    /**
     * Loads a {@code noise_router} object as a router graph. An output whose JSON equals the field of the shipped router loads as dimension default.
     * Any other bare number becomes a constant output, and any other value becomes a tree of function nodes.
     */
    public static DataResult<RouterGraph> readRouter(JsonObject router, JsonObject shipped) {
        RouterGraph graph = new RouterGraph(new RouterGraph.DimensionDefault());
        try {
            for (String field : router.keySet()) {
                if (!RouterGraph.FIELDS.contains(field)) {
                    throw new GraphJsonException(field, "The router has no field " + field);
                }
            }
            for (String field : RouterGraph.FIELDS) {
                JsonElement value = router.get(field);
                if (value == null) {
                    throw new GraphJsonException(field, "The router field is missing");
                }
                if (value.equals(shipped.get(field))) {
                    graph.set(field, new RouterGraph.DimensionDefault());
                } else if (isNumber(value)) {
                    graph.set(field, new RouterGraph.Constant(value.getAsDouble()));
                } else {
                    graph.link(read(graph, value, field), field);
                }
            }
        } catch (GraphJsonException exception) {
            return DataResult.error(exception::getMessage);
        }
        return DataResult.success(graph);
    }

    /**
     * Loads one density function JSON tree into a graph that exists, without a link to a graph output. Gives the id of its root function node.
     * A failed load can leave function nodes of the tree in the graph.
     */
    public static DataResult<Integer> readInto(FunctionNodeGraph graph, JsonElement json, String path) {
        try {
            return DataResult.success(read(graph, json, path));
        } catch (GraphJsonException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** Saves a function graph as one density function JSON tree. */
    public static DataResult<JsonElement> write(FunctionGraph graph) {
        Integer root = graph.output();
        if (root == null) {
            return DataResult.error(() -> GraphProblem.format(FunctionGraph.OUTPUT, "The output has no function node"));
        }
        try {
            return DataResult.success(write(graph, root, FunctionGraph.OUTPUT));
        } catch (GraphJsonException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /**
     * Saves a router graph as a {@code noise_router} object in the sequence of the vanilla codec.
     * An output on dimension default takes a copy of the field of the shipped router.
     */
    public static DataResult<JsonObject> writeRouter(RouterGraph graph, JsonObject shipped) {
        JsonObject router = new JsonObject();
        try {
            for (String field : RouterGraph.FIELDS) {
                RouterGraph.Output output = graph.output(field);
                if (output instanceof RouterGraph.Output.Linked linked) {
                    router.add(field, write(graph, linked.functionNode(), field));
                } else if (output instanceof RouterGraph.Constant constant) {
                    router.add(field, new JsonPrimitive(constant.value()));
                } else {
                    JsonElement value = shipped.get(field);
                    if (value == null) {
                        throw new GraphJsonException(field, "The shipped router has no field " + field);
                    }
                    router.add(field, value.deepCopy());
                }
            }
        } catch (GraphJsonException exception) {
            return DataResult.error(exception::getMessage);
        }
        return DataResult.success(router);
    }

    private static int read(FunctionNodeGraph graph, JsonElement json, String path) {
        if (isNumber(json)) {
            return graph.add(FunctionNode.Typed.bareConstant(json.getAsDouble()));
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return graph.add(new FunctionNode.Reference(id(json.getAsString(), path)));
        }
        if (!json.isJsonObject()) {
            throw new GraphJsonException(path, "A density function is a number, a string or an object");
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement typeJson = object.get(TYPE);
        if (typeJson == null || !typeJson.isJsonPrimitive() || !typeJson.getAsJsonPrimitive().isString()) {
            throw new GraphJsonException(path, "The object has no type id");
        }
        ResourceLocation typeId = id(typeJson.getAsString(), path);
        FunctionNodeType type = FunctionNodeTypes.get(typeId);
        if (type == null) {
            return graph.add(new FunctionNode.Unknown(typeId, object));
        }
        Set<String> keys = object.keySet();
        for (String key : keys) {
            if (!key.equals(TYPE) && type.field(key) == null) {
                throw new GraphJsonException(path, "The type " + typeId + " has no field " + key);
            }
        }
        Map<String, FieldValue> values = new LinkedHashMap<>();
        Map<String, JsonElement> children = new LinkedHashMap<>();
        for (FieldSpec field : type.fields()) {
            JsonElement value = object.get(field.name());
            String fieldPath = FunctionNodeGraph.childPath(path, field.name());
            if (value == null) {
                throw new GraphJsonException(fieldPath, "The field is missing");
            }
            switch (field.kind()) {
                case FUNCTION -> children.put(field.name(), value);
                case NUMBER -> values.put(field.name(), new FieldValue.Number(number(value, fieldPath)));
                case INTEGER -> values.put(field.name(), new FieldValue.Int(integer(value, fieldPath)));
                case ENUM -> values.put(field.name(), new FieldValue.Choice(string(value, fieldPath)));
                case NOISE -> values.put(field.name(), noise(value, fieldPath));
                case SPLINE -> values.put(field.name(), new FieldValue.Spline(spline(value, field.name(), fieldPath, children)));
            }
        }
        int id = graph.add(new FunctionNode.Typed(type, values));
        for (Map.Entry<String, JsonElement> child : children.entrySet()) {
            int childId = read(graph, child.getValue(), FunctionNodeGraph.childPath(path, child.getKey()));
            graph.link(childId, new FunctionNodeGraph.Pin(id, child.getKey()));
        }
        return id;
    }

    /** Reads a spline. Each coordinate goes to the children under its pin name. */
    private static SplineValue spline(JsonElement json, String pin, String path, Map<String, JsonElement> children) {
        if (isNumber(json)) {
            return new SplineValue.Constant(json.getAsDouble());
        }
        if (!json.isJsonObject()) {
            throw new GraphJsonException(path, "A spline is a number or an object");
        }
        JsonObject object = json.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!key.equals(SplineValue.COORDINATE) && !key.equals("points")) {
                throw new GraphJsonException(path, "A spline has no field " + key);
            }
        }
        JsonElement coordinate = object.get(SplineValue.COORDINATE);
        if (coordinate == null) {
            throw new GraphJsonException(path, "The spline has no coordinate");
        }
        children.put(pin + "/" + SplineValue.COORDINATE, coordinate);
        JsonElement pointsJson = object.get("points");
        if (pointsJson == null || !pointsJson.isJsonArray()) {
            throw new GraphJsonException(path, "The spline has no points list");
        }
        List<SplineValue.Point> points = new ArrayList<>();
        JsonArray array = pointsJson.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            String pointPath = path + "/points/" + index;
            if (!array.get(index).isJsonObject()) {
                throw new GraphJsonException(pointPath, "A spline point is an object");
            }
            JsonObject point = array.get(index).getAsJsonObject();
            for (String key : point.keySet()) {
                if (!key.equals("location") && !key.equals("value") && !key.equals("derivative")) {
                    throw new GraphJsonException(pointPath, "A spline point has no field " + key);
                }
            }
            JsonElement value = required(point, "value", pointPath);
            points.add(new SplineValue.Point(
                    number(required(point, "location", pointPath), pointPath + "/location"),
                    spline(value, pin + "/points/" + index + "/value", pointPath + "/value", children),
                    number(required(point, "derivative", pointPath), pointPath + "/derivative")));
        }
        return new SplineValue.Multipoint(points);
    }

    private static JsonElement write(FunctionNodeGraph graph, int id, String path) {
        FunctionNode functionNode = graph.functionNode(id);
        if (functionNode instanceof FunctionNode.Reference reference) {
            return new JsonPrimitive(reference.entry().toString());
        }
        if (functionNode instanceof FunctionNode.Unknown unknown) {
            return unknown.json();
        }
        FunctionNode.Typed typed = (FunctionNode.Typed) functionNode;
        if (typed.bare()) {
            return new JsonPrimitive(((FieldValue.Number) typed.values().get("argument")).value());
        }
        JsonObject object = new JsonObject();
        object.addProperty(TYPE, typed.type().id().toString());
        for (FieldSpec field : typed.type().fields()) {
            String fieldPath = FunctionNodeGraph.childPath(path, field.name());
            if (field.kind() == FieldKind.FUNCTION) {
                object.add(field.name(), child(graph, id, field.name(), fieldPath));
                continue;
            }
            FieldValue value = typed.values().get(field.name());
            if (value == null) {
                throw new GraphJsonException(fieldPath, "The field has no value");
            }
            object.add(field.name(), switch (value) {
                case FieldValue.Number number -> new JsonPrimitive(number.value());
                case FieldValue.Int integer -> new JsonPrimitive(integer.value());
                case FieldValue.Choice choice -> new JsonPrimitive(choice.value());
                case FieldValue.Noise noise -> noise.id() != null ? new JsonPrimitive(noise.id().toString()) : noise.inline();
                case FieldValue.Spline spline -> writeSpline(graph, id, spline.value(), field.name(), fieldPath);
            });
        }
        return object;
    }

    private static JsonElement writeSpline(FunctionNodeGraph graph, int id, SplineValue spline, String pin, String path) {
        if (spline instanceof SplineValue.Constant constant) {
            return new JsonPrimitive(constant.value());
        }
        SplineValue.Multipoint multipoint = (SplineValue.Multipoint) spline;
        JsonObject object = new JsonObject();
        String coordinatePin = pin + "/" + SplineValue.COORDINATE;
        object.add(SplineValue.COORDINATE, child(graph, id, coordinatePin, FunctionNodeGraph.childPath(path, SplineValue.COORDINATE)));
        JsonArray points = new JsonArray();
        for (int index = 0; index < multipoint.points().size(); index++) {
            SplineValue.Point point = multipoint.points().get(index);
            JsonObject pointJson = new JsonObject();
            pointJson.addProperty("location", point.location());
            pointJson.add("value", writeSpline(graph, id, point.value(), pin + "/points/" + index + "/value", path + "/points/" + index + "/value"));
            pointJson.addProperty("derivative", point.derivative());
            points.add(pointJson);
        }
        object.add("points", points);
        return object;
    }

    private static JsonElement child(FunctionNodeGraph graph, int id, String pin, String path) {
        Integer child = graph.source(new FunctionNodeGraph.Pin(id, pin));
        if (child == null) {
            throw new GraphJsonException(path, "The input has no function node");
        }
        return write(graph, child, path);
    }

    private static FieldValue.Noise noise(JsonElement json, String path) {
        if (json.isJsonObject()) {
            return new FieldValue.Noise(null, json.getAsJsonObject());
        }
        return FieldValue.Noise.of(id(string(json, path), path));
    }

    private static JsonElement required(JsonObject object, String key, String path) {
        JsonElement value = object.get(key);
        if (value == null) {
            throw new GraphJsonException(FunctionNodeGraph.childPath(path, key), "The field is missing");
        }
        return value;
    }

    private static boolean isNumber(JsonElement json) {
        return json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber();
    }

    private static double number(JsonElement json, String path) {
        if (!isNumber(json)) {
            throw new GraphJsonException(path, "The field is not a number");
        }
        return json.getAsDouble();
    }

    private static int integer(JsonElement json, String path) {
        double value = number(json, path);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new GraphJsonException(path, "The field is not an int");
        }
        return (int) value;
    }

    private static String string(JsonElement json, String path) {
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new GraphJsonException(path, "The field is not a string");
        }
        return json.getAsString();
    }

    private static ResourceLocation id(String text, String path) {
        try {
            return ResourceLocation.parse(text);
        } catch (ResourceLocationException exception) {
            throw new GraphJsonException(path, "The id " + text + " is not valid");
        }
    }

    /** A load or save failure at one path. */
    private static final class GraphJsonException extends RuntimeException {

        GraphJsonException(String path, String message) {
            super(GraphProblem.format(path, message), null, false, false);
        }
    }

}
