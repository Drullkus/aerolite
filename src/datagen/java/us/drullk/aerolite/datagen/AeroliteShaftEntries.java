package us.drullk.aerolite.datagen;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.*;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shipped entries of the shaft data registries: the variations, the variation list of each vanilla dimension and the placements.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 */
@Component
public final class AeroliteShaftEntries {

    public static final ResourceKey<ShaftVariation> STONE = ShaftRegistries.STONE;
    public static final ResourceKey<ShaftVariation> MOSSY = variation("mossy");
    public static final ResourceKey<ShaftVariation> TUFF = variation("tuff");
    public static final ResourceKey<ShaftVariation> DEEPSLATE = variation("deepslate");
    public static final ResourceKey<ShaftVariation> COPPER = variation("copper");
    public static final ResourceKey<ShaftVariation> MUD = variation("mud");
    public static final ResourceKey<ShaftVariation> ANDESITE = variation("andesite");
    public static final ResourceKey<ShaftVariation> DIORITE = variation("diorite");
    public static final ResourceKey<ShaftVariation> GRANITE = variation("granite");
    public static final ResourceKey<ShaftVariation> NETHER = variation("nether");
    public static final ResourceKey<ShaftVariation> BLACKSTONE = variation("blackstone");
    public static final ResourceKey<ShaftVariation> SOUL = variation("soul");
    public static final ResourceKey<ShaftVariation> QUARTZ = variation("quartz");
    public static final ResourceKey<ShaftVariation> END_BRICKS = variation("end_bricks");
    public static final ResourceKey<ShaftVariation> END_MAZE = variation("end_maze");
    public static final ResourceKey<ShaftVariation> PURPUR_PILLAR = variation("purpur_pillar");

    public AeroliteShaftEntries() {
    }

    private static ResourceKey<ShaftVariation> variation(String name) {
        return ResourceKey.create(ShaftRegistries.SHAFT_VARIATION, ShaftRegistries.id(name));
    }

    public void variations(BootstrapContext<ShaftVariation> context) {
        register(context, STONE, ShaftVariation.UNIFORM_BIAS, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE);
        register(context, MOSSY, ShaftVariation.UNIFORM_BIAS, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK);
        register(context, TUFF, 0.7F, Blocks.TUFF, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF);
        register(context, DEEPSLATE, 0.3F, Blocks.DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE);
        register(context, COPPER, 0.8F, Blocks.OXIDIZED_COPPER, Blocks.OXIDIZED_CUT_COPPER, Blocks.OXIDIZED_COPPER_BULB);
        register(context, MUD, ShaftVariation.UNIFORM_BIAS, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.MOSS_BLOCK);
        register(context, ANDESITE, ShaftVariation.UNIFORM_BIAS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE);
        register(context, DIORITE, ShaftVariation.UNIFORM_BIAS, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE);
        register(context, GRANITE, ShaftVariation.UNIFORM_BIAS, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.POLISHED_ANDESITE);
        register(context, NETHER, 0.25F, Blocks.NETHERRACK, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS);
        register(context, BLACKSTONE, ShaftVariation.UNIFORM_BIAS, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE);
        register(context, SOUL, 0.6F, Blocks.SOUL_SOIL, Blocks.SOUL_SAND, Blocks.WARPED_WART_BLOCK);
        register(context, QUARTZ, 0.8F, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BLOCK);

        this.endPillars(context);
    }

    private void endPillars(BootstrapContext<ShaftVariation> context) {
        register(context, END_BRICKS, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                128,
                0.35f,
                0.5f,
                new BricksSettings(BricksSettings.Size.LARGE, new SurfacePathSettings(false, 0f, true, true, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE_BRICKS),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE)
        ));
        register(context, END_MAZE, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                128,
                0.35f,
                0.5f,
                new MazeSettings(MazeSettings.Type.GILBERT, 0.95f, MazeSettings.Seam.RANDOM, MazeSettings.MIN_SPACING),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE_BRICKS),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE)
        ));
        register(context, PURPUR_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                256,
                0.7f,
                0.85f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, true, new SurfacePathSettings(true, 1f, true, false, 0.25f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.OBSIDIAN),
                BlockStateProvider.simple(Blocks.OBSIDIAN),
                BlockStateProvider.simple(Blocks.PURPUR_BLOCK),
                BlockStateProvider.simple(Blocks.CRYING_OBSIDIAN),
                BlockStateProvider.simple(Blocks.PURPLE_STAINED_GLASS),
                BlockStateProvider.simple(Blocks.OBSIDIAN)
        ));
    }

    private static void register(BootstrapContext<ShaftVariation> context, ResourceKey<ShaftVariation> key, float bias, Block interior, Block wall, Block path) {
        register(context, key, ShaftVariation.standard(bias, BlockStateProvider.simple(interior), BlockStateProvider.simple(wall), BlockStateProvider.simple(path)));
    }

    private static void register(BootstrapContext<ShaftVariation> context, ResourceKey<ShaftVariation> key, ShaftVariation standard) {
        context.register(key, standard);
    }

    /** The default variation list of each vanilla dimension. The sequence is the pick sequence. */
    public Map<ResourceKey<VariationList>, VariationList> variationLists() {
        Map<ResourceKey<VariationList>, VariationList> lists = new LinkedHashMap<>();
        lists.put(ShaftRegistries.OVERWORLD_LIST, this.overworldList());
        lists.put(ShaftRegistries.NETHER_LIST, this.netherList());
        lists.put(ShaftRegistries.END_LIST, this.endList());
        return lists;
    }

    private VariationList overworldList() {
        return list(entry(STONE, 16), entry(MOSSY, 4), entry(TUFF, 4), entry(DEEPSLATE, 2), entry(COPPER, 1),
                entry(MUD, 1), entry(ANDESITE, 1), entry(DIORITE, 1), entry(GRANITE, 1));
    }

    private VariationList netherList() {
        return list(entry(NETHER, 16), entry(BLACKSTONE, 9), entry(SOUL, 5), entry(QUARTZ, 1));
    }

    private VariationList endList() {
        return list(entry(END_BRICKS, 16), entry(END_MAZE, 3), entry(PURPUR_PILLAR, 1));
    }

    private static VariationList.Entry entry(ResourceKey<ShaftVariation> variation, int weight) {
        return new VariationList.Entry(variation, weight);
    }

    private static VariationList list(VariationList.Entry... entries) {
        return new VariationList(List.of(entries));
    }

    /** The default placement, and one placement under the id of each vanilla dimension. */
    public void placements(BootstrapContext<ShaftPlacement> context) {
        context.register(ShaftRegistries.DEFAULT_PLACEMENT, ShaftPlacement.DEFAULT);

        final int chunk = 16;

        context.register(ShaftRegistries.placementOf(Level.OVERWORLD), ShaftPlacement.DEFAULT);
        context.register(ShaftRegistries.placementOf(Level.NETHER), ShaftPlacement.DEFAULT);
        context.register(ShaftRegistries.placementOf(Level.END), new ShaftPlacement(4 * chunk, 16 * chunk, 0.9f));
    }

}
