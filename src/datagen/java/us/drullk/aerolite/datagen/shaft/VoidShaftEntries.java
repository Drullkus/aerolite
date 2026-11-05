package us.drullk.aerolite.datagen.shaft;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.*;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.List;
import java.util.Optional;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

@Component
public final class VoidShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> VOID_BLACK_NULL = variation("void_black_null");
    public static final ResourceKey<ShaftVariation> VOID_WHITE_NULL = variation("void_white_null");

    private static final int CHUNK = 16;

    public VoidShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return ModdedDimensions.VOID;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(4 * CHUNK, 16 * CHUNK, 0.1f);
    }

    @Override
    public Optional<String> requiredMod() {
        return Optional.of("voidscape");
    }

    @Override
    public List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of(VOID_BLACK_NULL, VOID_WHITE_NULL);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(VOID_BLACK_NULL, new ShaftVariation(
                0.35f,
                64,
                0.35f,
                0.5f,
                new MazeSettings(MazeSettings.Type.RIBS, 0.95f, MazeSettings.Seam.RANDOM, 1),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_black")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_black")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_black")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_white")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_white")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_white"))
        ));
        context.register(VOID_WHITE_NULL, new ShaftVariation(
                0.35f,
                64,
                0.35f,
                0.5f,
                new MazeSettings(MazeSettings.Type.RIBS, 0.95f, MazeSettings.Seam.RANDOM, 1),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_white")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_white")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_white")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_black")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_black")),
                BlockStateProvider.simple(getBlockState(blocks, "voidscape", "null_black"))
        ));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(VOID_BLACK_NULL, 16),
                entry(VOID_WHITE_NULL, 16)
        );
    }

}
