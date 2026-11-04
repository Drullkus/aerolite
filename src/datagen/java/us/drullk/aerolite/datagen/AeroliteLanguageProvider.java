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
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
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
        this.add("createWorld.customize.aerolite.shaft_placement", "Pillar Placement");
        this.add("createWorld.customize.aerolite.shaft_surface", "Pillar Surface");
        this.add("createWorld.customize.aerolite.column_chance", "Column Chance: %s");
        this.add("createWorld.customize.aerolite.min_shaft_spacing", "Minimum Pillar Spacing: %s");
        this.add("createWorld.customize.aerolite.max_shaft_spacing", "Maximum Pillar Spacing: %s");
        this.add("createWorld.customize.aerolite.decay_scale", "Maze Decay Scale: %s");
        this.add("createWorld.customize.aerolite.decay_threshold", "Maze Decay Threshold: %s");
        this.add("createWorld.customize.aerolite.erosion_threshold", "Erosion Threshold: %s");
        this.add("createWorld.customize.aerolite.clouds", "Clouds");
        this.add("createWorld.customize.aerolite.cloud_height", "Cloud Height: %s");
        this.add("createWorld.customize.aerolite.shaft_variations", "Pillar Variations");
        this.add("createWorld.customize.aerolite.configure_dimension", "Configure");
        this.add("createWorld.customize.aerolite.tab.shaft_placement", "Pillar Placement");
        this.add("createWorld.customize.aerolite.tab.shaft_variations", "Pillar Variations");
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
        this.add("createWorld.customize.aerolite.blocks", "Blocks");
        this.add("createWorld.customize.aerolite.interior_block", "Interior");
        this.add("createWorld.customize.aerolite.junction_block", "Junction");
        this.add("createWorld.customize.aerolite.wall_block", "Wall");
        this.add("createWorld.customize.aerolite.path_block", "Path");
        this.add("createWorld.customize.aerolite.surface_block", "Surface");
        this.add("createWorld.customize.aerolite.trusses_block", "Trusses");
        this.add("createWorld.customize.aerolite.provider.edit", "Edit");
        this.add("createWorld.customize.aerolite.provider.open_tab", "Open the tab of %s");
        this.add("createWorld.customize.aerolite.provider.title", "%s: %s");
        this.add("createWorld.customize.aerolite.provider.type", "Type: %s");
        this.add("createWorld.customize.aerolite.provider.type.simple_state_provider", "Simple state");
        this.add("createWorld.customize.aerolite.provider.type.weighted_state_provider", "Weighted");
        this.add("createWorld.customize.aerolite.provider.type.noise_threshold_provider", "Noise threshold");
        this.add("createWorld.customize.aerolite.provider.type.noise_provider", "Noise");
        this.add("createWorld.customize.aerolite.provider.type.dual_noise_provider", "Dual noise");
        this.add("createWorld.customize.aerolite.provider.type.rotated_block_provider", "Rotated block");
        this.add("createWorld.customize.aerolite.provider.type.randomized_int_state_provider", "Randomized int");
        this.add("createWorld.customize.aerolite.provider.type.other", "Other");
        this.add("createWorld.customize.aerolite.provider.typed_summary", "%s: %s");
        this.add("createWorld.customize.aerolite.provider.summary.noise_threshold", "%s | %s low | %s high");
        this.add("createWorld.customize.aerolite.provider.no_editor", "No editor for %s");
        this.add("createWorld.customize.aerolite.provider.state", "State");
        this.add("createWorld.customize.aerolite.provider.state_hint", "Block state");
        this.add("createWorld.customize.aerolite.provider.entries", "Entries");
        this.add("createWorld.customize.aerolite.provider.weight", "Weight");
        this.add("createWorld.customize.aerolite.provider.add_entry", "+ Entry");
        this.add("createWorld.customize.aerolite.provider.remove_entry", "Remove this entry");
        this.add("createWorld.customize.aerolite.provider.seed", "Seed");
        this.add("createWorld.customize.aerolite.provider.octave", "Octave");
        this.add("createWorld.customize.aerolite.provider.amplitudes", "Amplitudes");
        this.add("createWorld.customize.aerolite.provider.scale", "Scale");
        this.add("createWorld.customize.aerolite.provider.slow_octave", "Slow Octave");
        this.add("createWorld.customize.aerolite.provider.slow_amplitudes", "Slow Amplitudes");
        this.add("createWorld.customize.aerolite.provider.slow_scale", "Slow Scale");
        this.add("createWorld.customize.aerolite.provider.threshold", "Threshold");
        this.add("createWorld.customize.aerolite.provider.high_chance", "High Chance");
        this.add("createWorld.customize.aerolite.provider.default_state", "Default");
        this.add("createWorld.customize.aerolite.provider.states", "States");
        this.add("createWorld.customize.aerolite.provider.low_states", "Low States");
        this.add("createWorld.customize.aerolite.provider.high_states", "High States");
        this.add("createWorld.customize.aerolite.provider.add_state", "+ State");
        this.add("createWorld.customize.aerolite.provider.remove_state", "Remove this state");
        this.add("createWorld.customize.aerolite.provider.variety_min", "Variety Min: %s");
        this.add("createWorld.customize.aerolite.provider.variety_max", "Variety Max: %s");
        this.add("createWorld.customize.aerolite.provider.block", "Block");
        this.add("createWorld.customize.aerolite.provider.block_tooltip", "Only a block with the property axis turns. The placement picks the axis.");
        this.add("createWorld.customize.aerolite.provider.source", "Source");
        this.add("createWorld.customize.aerolite.provider.source_tooltip", "Only a simple source has an editor");
        this.add("createWorld.customize.aerolite.provider.property", "Property: %s");
        this.add("createWorld.customize.aerolite.provider.property_entry", "%s (%s-%s)");
        this.add("createWorld.customize.aerolite.provider.property_none", "Property: none");
        this.add("createWorld.customize.aerolite.provider.values", "Values: %s");
        this.add("createWorld.customize.aerolite.provider.values.constant", "Constant");
        this.add("createWorld.customize.aerolite.provider.values.uniform", "Uniform");
        this.add("createWorld.customize.aerolite.provider.values.other", "Other");
        this.add("createWorld.customize.aerolite.provider.value", "Value: %s");
        this.add("createWorld.customize.aerolite.provider.min", "Min: %s");
        this.add("createWorld.customize.aerolite.provider.max", "Max: %s");
        this.add("createWorld.customize.aerolite.pattern", "Pattern: %s");
        this.add("createWorld.customize.aerolite.pattern.mazes", "Mazes");
        this.add("createWorld.customize.aerolite.pattern.bricks", "Bricks");
        this.add("createWorld.customize.aerolite.pattern.tiles", "Tiles");
        this.add("createWorld.customize.aerolite.pattern.scales", "Scales");
        this.add("createWorld.customize.aerolite.pattern.chaos", "Chaos");
        this.add("createWorld.customize.aerolite.maze_type", "Type: %s");
        this.add("createWorld.customize.aerolite.maze_type.uniform", "Uniform");
        this.add("createWorld.customize.aerolite.maze_type.serpentine", "Serpentine");
        this.add("createWorld.customize.aerolite.maze_type.ribs", "Ribs");
        this.add("createWorld.customize.aerolite.maze_type.lines", "Lines");
        this.add("createWorld.customize.aerolite.maze_type.gilbert", "Gilbert");
        this.add("createWorld.customize.aerolite.pattern_strength", "Strength: %s");
        this.add("createWorld.customize.aerolite.pattern_strength_tooltip", "Pattern Strength. At 100%, the maze follows the pattern exactly.");
        this.add("createWorld.customize.aerolite.seam", "Seam: %s");
        this.add("createWorld.customize.aerolite.seam.random", "Random");
        this.add("createWorld.customize.aerolite.seam.seeded", "Seeded");
        this.add("createWorld.customize.aerolite.seam.pillar", "Per pillar");
        this.add("createWorld.customize.aerolite.seam.random_tooltip", "Random per segment");
        this.add("createWorld.customize.aerolite.seam.seeded_tooltip", "Seeded per segment");
        this.add("createWorld.customize.aerolite.seam.pillar_tooltip", "Per pillar");
        this.add("createWorld.customize.aerolite.line_spacing", "Spacing: %s");
        this.add("createWorld.customize.aerolite.line_spacing_tooltip", "The node columns per groove");
        this.add("createWorld.customize.aerolite.size", "Size: %s");
        this.add("createWorld.customize.aerolite.size.small", "Small");
        this.add("createWorld.customize.aerolite.size.jumbled", "Jumbled");
        this.add("createWorld.customize.aerolite.size.large", "Large");
        this.add("createWorld.customize.aerolite.stagger", "Stagger: %s");
        this.add("createWorld.customize.aerolite.stagger.none", "None");
        this.add("createWorld.customize.aerolite.stagger.x", "X");
        this.add("createWorld.customize.aerolite.stagger.y", "Y");
        this.add("createWorld.customize.aerolite.stagger.x_encased_tooltip", "Encased tiles and large tiles do not stagger along X");
        this.add("createWorld.customize.aerolite.encased", "Encased");
        this.add("createWorld.customize.aerolite.encased_tooltip", "Keep the corner strip solid");
        this.add("createWorld.customize.aerolite.cells", "Cells: %s");
        this.add("createWorld.customize.aerolite.cells.voronoi", "Voronoi");
        this.add("createWorld.customize.aerolite.cells.delaunay", "Delaunay");
        this.add("createWorld.customize.aerolite.node_spacing", "Node Spacing: %s");
        this.add("createWorld.customize.aerolite.node_spacing_tooltip", "The node spacing in blocks");
        this.add("createWorld.customize.aerolite.jitter", "Jitter: %s");
        this.add("createWorld.customize.aerolite.jitter_tooltip", "The offset range of each cell point");
        this.add("createWorld.customize.aerolite.corner_radius", "Corner Radius: %s");
        this.add("createWorld.customize.aerolite.corner_radius_tooltip", "The chamfer at each T junction, in blocks");
        this.add("createWorld.customize.aerolite.allow_loops", "Allow loops");
        this.add("createWorld.customize.aerolite.allow_loops_tooltip", "Keep a share of the pattern cycles");
        this.add("createWorld.customize.aerolite.loop_share", "Loop Share: %s");
        this.add("createWorld.customize.aerolite.loop_share_tooltip", "The share of the cycle grooves that stays");
        this.add("createWorld.customize.aerolite.surface_paths", "Surface paths");
        this.add("createWorld.customize.aerolite.surface_paths_tooltip", "Put surface paths on the pattern");
        this.add("createWorld.customize.aerolite.cutting", "Cutting");
        this.add("createWorld.customize.aerolite.cutting_tooltip", "Carve each surface path as a groove");
        this.add("createWorld.customize.aerolite.path_chance", "Path Chance: %s");
        this.add("createWorld.customize.aerolite.path_chance_tooltip", "The chance of a surface path in one region");
        this.add("createWorld.customize.aerolite.paths_over_tails", "Paths over tails");
        this.add("createWorld.customize.aerolite.paths_over_tails_tooltip", "A cut surface path carves the tail blocks");
        this.add("createWorld.customize.aerolite.shape", "Shape: %s");
        this.add("createWorld.customize.aerolite.shape.pillar", "Pillar");
        this.add("createWorld.customize.aerolite.shape.i_beam", "I-beam");
        this.add("createWorld.customize.aerolite.shape.bridge", "Bridge");
        this.add("createWorld.customize.aerolite.add_variation", "Add Variation");
        this.add("createWorld.customize.aerolite.remove_variation", "Remove");
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
        this.add("createWorld.customize.aerolite.preview.no_shafts", "This dimension has no variations, so it has no pillars.");
        this.add("createWorld.customize.aerolite.preview.center", "Center");
        this.add("createWorld.customize.aerolite.preview.reset_center", "Reset");
        this.add("aerolite.configuration.panelWidth", "Preview Panel Width");
    }

    /** The name of a variation list, under {@code pillar_variations.<namespace>.<path>}. */
    private void addListName(ResourceKey<VariationList> list, String name) {
        this.add(list.location().toLanguageKey("pillar_variations"), name);
    }

}
