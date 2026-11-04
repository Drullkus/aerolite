package us.drullk.aerolite.client.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RandomizedIntStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RotatedBlockProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.StateProviders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that each draft builds the provider that its values describe, that the provider round-trips through {@link StateProviders#CODEC},
 * and that a type change carries the states over.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ProviderDraftTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final NormalNoise.NoiseParameters NOISE = new NormalNoise.NoiseParameters(-4, 1.0, 0.5);
    private static final NormalNoise.NoiseParameters SLOW = new NormalNoise.NoiseParameters(-9, 1.0);

    private static BlockStateProvider build(ProviderDraft draft) {
        return draft.build().getOrThrow();
    }

    private static JsonObject json(BlockStateProvider provider) {
        return BlockStateProvider.CODEC.encodeStart(JsonOps.INSTANCE, provider).getOrThrow().getAsJsonObject();
    }

    /** The provider survives one write and read through the variation field codec, and a draft of it builds the same provider. */
    private static void assertRoundTrip(BlockStateProvider provider) {
        JsonElement written = StateProviders.CODEC.encodeStart(JsonOps.INSTANCE, provider).getOrThrow();
        BlockStateProvider read = StateProviders.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow();
        assertTrue(StateProviders.same(provider, read), () -> "Codec round trip changed " + written);
        BlockStateProvider rebuilt = build(ProviderDraft.of(provider));
        assertTrue(StateProviders.same(provider, rebuilt), () -> "Draft round trip changed " + written + " into " + json(rebuilt));
    }

    @Test
    void everyBlankDraftBuildsItsType() {
        for (ProviderType type : ProviderType.EDITABLE) {
            BlockStateProvider built = build(ProviderDraft.blank(type));
            assertEquals(type, ProviderType.of(built));
            assertEquals(type.id(), StateProviders.typeId(built));
            assertRoundTrip(built);
        }
    }

    @Test
    void simpleDraftBuildsItsState() {
        ProviderDraft draft = ProviderDraft.blank(ProviderType.SIMPLE);
        draft.state = TUFF;
        BlockStateProvider built = build(draft);
        assertSame(TUFF, StateProviders.simpleState(built));
        assertRoundTrip(built);
    }

    @Test
    void weightedDraftBuildsItsEntries() {
        ProviderDraft draft = ProviderDraft.blank(ProviderType.WEIGHTED);
        draft.entries.clear();
        draft.entries.add(new ProviderDraft.Entry(STONE, 5));
        draft.entries.add(new ProviderDraft.Entry(ANDESITE, 2));
        BlockStateProvider built = build(draft);
        WeightedStateProvider expected = new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder().add(STONE, 5).add(ANDESITE, 2));
        assertTrue(StateProviders.same(expected, built));
        assertRoundTrip(built);
        ProviderDraft read = ProviderDraft.of(expected);
        assertEquals(ProviderType.WEIGHTED, read.type);
        assertEquals(List.of(5, 2), read.entries.stream().map(entry -> entry.weight).toList());
    }

    @Test
    void noiseDraftBuildsItsValues() {
        ProviderDraft draft = ProviderDraft.blank(ProviderType.NOISE);
        draft.seed = 77L;
        draft.noise.firstOctave = -4;
        draft.noise.amplitudes.clear();
        draft.noise.amplitudes.addAll(List.of(1.0, 0.5));
        draft.noise.scale = 0.125F;
        draft.states.clear();
        draft.states.addAll(List.of(STONE, ANDESITE));
        BlockStateProvider built = build(draft);
        assertTrue(StateProviders.same(new NoiseProvider(77L, NOISE, 0.125F, List.of(STONE, ANDESITE)), built));
        assertRoundTrip(built);
    }

    @Test
    void noiseThresholdDraftBuildsItsValues() {
        NoiseThresholdProvider expected = new NoiseThresholdProvider(9L, NOISE, 0.05F, 0.25F, 0.5F, STONE, List.of(ANDESITE), List.of(TUFF, STONE));
        ProviderDraft draft = ProviderDraft.of(expected);
        assertEquals(ProviderType.NOISE_THRESHOLD, draft.type);
        assertEquals(0.25F, draft.threshold);
        assertEquals(0.5F, draft.highChance);
        assertEquals(List.of(TUFF, STONE), draft.highStates);
        assertTrue(StateProviders.same(expected, build(draft)));
        assertRoundTrip(expected);
    }

    @Test
    void dualNoiseDraftBuildsItsValues() {
        DualNoiseProvider expected = new DualNoiseProvider(new InclusiveRange<>(2, 5), SLOW, 0.5F, 3L, NOISE, 0.02F, List.of(STONE, ANDESITE, TUFF));
        ProviderDraft draft = ProviderDraft.of(expected);
        assertEquals(ProviderType.DUAL_NOISE, draft.type);
        assertEquals(2, draft.varietyMin);
        assertEquals(5, draft.varietyMax);
        assertEquals(-9, draft.slowNoise.firstOctave);
        assertEquals(0.5F, draft.slowNoise.scale);
        assertTrue(StateProviders.same(expected, build(draft)));
        assertRoundTrip(expected);
    }

    @Test
    void rotatedDraftBuildsItsBlock() {
        ProviderDraft draft = ProviderDraft.blank(ProviderType.ROTATED);
        draft.rotatedBlock = Blocks.BIRCH_LOG;
        assertTrue(StateProviders.same(new RotatedBlockProvider(Blocks.BIRCH_LOG), build(draft)));
        assertRoundTrip(build(draft));
    }

    @Test
    void randomizedIntDraftBuildsItsValues() {
        RandomizedIntStateProvider uniform = new RandomizedIntStateProvider(BlockStateProvider.simple(Blocks.SEA_PICKLE), SeaPickleBlock.PICKLES, UniformInt.of(2, 3));
        ProviderDraft draft = ProviderDraft.of(uniform);
        assertEquals(ProviderType.RANDOMIZED_INT, draft.type);
        assertEquals("pickles", draft.property);
        assertEquals(ProviderDraft.Values.UNIFORM, draft.values);
        assertEquals(2, draft.min);
        assertEquals(3, draft.max);
        assertTrue(StateProviders.same(uniform, build(draft)));
        assertRoundTrip(uniform);

        draft.values = ProviderDraft.Values.CONSTANT;
        draft.constant = 4;
        assertTrue(StateProviders.same(
                new RandomizedIntStateProvider(BlockStateProvider.simple(Blocks.SEA_PICKLE), SeaPickleBlock.PICKLES, ConstantInt.of(4)), build(draft)));

        draft.constant = 9;
        assertTrue(draft.build().isError(), "A value outside the property range must not build");
    }

    @Test
    void otherIntProviderAndOtherSourceStayAsTheyAre() {
        RandomizedIntStateProvider other = new RandomizedIntStateProvider(
                new WeightedStateProvider(SimpleWeightedRandomList.single(Blocks.SEA_PICKLE.defaultBlockState())), "pickles", BiasedToBottomInt.of(1, 3));
        ProviderDraft draft = ProviderDraft.of(other);
        assertEquals(ProviderDraft.Values.OTHER, draft.values);
        assertEquals(null, draft.sourceState());
        assertRoundTrip(other);
    }

    @Test
    void badValuesDoNotBuild() {
        ProviderDraft zeroScale = ProviderDraft.blank(ProviderType.NOISE);
        zeroScale.noise.scale = 0;
        assertTrue(zeroScale.build().isError());
        ProviderDraft highThreshold = ProviderDraft.blank(ProviderType.NOISE_THRESHOLD);
        highThreshold.threshold = 2;
        assertTrue(highThreshold.build().isError());
        ProviderDraft noEntries = ProviderDraft.blank(ProviderType.WEIGHTED);
        noEntries.entries.clear();
        assertTrue(noEntries.build().isError());
        ProviderDraft noStates = ProviderDraft.blank(ProviderType.DUAL_NOISE);
        noStates.states.clear();
        assertTrue(noStates.build().isError());
    }

    @Test
    void simpleStateBecomesTheFirstWeightedEntry() {
        ProviderDraft draft = ProviderDraft.of(BlockStateProvider.simple(TUFF));
        draft.changeType(ProviderType.WEIGHTED);
        assertEquals(1, draft.entries.size());
        assertSame(TUFF, draft.entries.getFirst().state);
        assertEquals(1, draft.entries.getFirst().weight);
        draft.changeType(ProviderType.SIMPLE);
        assertSame(TUFF, draft.state);
    }

    @Test
    void weightedEntriesBecomeNoiseStates() {
        ProviderDraft draft = ProviderDraft.of(new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder().add(STONE, 3).add(ANDESITE, 1).add(TUFF, 1)));
        draft.changeType(ProviderType.NOISE);
        assertEquals(List.of(STONE, ANDESITE, TUFF), draft.states);
        draft.changeType(ProviderType.NOISE_THRESHOLD);
        assertSame(STONE, draft.defaultState);
        assertEquals(List.of(ANDESITE), draft.lowStates);
        assertEquals(List.of(TUFF), draft.highStates);
    }

    @Test
    void noiseValuesStayAcrossNoiseTypes() {
        ProviderDraft draft = ProviderDraft.of(new NoiseProvider(77L, NOISE, 0.125F, List.of(STONE, ANDESITE)));
        draft.changeType(ProviderType.DUAL_NOISE);
        assertEquals(77L, draft.seed);
        assertEquals(0.125F, draft.noise.scale);
        assertEquals(List.of(STONE, ANDESITE), draft.states);
        BlockStateProvider built = build(draft);
        assertEquals(ProviderType.DUAL_NOISE, ProviderType.of(built));
        assertRoundTrip(built);
    }

    @Test
    void typeChangePicksFittingStates() {
        ProviderDraft draft = ProviderDraft.of(new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder()
                .add(STONE, 1).add(Blocks.OAK_LOG.defaultBlockState(), 1).add(Blocks.SEA_PICKLE.defaultBlockState(), 1)));
        ProviderDraft rotated = ProviderDraft.of(build(draft));
        rotated.changeType(ProviderType.ROTATED);
        assertSame(Blocks.OAK_LOG, rotated.rotatedBlock);
        draft.changeType(ProviderType.RANDOMIZED_INT);
        assertSame(Blocks.SEA_PICKLE.defaultBlockState(), draft.sourceState());
        assertEquals("pickles", draft.property);
        BlockStateProvider built = build(draft);
        assertEquals(ProviderType.RANDOMIZED_INT, ProviderType.of(built));
        assertRoundTrip(built);
    }

}
