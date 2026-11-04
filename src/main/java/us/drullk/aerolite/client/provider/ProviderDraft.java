package us.drullk.aerolite.client.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RandomizedIntStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RotatedBlockProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.aerolite.world.StateProviders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The editable values of one block state provider. It holds the values of every type, so a type change keeps them.
 * It holds no widget. The vanilla editor and the panel editor both edit it.
 * {@link #of} reads a provider through the provider codec. {@link #build} makes the provider that the values describe.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProviderDraft {

    public static final int MIN_VARIETY = 1;
    public static final int MAX_VARIETY = 64;
    private static final BlockState FALLBACK = Blocks.STONE.defaultBlockState();

    public ProviderType type = ProviderType.SIMPLE;

    public BlockState state = Blocks.STONE_BRICKS.defaultBlockState();

    public final List<Entry> entries = new ArrayList<>(List.of(
            new Entry(Blocks.STONE_BRICKS.defaultBlockState(), 3),
            new Entry(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 1),
            new Entry(Blocks.ANDESITE.defaultBlockState(), 1)));

    /** The seed of the noise types. */
    public long seed = 2345L;
    /** The noise of the noise types. */
    public final Noise noise = new Noise(-3, List.of(1.0), 0.02F);
    /** The states of a noise provider and of a dual noise provider. */
    public final List<BlockState> states = new ArrayList<>(List.of(
            Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState()));

    public float threshold = -0.8F;
    public float highChance = 0.35F;
    public BlockState defaultState = Blocks.STONE_BRICKS.defaultBlockState();
    public final List<BlockState> lowStates = new ArrayList<>(List.of(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()));
    public final List<BlockState> highStates = new ArrayList<>(List.of(Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()));

    public int varietyMin = 1;
    public int varietyMax = 3;
    /** The slow noise of a dual noise provider. Its scale is the slow scale. */
    public final Noise slowNoise = new Noise(-10, List.of(1.0), 1.0F);

    public Block rotatedBlock = Blocks.OAK_LOG;

    /** The source of a randomized int provider. Only a simple source has an editor. */
    public BlockStateProvider source = BlockStateProvider.simple(Blocks.SEA_PICKLE);
    public String property = "pickles";
    public Values values = Values.UNIFORM;
    public int constant = 2;
    public int min = 1;
    public int max = 4;
    /** The int provider of the values when it is neither constant nor uniform. */
    @Nullable
    public IntProvider otherValues;

    /** The provider of the type {@link ProviderType#OTHER}. The draft keeps it as it is. */
    @Nullable
    public BlockStateProvider other;

    /** The kinds of int provider that the values of a randomized int provider can be. */
    public enum Values {
        CONSTANT,
        UNIFORM,
        /** Any other int provider. It has no editor. */
        OTHER
    }

    /** One entry of a weighted provider. */
    public static final class Entry {

        public BlockState state;
        public int weight;

        public Entry(BlockState state, int weight) {
            this.state = state;
            this.weight = weight;
        }
    }

    /** The first octave, the amplitudes and the scale of one noise. */
    public static final class Noise {

        public int firstOctave;
        public final List<Double> amplitudes;
        public float scale;

        Noise(int firstOctave, List<Double> amplitudes, float scale) {
            this.firstOctave = firstOctave;
            this.amplitudes = new ArrayList<>(amplitudes);
            this.scale = scale;
        }

        NormalNoise.NoiseParameters parameters() {
            return new NormalNoise.NoiseParameters(this.firstOctave, this.amplitudes);
        }

        void read(NormalNoise.NoiseParameters parameters) {
            this.firstOctave = parameters.firstOctave();
            this.amplitudes.clear();
            this.amplitudes.addAll(parameters.amplitudes());
        }
    }

    /** A draft of the type with the default values. */
    public static ProviderDraft blank(ProviderType type) {
        ProviderDraft draft = new ProviderDraft();
        draft.type = type;
        return draft;
    }

    /** A draft with the values of the provider. A provider that the codec cannot read gives the type {@link ProviderType#OTHER}. */
    public static ProviderDraft of(BlockStateProvider provider) {
        ProviderDraft draft = new ProviderDraft();
        BlockState simple = StateProviders.simpleState(provider);
        if (simple != null) {
            draft.state = simple;
            return draft;
        }
        try {
            JsonObject json = BlockStateProvider.CODEC.encodeStart(JsonOps.INSTANCE, provider).getOrThrow().getAsJsonObject();
            draft.type = ProviderType.byId(json.get("type").getAsString());
            draft.read(json);
        } catch (RuntimeException exception) {
            draft.type = ProviderType.OTHER;
        }
        if (draft.type == ProviderType.OTHER) {
            draft.other = provider;
        }
        return draft;
    }

    private static <T> T parse(Codec<T> codec, JsonElement json) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    /** Reads the fields of the type from the JSON of the provider codec. */
    private void read(JsonObject json) {
        switch (this.type) {
            case WEIGHTED -> {
                this.entries.clear();
                for (WeightedEntry.Wrapper<BlockState> entry : parse(SimpleWeightedRandomList.wrappedCodec(BlockState.CODEC), json.get("entries")).unwrap()) {
                    this.entries.add(new Entry(entry.data(), entry.weight().asInt()));
                }
            }
            case NOISE -> {
                this.readNoise(json);
                replace(this.states, parse(BlockState.CODEC.listOf(), json.get("states")));
            }
            case NOISE_THRESHOLD -> {
                this.readNoise(json);
                this.threshold = json.get("threshold").getAsFloat();
                this.highChance = json.get("high_chance").getAsFloat();
                this.defaultState = parse(BlockState.CODEC, json.get("default_state"));
                replace(this.lowStates, parse(BlockState.CODEC.listOf(), json.get("low_states")));
                replace(this.highStates, parse(BlockState.CODEC.listOf(), json.get("high_states")));
            }
            case DUAL_NOISE -> {
                this.readNoise(json);
                replace(this.states, parse(BlockState.CODEC.listOf(), json.get("states")));
                InclusiveRange<Integer> variety = parse(InclusiveRange.codec(Codec.INT), json.get("variety"));
                this.varietyMin = variety.minInclusive();
                this.varietyMax = variety.maxInclusive();
                this.slowNoise.read(parse(NormalNoise.NoiseParameters.DIRECT_CODEC, json.get("slow_noise")));
                this.slowNoise.scale = json.get("slow_scale").getAsFloat();
            }
            case ROTATED -> this.rotatedBlock = parse(BlockState.CODEC, json.get("state")).getBlock();
            case RANDOMIZED_INT -> {
                this.source = parse(BlockStateProvider.CODEC, json.get("source"));
                this.property = json.get("property").getAsString();
                this.readValues(parse(IntProvider.CODEC, json.get("values")));
            }
            default -> {
            }
        }
    }

    private void readNoise(JsonObject json) {
        this.seed = json.get("seed").getAsLong();
        this.noise.read(parse(NormalNoise.NoiseParameters.DIRECT_CODEC, json.get("noise")));
        this.noise.scale = json.get("scale").getAsFloat();
    }

    private void readValues(IntProvider provider) {
        if (provider instanceof ConstantInt constantInt) {
            this.values = Values.CONSTANT;
            this.constant = constantInt.getValue();
        } else if (provider instanceof UniformInt) {
            this.values = Values.UNIFORM;
            this.min = provider.getMinValue();
            this.max = provider.getMaxValue();
        } else {
            this.values = Values.OTHER;
            this.otherValues = provider;
        }
    }

    private static void replace(List<BlockState> list, List<BlockState> states) {
        list.clear();
        list.addAll(states);
    }

    /**
     * Changes the type. The states of the current type carry over to the new type where they fit.
     * A simple state becomes the first entry of a weighted provider. The entries of a weighted provider become the states of a noise provider.
     * The noise values stay, so they carry over between the noise types.
     */
    public void changeType(ProviderType next) {
        if (next == this.type || next == ProviderType.OTHER) {
            return;
        }
        List<BlockState> carried = this.carriedStates();
        switch (next) {
            case SIMPLE -> this.state = carried.getFirst();
            case WEIGHTED -> {
                this.entries.clear();
                carried.forEach(carriedState -> this.entries.add(new Entry(carriedState, 1)));
            }
            case NOISE, DUAL_NOISE -> {
                if (this.type != ProviderType.NOISE && this.type != ProviderType.DUAL_NOISE) {
                    replace(this.states, carried);
                }
            }
            case NOISE_THRESHOLD -> {
                this.defaultState = carried.getFirst();
                replace(this.lowStates, List.of(carried.size() > 1 ? carried.get(1) : carried.getFirst()));
                replace(this.highStates, carried.size() > 2 ? carried.subList(2, carried.size()) : List.of(carried.getLast()));
            }
            case ROTATED -> this.rotatedBlock = carried.stream()
                    .filter(candidate -> candidate.hasProperty(BlockStateProperties.AXIS))
                    .findFirst().orElse(carried.getFirst()).getBlock();
            case RANDOMIZED_INT -> {
                BlockState source = carried.stream().filter(candidate -> !intProperties(candidate).isEmpty()).findFirst().orElse(carried.getFirst());
                this.setSourceState(source);
            }
            default -> {
            }
        }
        this.type = next;
    }

    /** The distinct states of the current type, in their sequence. The list holds at least one state. */
    public List<BlockState> carriedStates() {
        LinkedHashSet<BlockState> carried = new LinkedHashSet<>();
        switch (this.type) {
            case SIMPLE -> carried.add(this.state);
            case WEIGHTED -> this.entries.forEach(entry -> carried.add(entry.state));
            case NOISE, DUAL_NOISE -> carried.addAll(this.states);
            case NOISE_THRESHOLD -> {
                carried.add(this.defaultState);
                carried.addAll(this.lowStates);
                carried.addAll(this.highStates);
            }
            case ROTATED -> carried.add(this.rotatedBlock.defaultBlockState());
            case RANDOMIZED_INT -> {
                BlockState source = this.sourceState();
                if (source != null) {
                    carried.add(source);
                }
            }
            case OTHER -> {
                if (this.other != null) {
                    try {
                        carried.add(this.other.getState(RandomSource.create(0L), BlockPos.ZERO));
                    } catch (RuntimeException exception) {
                        // A provider that cannot sample carries no state.
                    }
                }
            }
        }
        carried.removeIf(BlockState::isAir);
        if (carried.isEmpty()) {
            carried.add(FALLBACK);
        }
        return List.copyOf(carried);
    }

    /** The state of a simple source, or null when the source is another provider type. */
    @Nullable
    public BlockState sourceState() {
        return StateProviders.simpleState(this.source);
    }

    /** Sets a simple source. The property and the values follow the integer properties of the new state. */
    public void setSourceState(BlockState source) {
        this.source = BlockStateProvider.simple(source);
        this.fitProperty();
    }

    /** The integer properties of the simple source. */
    public List<IntegerProperty> sourceProperties() {
        BlockState source = this.sourceState();
        return source == null ? List.of() : intProperties(source);
    }

    /** The integer property of the simple source with the property name, or null. */
    @Nullable
    public IntegerProperty sourceProperty() {
        return this.sourceProperties().stream().filter(candidate -> candidate.getName().equals(this.property)).findFirst().orElse(null);
    }

    /**
     * Picks the first integer property of a simple source when the source has no property of the name.
     * Then it clamps the values into the range of the property.
     */
    public void fitProperty() {
        List<IntegerProperty> properties = this.sourceProperties();
        if (properties.isEmpty()) {
            return;
        }
        IntegerProperty chosen = this.sourceProperty();
        if (chosen == null) {
            chosen = properties.getFirst();
            this.property = chosen.getName();
        }
        int low = lowest(chosen);
        int high = highest(chosen);
        this.constant = Math.clamp(this.constant, low, high);
        this.min = Math.clamp(this.min, low, high);
        this.max = Math.clamp(this.max, this.min, high);
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
    public static int lowest(IntegerProperty property) {
        return property.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    /** The largest value of the property. */
    public static int highest(IntegerProperty property) {
        return property.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** The int provider of the values. */
    public IntProvider valuesProvider() {
        return switch (this.values) {
            case CONSTANT -> ConstantInt.of(this.constant);
            case UNIFORM -> UniformInt.of(this.min, this.max);
            case OTHER -> this.otherValues != null ? this.otherValues : ConstantInt.of(this.constant);
        };
    }

    /**
     * The provider that the values describe. It fails when vanilla rejects a value, such as an empty state list or a scale of zero.
     * The provider passes one write and read through {@link StateProviders#CODEC} and one sample.
     */
    public DataResult<BlockStateProvider> build() {
        try {
            BlockStateProvider built = this.create();
            JsonElement json = BlockStateProvider.CODEC.encodeStart(JsonOps.INSTANCE, built).getOrThrow();
            StateProviders.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().getState(RandomSource.create(0L), BlockPos.ZERO);
            return DataResult.success(built);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return DataResult.error(() -> message);
        }
    }

    private BlockStateProvider create() {
        return switch (this.type) {
            case SIMPLE -> BlockStateProvider.simple(this.state);
            case WEIGHTED -> {
                require(!this.entries.isEmpty(), "A weighted provider needs an entry");
                SimpleWeightedRandomList.Builder<BlockState> builder = SimpleWeightedRandomList.builder();
                for (Entry entry : this.entries) {
                    require(entry.weight > 0, "A weight must be positive");
                    builder.add(entry.state, entry.weight);
                }
                yield new WeightedStateProvider(builder);
            }
            case NOISE -> {
                require(!this.states.isEmpty(), "A noise provider needs a state");
                yield new NoiseProvider(this.seed, this.noise.parameters(), this.noise.scale, List.copyOf(this.states));
            }
            case NOISE_THRESHOLD -> {
                require(!this.lowStates.isEmpty() && !this.highStates.isEmpty(), "A noise threshold provider needs a low state and a high state");
                yield new NoiseThresholdProvider(this.seed, this.noise.parameters(), this.noise.scale, this.threshold, this.highChance,
                        this.defaultState, List.copyOf(this.lowStates), List.copyOf(this.highStates));
            }
            case DUAL_NOISE -> {
                require(!this.states.isEmpty(), "A dual noise provider needs a state");
                yield new DualNoiseProvider(new InclusiveRange<>(this.varietyMin, this.varietyMax), this.slowNoise.parameters(), this.slowNoise.scale,
                        this.seed, this.noise.parameters(), this.noise.scale, List.copyOf(this.states));
            }
            case ROTATED -> new RotatedBlockProvider(this.rotatedBlock);
            case RANDOMIZED_INT -> {
                IntProvider provider = this.valuesProvider();
                IntegerProperty known = this.sourceProperty();
                if (known != null) {
                    require(known.getPossibleValues().contains(provider.getMinValue()) && known.getPossibleValues().contains(provider.getMaxValue()),
                            "The values leave the range of " + this.property);
                }
                yield new RandomizedIntStateProvider(this.source, this.property, provider);
            }
            case OTHER -> Optional.ofNullable(this.other).orElseThrow(() -> new IllegalStateException("No provider"));
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

}
