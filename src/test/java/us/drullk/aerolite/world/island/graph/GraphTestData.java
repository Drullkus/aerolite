package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.NoiseRouterData;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The shared data of the function node model tests: the vanilla registries of the noises and the density functions,
 * a JSON object with every field of each type, and the shipped routers. The values are arbitrary and carry no shape.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
final class GraphTestData {

    static final String REFERENCE = "minecraft:shift_x";
    static final String NOISE = "minecraft:temperature";
    static final String VARIATION_LIST = "aerolite:overworld_islands";

    private static final HolderLookup.Provider REGISTRIES = new RegistrySetBuilder()
            .add(Registries.NOISE, NoiseData::bootstrap)
            .add(Registries.DENSITY_FUNCTION, NoiseRouterData::bootstrap)
            .build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

    private GraphTestData() {
    }

    /** JSON ops that see the vanilla noises and density functions. */
    static RegistryOps<JsonElement> ops() {
        return REGISTRIES.createSerializationContext(JsonOps.INSTANCE);
    }

    /** A JSON object of the type with every field, in the field sequence. */
    static JsonObject full(FunctionNodeType type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type.id().toString());
        for (FieldSpec field : type.fields()) {
            switch (field.kind()) {
                case FUNCTION -> {
                    if (field.acceptsReference()) {
                        object.addProperty(field.name(), REFERENCE);
                    } else {
                        object.addProperty(field.name(), 0.25);
                    }
                }
                case NUMBER -> object.addProperty(field.name(), Mth.clamp(0.5, field.min(), field.max()));
                case INTEGER -> object.addProperty(field.name(), (int) Mth.clamp(16.0, field.min(), field.max()));
                case ENUM -> object.addProperty(field.name(), field.choices().getFirst());
                case NOISE -> object.addProperty(field.name(), NOISE);
                case SPLINE -> object.add(field.name(), spline());
                case VARIATION_LIST -> object.addProperty(field.name(), VARIATION_LIST);
            }
        }
        return object;
    }

    /** A spline of two levels. Each number is exact as a float. */
    static JsonObject spline() {
        JsonObject inner = new JsonObject();
        inner.addProperty("coordinate", "minecraft:overworld/erosion");
        JsonArray innerPoints = new JsonArray();
        innerPoints.add(point(0.0, new JsonPrimitive(-0.75), 1.0));
        inner.add("points", innerPoints);
        JsonObject outer = new JsonObject();
        outer.addProperty("coordinate", "minecraft:overworld/continents");
        JsonArray points = new JsonArray();
        points.add(point(-0.5, new JsonPrimitive(0.25), 0.0));
        points.add(point(0.5, inner, 0.5));
        outer.add("points", points);
        return outer;
    }

    private static JsonObject point(double location, JsonElement value, double derivative) {
        JsonObject point = new JsonObject();
        point.addProperty("location", location);
        point.add("value", value);
        point.addProperty("derivative", derivative);
        return point;
    }

    /** The generated noise settings of the Islands preset for one dimension. */
    static JsonObject islandNoiseSettings(String dimension) {
        return generated("data/aerolite/worldgen/noise_settings/islands/" + dimension + ".json");
    }

    /** The generated island density function entry of the Islands preset for one dimension. */
    static JsonObject islandFunction(String dimension) {
        return generated("data/aerolite/worldgen/density_function/islands/" + dimension + ".json");
    }

    private static JsonObject generated(String path) {
        try (InputStream stream = GraphTestData.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("The generated resource " + path + " does not exist");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("The read of " + path + " failed", exception);
        }
    }

}
