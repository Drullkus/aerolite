package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * One set of shaft blocks and maze bias. Each shaft and each column picks one variation by weight.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 *
 * @param weight   the pick weight among the variations of one dimension
 * @param bias     the weight of axis edges in the maze sampler; ring edges get one minus this weight
 * @param interior the shaft interior block
 * @param wall     the maze wall block
 * @param path     the maze path block
 */
public record ShaftVariation(int weight, float bias, BlockState interior, BlockState wall, BlockState path) {

    public static final int MAX_WEIGHT = 16;
    public static final float MIN_BIAS = 0.05F;
    public static final float MAX_BIAS = 0.95F;
    public static final float UNIFORM_BIAS = 0.5F;

    public static final ShaftVariation STONE = new ShaftVariation(7, UNIFORM_BIAS,
            Blocks.STONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
    public static final ShaftVariation TRIAL_CHAMBER = new ShaftVariation(1, UNIFORM_BIAS,
            Blocks.TUFF.defaultBlockState(), Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.OXIDIZED_COPPER.defaultBlockState());
    public static final ShaftVariation NETHER = new ShaftVariation(1, UNIFORM_BIAS,
            Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState());
    public static final ShaftVariation END = new ShaftVariation(1, UNIFORM_BIAS,
            Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState());

    /** A bias outside the slider range clamps to the range. */
    private static final Codec<Float> BIAS_CODEC = Codec.FLOAT.xmap(ShaftVariation::clampBias, bias -> bias);

    public static final Codec<ShaftVariation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("weight", 1).forGetter(ShaftVariation::weight),
            BIAS_CODEC.optionalFieldOf("bias", UNIFORM_BIAS).forGetter(ShaftVariation::bias),
            BlockState.CODEC.fieldOf("interior").forGetter(ShaftVariation::interior),
            BlockState.CODEC.fieldOf("wall").forGetter(ShaftVariation::wall),
            BlockState.CODEC.fieldOf("path").forGetter(ShaftVariation::path)
    ).apply(instance, ShaftVariation::new));

    /** The default variation list of a dimension. A dimension other than the three vanilla ones gets stone with weight 1. */
    public static List<ShaftVariation> defaultList(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return List.of(STONE, TRIAL_CHAMBER);
        }
        if (Level.NETHER.equals(dimension)) {
            return List.of(NETHER);
        }
        if (Level.END.equals(dimension)) {
            return List.of(END);
        }
        return List.of(STONE.withWeight(1));
    }

    public static float clampBias(float bias) {
        return Mth.clamp(bias, MIN_BIAS, MAX_BIAS);
    }

    public ShaftVariation withWeight(int weight) {
        return new ShaftVariation(weight, this.bias, this.interior, this.wall, this.path);
    }

    public ShaftVariation withBias(float bias) {
        return new ShaftVariation(this.weight, bias, this.interior, this.wall, this.path);
    }

    public ShaftVariation withInterior(BlockState interior) {
        return new ShaftVariation(this.weight, this.bias, interior, this.wall, this.path);
    }

    public ShaftVariation withWall(BlockState wall) {
        return new ShaftVariation(this.weight, this.bias, this.interior, wall, this.path);
    }

    public ShaftVariation withPath(BlockState path) {
        return new ShaftVariation(this.weight, this.bias, this.interior, this.wall, path);
    }

}
