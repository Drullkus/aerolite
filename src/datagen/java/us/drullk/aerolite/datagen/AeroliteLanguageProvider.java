package us.drullk.aerolite.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.data.LanguageProvider;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.VariationList;

/**
 * Writes the American English lang file of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_06-17_8d424d05-8940-4aa5-872e-7b2bd927cb73.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_00-41_1fb7a266-bfdf-4ab5-8b4a-1652dc840cb2.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_bf29e15f-47bb-42fe-94f0-29ae22f43f5a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_1143d92a-d4be-41d5-9df1-3b5c5d54fb8d.md}.
 */
public class AeroliteLanguageProvider extends LanguageProvider {

    public AeroliteLanguageProvider(PackOutput output) {
        super(output, Aerolite.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add("generator.aerolite.void", "Aerolite Void");
        this.add("createWorld.customize.aerolite.void.title", "Aerolite Void");
        this.add("createWorld.customize.aerolite.void.vanilla_biomes", "Vanilla biomes");
        this.add("createWorld.customize.aerolite.void.biome", "Biome");
        this.add("createWorld.customize.aerolite.void.reset", "Reset");
        this.add("createWorld.customize.aerolite.title", "Amazing Pillars");
        this.add("createWorld.customize.aerolite.pillars", "Amazing Pillars");
        this.add("createWorld.customize.aerolite.settings", "Settings");
        this.add("createWorld.customize.aerolite.no_prefab", "No prefab");
        this.add("createWorld.customize.aerolite.prefab_tooltip", "Prefab Pillars to add in %s");
        this.add("createWorld.customize.aerolite.prefab_dimension", "Dimension");
        this.addListName(ShaftRegistries.OVERWORLD_LIST, "Overworld Pillars");
        this.addListName(ShaftRegistries.NETHER_LIST, "Nether Pillars");
        this.addListName(ShaftRegistries.END_LIST, "End Pillars");
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
        this.add("createWorld.customize.aerolite.configure_dimension", "Configure");
        this.add("createWorld.customize.aerolite.tab.shaft_placement", "Shaft Placement");
        this.add("createWorld.customize.aerolite.tab.shaft_variations", "Shaft Variations");
        this.add("createWorld.customize.aerolite.dimension_title", "Amazing Pillars: %s");
        this.add("createWorld.customize.aerolite.dimension.overworld", "Overworld");
        this.add("createWorld.customize.aerolite.dimension.the_nether", "The Nether");
        this.add("createWorld.customize.aerolite.dimension.the_end", "The End");
        this.add("createWorld.customize.aerolite.remove_dimension", "Remove");
        this.add("createWorld.customize.aerolite.add_dimension", "Add Dimension");
        this.add("createWorld.customize.aerolite.all_dimensions_configured", "All dimensions configured");
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
        this.add("createWorld.customize.aerolite.copy_variation", "Copy");
        this.add("createWorld.customize.aerolite.paste_variation", "Paste");
        this.add("createWorld.customize.aerolite.copy_variation_tooltip", "Export this variation to the clipboard");
        this.add("createWorld.customize.aerolite.paste_variation_tooltip", "Import this variation from the clipboard");
        this.add("createWorld.customize.aerolite.preview", "Preview");
        this.add("createWorld.customize.aerolite.copy", "Copy");
        this.add("createWorld.customize.aerolite.paste", "Paste");
        this.add("createWorld.customize.aerolite.copy_tooltip", "Copy to Clipboard");
        this.add("createWorld.customize.aerolite.paste_tooltip", "Paste from Clipboard");
        this.add("createWorld.customize.aerolite.defaults", "Defaults");
        this.add("createWorld.customize.aerolite.defaults_tooltip", "Replace empty settings with Amazing Pillar defaults");
        this.add("createWorld.customize.aerolite.paste_failed", "Paste failed");
        this.add("createWorld.customize.aerolite.import_failed", "Import failed");
        this.add("createWorld.customize.aerolite.preview.title", "Aerolite Preview");
        this.add("createWorld.customize.aerolite.preview.section", "Preview");
        this.add("createWorld.customize.aerolite.preview.seed", "Seed");
        this.add("createWorld.customize.aerolite.preview.reseed", "Reseed");
        this.add("createWorld.customize.aerolite.preview.radius", "Radius: %s chunks");
        this.add("createWorld.customize.aerolite.preview.dimension", "Dimension");
        this.add("createWorld.customize.aerolite.preview.show_dimension", "Preview this dimension");
        this.add("createWorld.customize.aerolite.preview.waiting", "Waiting");
        this.add("createWorld.customize.aerolite.preview.painting", "Painting chunks: %s / %s");
        this.add("createWorld.customize.aerolite.preview.baking", "Baking sections: %s / %s");
        this.add("createWorld.customize.aerolite.preview.done", "Baked sections: %s");
        this.add("createWorld.customize.aerolite.preview.no_shafts", "This dimension has no variations, so it has no shafts.");
        this.add("createWorld.customize.aerolite.preview.center", "Center");
        this.add("createWorld.customize.aerolite.preview.reset_center", "Reset");
        this.add("aerolite.configuration.panelWidth", "Preview Panel Width");
    }

    /** The name of a variation list, under {@code pillar_variations.<namespace>.<path>}. */
    private void addListName(ResourceKey<VariationList> list, String name) {
        this.add(list.location().toLanguageKey("pillar_variations"), name);
    }

}
