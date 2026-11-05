package us.drullk.aerolite.datagen;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import tamaized.beanification.Component;
import tamaized.beanification.Directory;
import us.drullk.aerolite.datagen.shaft.DimensionShaftEntries;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the shipped shaft data of each dimension class in the package {@code datagen.shaft}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
@Component
public final class AeroliteShaftEntries {

    // Beanification fills this list after construction.
    @Directory(DimensionShaftEntries.class)
    private List<DimensionShaftEntries> dimensions = List.of();

    public AeroliteShaftEntries() {
    }

    public void variations(BootstrapContext<ShaftVariation> context) {
        this.dimensions.forEach(dimension -> dimension.variations(context));
    }

    /** The default placement, and one placement under the id of each dimension. */
    public void placements(BootstrapContext<ShaftPlacement> context) {
        context.register(ShaftRegistries.DEFAULT_PLACEMENT, ShaftPlacement.DEFAULT);
        this.dimensions.forEach(dimension -> context.register(ShaftRegistries.placementOf(dimension.dimension()), dimension.placement()));
    }

    /** The variation list of each dimension. */
    public Map<ResourceKey<VariationList>, VariationList> variationLists() {
        Map<ResourceKey<VariationList>, VariationList> lists = new LinkedHashMap<>();
        this.dimensions.forEach(dimension -> lists.put(ShaftRegistries.listOf(dimension.dimension()), dimension.variationList()));
        return lists;
    }

    /** The mod that each modded entry needs. A variation from another dimension class keeps its own conditions. */
    public Map<ResourceKey<?>, List<ICondition>> conditions() {
        Map<ResourceKey<?>, List<ICondition>> conditions = new HashMap<>();
        for (DimensionShaftEntries dimension : this.dimensions) {
            dimension.requiredMod().ifPresent(mod -> {
                List<ICondition> needsMod = List.of(new ModLoadedCondition(mod));
                conditions.put(ShaftRegistries.listOf(dimension.dimension()), needsMod);
                dimension.modVariations().forEach(variation -> conditions.put(variation, needsMod));
            });
        }
        return conditions;
    }

}
