package us.drullk.aerolite.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the codec, the comparison and the type id of the block state providers of a variation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 */
class StateProvidersTest {

    /** A shipped variation in the 0.3 format, with bare block states. */
    private static final String BARE = """
            {"bias": 0.5, "decay_scale": 64, "decay_threshold": 0.35, "erosion_threshold": 0.5,
             "interior": {"Name": "minecraft:stone"}, "junction": {"Name": "minecraft:stone"},
             "path": {"Name": "minecraft:cobblestone"}, "wall": {"Name": "minecraft:stone_bricks"}}""";

    /** The same variation with provider objects. */
    private static final String PROVIDERS = """
            {"bias": 0.5, "decay_scale": 64, "decay_threshold": 0.35, "erosion_threshold": 0.5,
             "interior": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:stone"}},
             "junction": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:stone"}},
             "wall": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:stone_bricks"}},
             "path": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:cobblestone"}}}""";

    private static final ShaftVariation STONE = ShaftVariation.standard(ShaftVariation.UNIFORM_BIAS,
            BlockStateProvider.simple(Blocks.STONE), BlockStateProvider.simple(Blocks.STONE_BRICKS), BlockStateProvider.simple(Blocks.COBBLESTONE));

    private static ShaftVariation parse(String json) {
        return ShaftVariation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static WeightedStateProvider weighted(int stoneWeight, int cobblestoneWeight) {
        return new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder()
                .add(Blocks.STONE.defaultBlockState(), stoneWeight)
                .add(Blocks.COBBLESTONE.defaultBlockState(), cobblestoneWeight));
    }

    @Test
    void bareStatesLoadLikeProviderObjects() {
        assertEquals(STONE, parse(BARE));
        assertEquals(parse(PROVIDERS), parse(BARE));
    }

    @Test
    void bareStateWithoutJunctionTakesTheInterior() {
        JsonObject object = JsonParser.parseString(BARE).getAsJsonObject();
        object.remove("junction");
        ShaftVariation parsed = ShaftVariation.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow();
        assertEquals(STONE, parsed);
        assertTrue(StateProviders.same(parsed.interior(), parsed.junction()));
    }

    @Test
    void codecWritesProviderObjectsInTheBlockKeySequence() {
        JsonObject written = ShaftVariation.CODEC.encodeStart(JsonOps.INSTANCE, parse(BARE)).getOrThrow().getAsJsonObject();
        List<String> blockKeys = written.keySet().stream().filter(List.of("interior", "junction", "wall", "path")::contains).toList();
        assertEquals(List.of("interior", "junction", "wall", "path"), blockKeys);
        JsonElement wall = written.get("wall");
        assertEquals("minecraft:simple_state_provider", wall.getAsJsonObject().get("type").getAsString());
        assertEquals("minecraft:stone_bricks", wall.getAsJsonObject().getAsJsonObject("state").get("Name").getAsString());
    }

    @Test
    void equalSimpleProvidersAreSame() {
        BlockStateProvider first = BlockStateProvider.simple(Blocks.STONE);
        BlockStateProvider second = BlockStateProvider.simple(Blocks.STONE.defaultBlockState());
        assertTrue(StateProviders.same(first, second));
        assertEquals(StateProviders.hash(first), StateProviders.hash(second));
    }

    @Test
    void differentSimpleProvidersAreNotSame() {
        assertFalse(StateProviders.same(BlockStateProvider.simple(Blocks.STONE), BlockStateProvider.simple(Blocks.COBBLESTONE)));
        assertNotEquals(STONE, STONE.withWall(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()));
    }

    @Test
    void otherProvidersCompareByJson() {
        assertTrue(StateProviders.same(weighted(1, 2), weighted(1, 2)));
        assertEquals(StateProviders.hash(weighted(1, 2)), StateProviders.hash(weighted(1, 2)));
        assertFalse(StateProviders.same(weighted(1, 2), weighted(2, 1)));
        assertFalse(StateProviders.same(weighted(1, 2), BlockStateProvider.simple(Blocks.STONE)));
        assertEquals(STONE.withPath(weighted(1, 2)), STONE.withPath(weighted(1, 2)));
    }

    @Test
    void equalVariationsFindOneMapKey() {
        Map<ShaftVariation, String> map = new HashMap<>();
        map.put(STONE, "stone");
        assertEquals("stone", map.get(parse(BARE)));
        map.put(STONE.withPath(weighted(1, 2)), "weighted");
        assertEquals("weighted", map.get(STONE.withPath(weighted(1, 2))));
    }

    @Test
    void typeIdAndSimpleState() {
        assertEquals("minecraft:weighted_state_provider", StateProviders.typeId(weighted(1, 1)));
        assertEquals("minecraft:simple_state_provider", StateProviders.typeId(BlockStateProvider.simple(Blocks.STONE)));
        assertNull(StateProviders.simpleState(weighted(1, 1)));
        assertEquals(Blocks.STONE.defaultBlockState(), StateProviders.simpleState(BlockStateProvider.simple(Blocks.STONE)));
    }

}
