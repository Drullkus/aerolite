package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.ShaftRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the worldgen JSON of the preset and the shaft data registry entries.
 * The placement of a dimension is in the namespace of that dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
public class AeroliteWorldgenProvider extends DatapackBuiltinEntriesProvider {

    public AeroliteWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, RegistrySetBuilder entries, Map<ResourceKey<?>, List<ICondition>> conditions) {
        super(output, registries, entries, conditions, namespaces());
    }

    /** The provider writes only entries in these namespaces. */
    private static Set<String> namespaces() {
        Set<String> namespaces = new HashSet<>(Set.of(Aerolite.MODID, ResourceLocation.DEFAULT_NAMESPACE));
        ShaftRegistries.DIMENSION_LISTS.keySet().forEach(dimension -> namespaces.add(dimension.location().getNamespace()));
        return namespaces;
    }

}
