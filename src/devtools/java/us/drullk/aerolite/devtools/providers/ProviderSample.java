package us.drullk.aerolite.devtools.providers;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import us.drullk.aerolite.client.BlockSearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One block field of the sample variation. It holds the values of every provider type, so a type change keeps them.
 * The default values are the values of {@code prototypes/ui/providers.js}.
 * It is in-memory sample data only. It builds no vanilla provider.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProviderSample {

    public static final int THRESHOLD_STEPS = 40;
    public static final int HIGH_CHANCE_STEPS = 20;
    public static final int MAX_VARIETY = 64;
    public static final int MAX_WEIGHT = 16;

    public ProviderType type;

    public BlockState simpleState = Blocks.STONE_BRICKS.defaultBlockState();

    public List<WeightedState> weighted = new ArrayList<>(List.of(
            new WeightedState(Blocks.STONE_BRICKS.defaultBlockState(), 3),
            new WeightedState(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 1),
            new WeightedState(Blocks.ANDESITE.defaultBlockState(), 1)));

    public NoiseSample noise = new NoiseSample(2345, -3, "1.0, 1.0", 4);
    public List<BlockState> noiseStates = states(Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState());

    public NoiseSample thresholdNoise = new NoiseSample(2345, -3, "1.0", 3);
    /** The threshold is {@code -1 + 0.05 * thresholdStep}. */
    public int thresholdStep = 4;
    /** The high chance is {@code 0.05 * highChanceStep}. */
    public int highChanceStep = 7;
    public BlockState defaultState = Blocks.STONE_BRICKS.defaultBlockState();
    public List<BlockState> lowStates = states(Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
    public List<BlockState> highStates = states(Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState());

    public NoiseSample dualNoise = new NoiseSample(2345, -3, "1.0", 5);
    public List<BlockState> dualStates = states(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState());
    public int varietyMin = 1;
    public int varietyMax = 3;
    public NoiseSample slowNoise = new NoiseSample(0, -10, "1.0", 9);

    public BlockState rotatedBlock = Blocks.OAK_LOG.defaultBlockState();

    public BlockState randomSource = Blocks.SEA_PICKLE.defaultBlockState();
    public String randomProperty = "pickles";
    public boolean randomConstant;
    public int randomMin = 1;
    public int randomMax = 4;
    public int randomValue = 2;

    public ProviderSample(ProviderType type) {
        this.type = type;
    }

    private static List<BlockState> states(BlockState... states) {
        List<BlockState> list = new ArrayList<>();
        Collections.addAll(list, states);
        return list;
    }

    public ProviderSample copy() {
        ProviderSample copy = new ProviderSample(this.type);
        copy.simpleState = this.simpleState;
        copy.weighted = new ArrayList<>(this.weighted.stream().map(WeightedState::copy).toList());
        copy.noise = this.noise.copy();
        copy.noiseStates = new ArrayList<>(this.noiseStates);
        copy.thresholdNoise = this.thresholdNoise.copy();
        copy.thresholdStep = this.thresholdStep;
        copy.highChanceStep = this.highChanceStep;
        copy.defaultState = this.defaultState;
        copy.lowStates = new ArrayList<>(this.lowStates);
        copy.highStates = new ArrayList<>(this.highStates);
        copy.dualNoise = this.dualNoise.copy();
        copy.dualStates = new ArrayList<>(this.dualStates);
        copy.varietyMin = this.varietyMin;
        copy.varietyMax = this.varietyMax;
        copy.slowNoise = this.slowNoise.copy();
        copy.rotatedBlock = this.rotatedBlock;
        copy.randomSource = this.randomSource;
        copy.randomProperty = this.randomProperty;
        copy.randomConstant = this.randomConstant;
        copy.randomMin = this.randomMin;
        copy.randomMax = this.randomMax;
        copy.randomValue = this.randomValue;
        return copy;
    }

    /** The one-line summary of the mock without the type name. */
    public String summary(BlockSearch blocks) {
        return switch (this.type) {
            case SIMPLE -> blocks.text(this.simpleState);
            case WEIGHTED -> this.weighted.stream().map(entry -> entry.weight + " " + blocks.text(entry.state)).collect(Collectors.joining(", "));
            case NOISE -> join(blocks, this.noiseStates);
            case NOISE_THRESHOLD -> blocks.text(this.defaultState) + " | " + this.lowStates.size() + " low | " + this.highStates.size() + " high";
            case DUAL_NOISE -> join(blocks, this.dualStates);
            case ROTATED -> blocks.text(this.rotatedBlock);
            case RANDOMIZED_INT -> blocks.text(this.randomSource) + " " + this.randomProperty + " "
                    + (this.randomConstant ? Integer.toString(this.randomValue) : this.randomMin + "-" + this.randomMax);
        };
    }

    /** The type name, a colon and the summary, like a closed field of the mock. */
    public String typedSummary(BlockSearch blocks) {
        return this.type.displayName() + ": " + this.summary(blocks);
    }

    private static String join(BlockSearch blocks, List<BlockState> states) {
        return states.stream().map(blocks::text).collect(Collectors.joining(" / "));
    }

    /** The integer properties of the state, such as {@code pickles} of a sea pickle. */
    public static List<IntegerProperty> intProperties(BlockState state) {
        List<IntegerProperty> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer) {
                properties.add(integer);
            }
        }
        return properties;
    }

    /** The smallest value of the property. */
    public static int min(IntegerProperty property) {
        return property.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    /** The largest value of the property. */
    public static int max(IntegerProperty property) {
        return property.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** One entry of a weighted provider. */
    public static final class WeightedState {

        public BlockState state;
        public int weight;

        public WeightedState(BlockState state, int weight) {
            this.state = state;
            this.weight = weight;
        }

        WeightedState copy() {
            return new WeightedState(this.state, this.weight);
        }
    }

}
