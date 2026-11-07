package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;

import java.util.concurrent.CompletableFuture;

/**
 * Puts both presets in the normal world type list.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public class AeroliteWorldPresetTagsProvider extends TagsProvider<WorldPreset> {

    public AeroliteWorldPresetTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, ExistingFileHelper existingFileHelper) {
        super(output, Registries.WORLD_PRESET, registries, Aerolite.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(WorldPresetTags.NORMAL).add(AeroliteWorldgen.PRESET).add(AeroliteWorldgen.ISLANDS_PRESET);
    }

}
