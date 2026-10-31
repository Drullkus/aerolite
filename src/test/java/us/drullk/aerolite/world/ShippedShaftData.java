package us.drullk.aerolite.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The shipped shaft data registry entries, read from the generated resources. Tests read the default settings from them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
public final class ShippedShaftData {

    public static final List<String> VARIATIONS = List.of("stone", "mossy", "tuff", "deepslate", "copper", "mud", "andesite", "diorite", "granite",
            "nether", "blackstone", "soul", "quartz", "end", "purpur");
    public static final List<String> LISTS = List.of("overworld_pillars", "nether_pillars", "end_pillars");
    public static final List<ResourceLocation> PLACEMENTS = List.of(ShaftRegistries.DEFAULT_PLACEMENT.location(),
            ResourceLocation.withDefaultNamespace("overworld"), ResourceLocation.withDefaultNamespace("the_nether"), ResourceLocation.withDefaultNamespace("the_end"));

    private static final HolderLookup.Provider REGISTRIES = new RegistrySetBuilder()
            .add(ShaftRegistries.SHAFT_VARIATION, context -> VARIATIONS.forEach(name -> register(context, ShaftRegistries.SHAFT_VARIATION, ShaftRegistries.id(name), ShaftVariation.CODEC)))
            .add(ShaftRegistries.VARIATION_LIST, context -> LISTS.forEach(name -> register(context, ShaftRegistries.VARIATION_LIST, ShaftRegistries.id(name), VariationList.CODEC)))
            .add(ShaftRegistries.SHAFT_PLACEMENT, context -> PLACEMENTS.forEach(id -> register(context, ShaftRegistries.SHAFT_PLACEMENT, id, ShaftPlacement.CODEC)))
            .build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

    private ShippedShaftData() {
    }

    public static HolderLookup.Provider registries() {
        return REGISTRIES;
    }

    public static ShaftLookup lookup() {
        return ShaftLookup.of(REGISTRIES);
    }

    /** JSON ops that see the shipped registries. */
    public static RegistryOps<JsonElement> ops() {
        return REGISTRIES.createSerializationContext(JsonOps.INSTANCE);
    }

    /** The shipped variation of a name. */
    public static ShaftVariation variation(String name) {
        return lookup().variations().getOrThrow(ResourceKey.create(ShaftRegistries.SHAFT_VARIATION, ShaftRegistries.id(name))).value();
    }

    private static <T> void register(BootstrapContext<T> context, ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Codec<T> codec) {
        context.register(ResourceKey.create(registry, id), read(codec, "data/" + id.getNamespace() + "/" + Registries.elementsDirPath(registry) + "/" + id.getPath() + ".json"));
    }

    /** Reads one generated JSON file. */
    public static <T> T read(Codec<T> codec, String path) {
        try (InputStream stream = ShippedShaftData.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("The generated resource " + path + " does not exist");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

}
