package us.drullk.aerolite.datagen;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.GsonHelper;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.VariationList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the entries of {@code aerolite:variation_list}. The key sequence of each file is the pick sequence, so this writer keeps it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public class AeroliteVariationListProvider implements DataProvider {

    private final PackOutput.PathProvider paths;
    private final Map<ResourceKey<VariationList>, VariationList> lists;

    public AeroliteVariationListProvider(PackOutput output, Map<ResourceKey<VariationList>, VariationList> lists) {
        this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, Registries.elementsDirPath(ShaftRegistries.VARIATION_LIST));
        this.lists = lists;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return CompletableFuture.allOf(this.lists.entrySet().stream()
                .map(entry -> save(output, VariationList.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow(), this.paths.json(entry.getKey().location())))
                .toArray(CompletableFuture[]::new));
    }

    // [VANILLACOPY] DataProvider#saveStable, without the key sort
    private static CompletableFuture<?> save(CachedOutput output, JsonElement json, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                HashingOutputStream hashing = new HashingOutputStream(Hashing.sha1(), bytes);
                try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(hashing, StandardCharsets.UTF_8))) {
                    writer.setSerializeNulls(false);
                    writer.setIndent("  ");
                    GsonHelper.writeValue(writer, json, null);
                }
                output.writeIfNeeded(path, bytes.toByteArray(), hashing.hash());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, Util.backgroundExecutor());
    }

    @Override
    public String getName() {
        return Aerolite.MODID + " variation lists";
    }

}
