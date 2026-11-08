package us.drullk.aerolite.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.drullk.aerolite.world.island.GraphSettings;
import us.drullk.aerolite.world.island.IslandPack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the folder of the island pack around a reload of the world creation context, with a fake reload step.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
class IslandPackApplierTest {

    private static final String ENTRY = "data/aerolite/worldgen/density_function/islands/overworld.json";

    private final IslandPack pack = new IslandPack();
    private final IslandPackApplier applier = new IslandPackApplier(this.pack, new GraphSettings());

    private static Map<String, JsonElement> files(int cellSize) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "aerolite:voronoi_islands");
        entry.addProperty("cell_size", cellSize);
        JsonObject meta = new JsonObject();
        meta.addProperty("pack", "probe");
        Map<String, JsonElement> files = new LinkedHashMap<>();
        files.put(IslandPack.PACK_META, meta);
        files.put(ENTRY, entry);
        return files;
    }

    @Test
    void failedReloadPutsTheOldPackBack(@TempDir Path folder) throws IOException {
        Map<String, JsonElement> old = files(256);
        this.pack.write(folder, old);
        AtomicBoolean returned = new AtomicBoolean();
        AtomicReference<Map<String, JsonElement>> during = new AtomicReference<>();
        boolean started = this.applier.writeAndReload(folder, files(512), (withPack, onFailure) -> {
            assertTrue(withPack, "A written pack reloads without the pack");
            during.set(this.pack.read(folder.resolve(IslandPack.NAME)));
            onFailure.run();
        }, () -> returned.set(true));
        assertTrue(started, "A changed pack started no reload");
        assertEquals(files(512), during.get(), "The reload did not see the new pack");
        assertTrue(returned.get(), "The failure did not return to the create world screen");
        assertEquals(old, this.pack.read(folder.resolve(IslandPack.NAME)), "The folder does not hold the old pack again");
    }

    @Test
    void failedReloadWithoutAnOldPackRemovesTheNewPack(@TempDir Path folder) {
        boolean started = this.applier.writeAndReload(folder, files(512), (withPack, onFailure) -> onFailure.run(), () -> {
        });
        assertTrue(started, "A new pack started no reload");
        assertFalse(Files.exists(folder.resolve(IslandPack.NAME)), "The failure left the new pack in the folder");
    }

    @Test
    void failedRemovalPutsTheOldPackBack(@TempDir Path folder) throws IOException {
        Map<String, JsonElement> old = files(256);
        this.pack.write(folder, old);
        boolean started = this.applier.writeAndReload(folder, Map.of(), (withPack, onFailure) -> {
            assertFalse(withPack, "A removed pack reloads with the pack");
            onFailure.run();
        }, () -> {
        });
        assertTrue(started, "A removed pack started no reload");
        assertEquals(old, this.pack.read(folder.resolve(IslandPack.NAME)), "The folder does not hold the old pack again");
    }

    @Test
    void goodReloadKeepsTheNewPack(@TempDir Path folder) throws IOException {
        this.pack.write(folder, files(256));
        AtomicBoolean returned = new AtomicBoolean();
        this.applier.writeAndReload(folder, files(512), (withPack, onFailure) -> {
        }, () -> returned.set(true));
        assertFalse(returned.get(), "A good reload returned to the create world screen");
        assertEquals(files(512), this.pack.read(folder.resolve(IslandPack.NAME)));
    }

    @Test
    void unchangedFolderStartsNoReload(@TempDir Path folder) {
        assertFalse(this.applier.writeAndReload(folder, Map.of(), (withPack, onFailure) -> {
            throw new AssertionError("An unchanged folder reloaded");
        }, () -> {
        }));
    }

}
