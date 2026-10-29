package us.drullk.aerolite.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import us.drullk.aerolite.Aerolite;

/**
 * Writes the American English lang file of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_06-17_8d424d05-8940-4aa5-872e-7b2bd927cb73.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public class AeroliteLanguageProvider extends LanguageProvider {

    public AeroliteLanguageProvider(PackOutput output) {
        super(output, Aerolite.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add("generator.aerolite.aerolite", "Aerolite");
        this.add("createWorld.customize.aerolite.title", "Aerolite Settings");
        this.add("createWorld.customize.aerolite.column_chance", "Column Chance: %s");
        this.add("createWorld.customize.aerolite.min_shaft_spacing", "Minimum Shaft Spacing: %s");
        this.add("createWorld.customize.aerolite.max_shaft_spacing", "Maximum Shaft Spacing: %s");
        this.add("createWorld.customize.aerolite.decay_scale", "Maze Decay Scale: %s");
        this.add("createWorld.customize.aerolite.decay_threshold", "Maze Decay Threshold: %s");
        this.add("createWorld.customize.aerolite.erosion_threshold", "Shaft Erosion Threshold: %s");
    }

}
