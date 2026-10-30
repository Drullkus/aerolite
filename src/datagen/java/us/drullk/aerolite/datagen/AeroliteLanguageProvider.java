package us.drullk.aerolite.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import us.drullk.aerolite.Aerolite;

/**
 * Writes the American English lang file of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_06-17_8d424d05-8940-4aa5-872e-7b2bd927cb73.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_00-41_1fb7a266-bfdf-4ab5-8b4a-1652dc840cb2.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public class AeroliteLanguageProvider extends LanguageProvider {

    public AeroliteLanguageProvider(PackOutput output) {
        super(output, Aerolite.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add("generator.aerolite.aerolite", "Aerolite");
        this.add("createWorld.customize.aerolite.title", "Aerolite Settings");
        this.add("createWorld.customize.aerolite.shaft_placement", "Shaft Placement");
        this.add("createWorld.customize.aerolite.shaft_surface", "Shaft Surface");
        this.add("createWorld.customize.aerolite.column_chance", "Column Chance: %s");
        this.add("createWorld.customize.aerolite.min_shaft_spacing", "Minimum Shaft Spacing: %s");
        this.add("createWorld.customize.aerolite.max_shaft_spacing", "Maximum Shaft Spacing: %s");
        this.add("createWorld.customize.aerolite.decay_scale", "Maze Decay Scale: %s");
        this.add("createWorld.customize.aerolite.decay_threshold", "Maze Decay Threshold: %s");
        this.add("createWorld.customize.aerolite.erosion_threshold", "Erosion Threshold: %s");
        this.add("createWorld.customize.aerolite.clouds", "Clouds");
        this.add("createWorld.customize.aerolite.cloud_height", "Cloud Height: %s");
        this.add("createWorld.customize.aerolite.shaft_variations", "Shaft Variations");
        this.add("createWorld.customize.aerolite.shafts_in_unlisted_dimensions", "Shafts in Unlisted Dimensions");
        this.add("createWorld.customize.aerolite.dimension.overworld", "Overworld");
        this.add("createWorld.customize.aerolite.dimension.the_nether", "The Nether");
        this.add("createWorld.customize.aerolite.dimension.the_end", "The End");
        this.add("createWorld.customize.aerolite.weight", "Weight: %s");
        this.add("createWorld.customize.aerolite.maze_bias", "Maze Bias: %s");
        this.add("createWorld.customize.aerolite.maze_bias.ringwise", "%s (ringwise)");
        this.add("createWorld.customize.aerolite.maze_bias.uniform", "%s (uniform)");
        this.add("createWorld.customize.aerolite.maze_bias.lengthwise", "%s (lengthwise)");
        this.add("createWorld.customize.aerolite.interior_block", "Interior Block");
        this.add("createWorld.customize.aerolite.wall_block", "Maze Wall Block");
        this.add("createWorld.customize.aerolite.path_block", "Maze Path Block");
        this.add("createWorld.customize.aerolite.add_variation", "Add Variation");
        this.add("createWorld.customize.aerolite.remove_variation", "Remove Variation");
    }

}
