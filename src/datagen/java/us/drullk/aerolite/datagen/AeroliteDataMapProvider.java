package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.ShaftDataMaps;

import java.util.concurrent.CompletableFuture;

/**
 * Writes the data maps of Aerolite. The shaft variations data map ships without values, so no dimension gets shafts from it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public class AeroliteDataMapProvider extends DataMapProvider {

    public AeroliteDataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void gather(HolderLookup.Provider registries) {
        this.builder(ShaftDataMaps.SHAFT_VARIATIONS);
        this.builder(ShaftDataMaps.CLOUD_HEIGHT).add(AeroliteWorldgen.OVERWORLD_TYPE, AeroliteSettings.DEFAULT_CLOUD_HEIGHT, false);
    }

}
