package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.*;
import us.drullk.aerolite.world.pattern.*;

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
    public static final ResourceKey<ShaftVariation> MOSSY_MAZE = variation("mossy_maze");
    public static final ResourceKey<ShaftVariation> TUFF_BRICKS = variation("tuff_bricks");
    public static final ResourceKey<ShaftVariation> DEEPSLATE = variation("deepslate");
    public static final ResourceKey<ShaftVariation> COPPER_BRIDGE = variation("copper_bridge");
    public static final ResourceKey<ShaftVariation> MUD_TILES = variation("mud_tiles");
    public static final ResourceKey<ShaftVariation> ANDESITE = variation("andesite");
    public static final ResourceKey<ShaftVariation> DIORITE = variation("diorite");
    public static final ResourceKey<ShaftVariation> GRANITE = variation("granite");
    public static final ResourceKey<ShaftVariation> PRISMARINE_SCALES = variation("prismarine_scales");

    public static final ResourceKey<ShaftVariation> NETHER_BRICKS = variation("nether_bricks");
    public static final ResourceKey<ShaftVariation> BLACKSTONE_RIBS = variation("blackstone_ribs");
    public static final ResourceKey<ShaftVariation> SOUL_CONDUIT = variation("soul_conduit");
    public static final ResourceKey<ShaftVariation> QUARTZ_BEAM = variation("quartz_beam");

    public static final ResourceKey<ShaftVariation> END_BRICKS = variation("end_bricks");
    public static final ResourceKey<ShaftVariation> END_MAZE = variation("end_maze");
    public static final ResourceKey<ShaftVariation> PURPUR_PILLAR = variation("purpur_pillar");

    public static final ResourceKey<ShaftVariation> AETHER_HOLYSTONE_PILLAR = variation("holystone_pillar");
    public static final ResourceKey<ShaftVariation> AETHER_BRONZE_PILLAR = variation("aether_bronze_pillar");
    public static final ResourceKey<ShaftVariation> AETHER_SILVER_PILLAR = variation("aether_silver_pillar");
    public static final ResourceKey<ShaftVariation> AETHER_GOLD_PILLAR = variation("aether_gold_pillar");

    public AeroliteShaftEntries() {
    }

    private static ResourceKey<ShaftVariation> variation(String name) {
        return ResourceKey.create(ShaftRegistries.SHAFT_VARIATION, ShaftRegistries.id(name));
    }

    public void variations(BootstrapContext<ShaftVariation> context) {
        this.overworldPillars(context);
        this.netherPillars(context);
        this.endPillars(context);

        this.aetherPillars(context);
    }

    private void overworldPillars(BootstrapContext<ShaftVariation> context) {
        register(context, STONE, ShaftVariation.UNIFORM_BIAS, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE);
        register(context, MOSSY_MAZE, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.2f,
                0.6f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, new SurfacePathSettings(false, 0f, true, true, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE),
                BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE),
                BlockStateProvider.simple(Blocks.MOSSY_STONE_BRICKS),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE)
        ));
        register(context, TUFF_BRICKS, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                128,
                0.5f,
                0.85f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, true, 1f)),
                PillarShape.I_BEAM,
                BlockStateProvider.simple(Blocks.TUFF),
                BlockStateProvider.simple(Blocks.TUFF),
                BlockStateProvider.simple(Blocks.TUFF_BRICKS),
                BlockStateProvider.simple(Blocks.POLISHED_TUFF),
                BlockStateProvider.simple(Blocks.POLISHED_TUFF),
                BlockStateProvider.simple(Blocks.TUFF)
        ));
        register(context, DEEPSLATE, 0.3F, Blocks.DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE);
        register(context, COPPER_BRIDGE, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                128,
                0.85f,
                0.95f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, true, 1f)),
                PillarShape.BRIDGE,
                BlockStateProvider.simple(Blocks.OXIDIZED_COPPER),
                BlockStateProvider.simple(Blocks.OXIDIZED_COPPER),
                BlockStateProvider.simple(Blocks.OXIDIZED_CUT_COPPER),
                BlockStateProvider.simple(Blocks.OXIDIZED_COPPER_BULB),
                BlockStateProvider.simple(Blocks.OXIDIZED_CHISELED_COPPER),
                BlockStateProvider.simple(Blocks.WAXED_EXPOSED_COPPER)
        ));
        register(context, MUD_TILES, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.2f,
                0.6f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.Y, false, new SurfacePathSettings(false, 0f, true, true, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.PACKED_MUD),
                BlockStateProvider.simple(Blocks.PACKED_MUD),
                BlockStateProvider.simple(Blocks.MUD_BRICKS),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.PACKED_MUD)
        ));
        register(context, ANDESITE, ShaftVariation.UNIFORM_BIAS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE);
        register(context, DIORITE, ShaftVariation.UNIFORM_BIAS, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE);
        register(context, GRANITE, ShaftVariation.UNIFORM_BIAS, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.POLISHED_ANDESITE);
        register(context, PRISMARINE_SCALES, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                64,
                0.35f,
                0.5f,
                new ScalesSettings(new SurfacePathSettings(true, 1f, true, false, 0.35f), true),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE_BRICKS),
                BlockStateProvider.simple(Blocks.DARK_PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE)
        ));
    }

    private void netherPillars(BootstrapContext<ShaftVariation> context) {
        register(context, NETHER_BRICKS, new ShaftVariation(
                0.25F,
                32,
                0.35f,
                0.4f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, false, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.NETHERRACK),
                BlockStateProvider.simple(Blocks.NETHERRACK),
                BlockStateProvider.simple(Blocks.NETHER_BRICKS),
                BlockStateProvider.simple(Blocks.NETHER_WART_BLOCK),
                BlockStateProvider.simple(Blocks.RED_NETHER_BRICKS),
                BlockStateProvider.simple(Blocks.NETHERRACK)
        ));
        register(context, BLACKSTONE_RIBS, new ShaftVariation(
                0.1F,
                64,
                0.35f,
                0.6f,
                new MazeSettings(MazeSettings.Type.RIBS, 0.95f, MazeSettings.Seam.RANDOM, MazeSettings.MIN_SPACING),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.BLACKSTONE),
                BlockStateProvider.simple(Blocks.BLACKSTONE),
                BlockStateProvider.simple(Blocks.POLISHED_BLACKSTONE_BRICKS),
                BlockStateProvider.simple(Blocks.POLISHED_BLACKSTONE),
                BlockStateProvider.simple(Blocks.BLACKSTONE),
                BlockStateProvider.simple(Blocks.BLACKSTONE)
        ));
        register(context, SOUL_CONDUIT, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                256,
                0.5f,
                0.65f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, new SurfacePathSettings(false, 0f, true, true, 0.25f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.SOUL_SOIL),
                BlockStateProvider.simple(Blocks.SOUL_SOIL),
                BlockStateProvider.simple(Blocks.SOUL_SAND),
                BlockStateProvider.simple(Blocks.WARPED_WART_BLOCK),
                BlockStateProvider.simple(Blocks.SOUL_SOIL),
                BlockStateProvider.simple(Blocks.SOUL_SOIL)
        ));
        register(context, QUARTZ_BEAM, new ShaftVariation(
                0.8F,
                128,
                0.5f,
                0.9f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, true, new SurfacePathSettings(true, 1f, true, false, 0.9f)),
                PillarShape.I_BEAM,
                BlockStateProvider.simple(Blocks.SMOOTH_QUARTZ),
                BlockStateProvider.simple(Blocks.SMOOTH_QUARTZ),
                BlockStateProvider.simple(Blocks.QUARTZ_BRICKS),
                BlockStateProvider.simple(Blocks.QUARTZ_BLOCK),
                BlockStateProvider.simple(Blocks.GLOWSTONE),
                BlockStateProvider.simple(Blocks.SMOOTH_QUARTZ)
        ));
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

    private void aetherPillars(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        register(context, AETHER_HOLYSTONE_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new MazeSettings(MazeSettings.Type.GILBERT, 0.95f, MazeSettings.Seam.RANDOM, 1),
                PillarShape.PILLAR,
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "holystone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "holystone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "holystone_bricks")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "mossy_holystone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "holystone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "holystone"))
        ));
        register(context, AETHER_BRONZE_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, false, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "carved_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "carved_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "carved_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "sentry_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "sentry_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "carved_stone"))
        ));
        register(context, AETHER_SILVER_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, false, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "angelic_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "angelic_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "angelic_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "light_angelic_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "light_angelic_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "angelic_stone"))
        ));
        register(context, AETHER_GOLD_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new MazeSettings(MazeSettings.Type.LINES, 1f, MazeSettings.Seam.RANDOM, 2),
                PillarShape.PILLAR,
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "hellfire_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "hellfire_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "hellfire_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "light_hellfire_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "light_hellfire_stone")),
                BlockStateProvider.simple(this.getBlockState(blocks, "aether", "hellfire_stone"))
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
        lists.put(ShaftRegistries.AETHER_LIST, this.aetherList());
        return lists;
    }

    private VariationList overworldList() {
        return list(
                entry(STONE, 16),
                entry(MOSSY_MAZE, 4),
                entry(TUFF_BRICKS, 4),
                entry(DEEPSLATE, 2),
                entry(COPPER_BRIDGE, 1),
                entry(MUD_TILES, 1),
                entry(ANDESITE, 1),
                entry(DIORITE, 1),
                entry(GRANITE, 1),
                entry(PRISMARINE_SCALES, 1)
        );
    }

    private VariationList netherList() {
        return list(entry(NETHER_BRICKS, 16), entry(BLACKSTONE_RIBS, 9), entry(SOUL_CONDUIT, 5), entry(QUARTZ_BEAM, 1));
    }

    private VariationList endList() {
        return list(entry(END_BRICKS, 16), entry(END_MAZE, 3), entry(PURPUR_PILLAR, 1));
    }

    private VariationList aetherList() {
        return list(
                entry(AETHER_HOLYSTONE_PILLAR, 16),
                entry(AETHER_BRONZE_PILLAR, 7),
                entry(AETHER_SILVER_PILLAR, 3),
                entry(AETHER_GOLD_PILLAR, 1)
        );
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
        context.register(ShaftRegistries.placementOf(Level.NETHER), new ShaftPlacement(6 * chunk, 12 * chunk, 0.7f));
        context.register(ShaftRegistries.placementOf(Level.END), new ShaftPlacement(4 * chunk, 16 * chunk, 0.9f));
        context.register(ShaftRegistries.placementOf(ModdedDimensions.AETHER), new ShaftPlacement(8 * chunk, 16 * chunk, 0.7f));
    }

    private BlockState getBlockState(HolderGetter<Block> blocks, String namespace, String path) {
        return blocks.getOrThrow(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path))).value().defaultBlockState();
    }

}
