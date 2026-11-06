package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.ShaftRegistries;

import java.util.concurrent.CompletableFuture;

/**
 * Writes the block tags. The Nether portal is the one vanilla block with a negative destroy time outside {@code minecraft:wither_immune}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-33_bb95aa4e-2e64-45f7-a528-d3415d48c0f3.md}.
 */
public class AeroliteBlockTagsProvider extends BlockTagsProvider {

    public AeroliteBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, ExistingFileHelper existingFileHelper) {
        super(output, registries, Aerolite.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(ShaftRegistries.PILLARS_CANNOT_REPLACE).addTag(BlockTags.WITHER_IMMUNE).add(Blocks.NETHER_PORTAL);
    }

}
