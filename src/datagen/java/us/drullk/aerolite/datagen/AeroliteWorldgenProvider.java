package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import us.drullk.aerolite.Aerolite;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the worldgen JSON of the preset and the shaft data registry entries.
 * The placements of the vanilla dimensions are in the namespace {@code minecraft}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public class AeroliteWorldgenProvider extends DatapackBuiltinEntriesProvider {

    public AeroliteWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, RegistrySetBuilder entries) {
        super(output, registries, entries, Set.of(Aerolite.MODID, ResourceLocation.DEFAULT_NAMESPACE));
    }

}
