package us.drullk.aerolite.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModdedDimensions {

    public static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    public static final ResourceKey<Level> BUMBLEZONE = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("the_bumblezone", "the_bumblezone"));
    public static final ResourceKey<Level> GAIA = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("gaiadimension", "gaia_dimension"));
    public static final ResourceKey<Level> SPATIAL_STORAGE = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage"));
    public static final ResourceKey<Level> TWILIGHT_FOREST = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("twilightforest", "twilight_forest"));
    public static final ResourceKey<Level> UNDERGARDEN = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("undergarden", "undergarden"));
    public static final ResourceKey<Level> VOID = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("voidscape", "void"));

}
