package us.drullk.aerolite.client;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.pattern.PatternType;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the edit rules of the pattern widgets and one export and import through the variation codec.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class PatternEditsTest {

    private final PresetSliders sliders = new PresetSliders();
    private final PresetEdits edits = new PresetEdits(this.sliders);

    @Test
    void patternChangeGivesTheDefaultsOfTheNewType() {
        ShaftVariation edited = ShippedShaftData.variation("stone")
                .withPattern(new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0.2F, true, true, 0.4F)));
        for (PatternType type : PatternType.values()) {
            PatternSettings pattern = this.edits.withPatternType(edited, type).pattern();
            assertEquals(type, pattern.patternType());
            if (type != PatternType.BRICKS) {
                assertEquals(PresetEdits.defaultPattern(type), pattern);
            }
        }
        assertEquals(MazeSettings.DEFAULT, PresetEdits.defaultPattern(PatternType.MAZES));
        assertEquals(BricksSettings.DEFAULT, PresetEdits.defaultPattern(PatternType.BRICKS));
        assertEquals(TilesSettings.DEFAULT, PresetEdits.defaultPattern(PatternType.TILES));
        assertEquals(ScalesSettings.DEFAULT, PresetEdits.defaultPattern(PatternType.SCALES));
        assertEquals(ChaosSettings.DEFAULT, PresetEdits.defaultPattern(PatternType.CHAOS));
    }

    @Test
    void sameTypeKeepsThePattern() {
        ShaftVariation edited = ShippedShaftData.variation("stone").withPattern(MazeSettings.DEFAULT.withType(MazeSettings.Type.GILBERT).withStrength(0.7F));
        assertSame(edited, this.edits.withPatternType(edited, PatternType.MAZES));
    }

    @Test
    void staggerXTurnsOffWithEncasedOrLarge() {
        TilesSettings small = TilesSettings.DEFAULT.withSize(TilesSettings.Size.SMALL).withStagger(TilesSettings.Stagger.X);
        assertEquals(TilesSettings.Stagger.X, small.stagger());
        assertTrue(PresetEdits.staggerAllowed(small, TilesSettings.Stagger.X));
        assertEquals(List.of(TilesSettings.Stagger.values()), PresetEdits.staggerChoices(TilesSettings.Size.SMALL));

        TilesSettings encased = small.withEncased(true);
        assertEquals(TilesSettings.Stagger.NONE, encased.stagger());
        assertFalse(PresetEdits.staggerAllowed(encased, TilesSettings.Stagger.X));
        assertTrue(PresetEdits.staggerAllowed(encased, TilesSettings.Stagger.Y));

        TilesSettings large = small.withSize(TilesSettings.Size.LARGE);
        assertEquals(TilesSettings.Stagger.NONE, large.stagger());
        assertFalse(PresetEdits.staggerAllowed(large, TilesSettings.Stagger.X));
        assertEquals(List.of(TilesSettings.Stagger.NONE, TilesSettings.Stagger.Y), PresetEdits.staggerChoices(TilesSettings.Size.LARGE));
    }

    @Test
    void loopShareAndPathChanceClampToTheirSteps() {
        SurfacePathSettings paths = SurfacePathSettings.DEFAULT;
        assertEquals(10, this.sliders.clampedFractionStep(paths.loopShare()));
        assertEquals(15, this.sliders.clampedFractionStep(paths.pathChance()));
        assertEquals(0.35F, this.edits.withLoopShareStep(paths, 7).loopShare(), 1e-6F);
        assertEquals(1F, this.edits.withLoopShareStep(paths, 25).loopShare(), 1e-6F);
        assertEquals(0F, this.edits.withLoopShareStep(paths, -3).loopShare(), 1e-6F);
        assertEquals(0.05F, this.edits.withPathChanceStep(paths, 1).pathChance(), 1e-6F);
        assertEquals(1F, this.edits.withPathChanceStep(paths, 21).pathChance(), 1e-6F);
        assertEquals(0F, this.edits.withPathChanceStep(paths, -1).pathChance(), 1e-6F);
        assertEquals(7, this.sliders.clampedFractionStep(0.33F));
        assertEquals(20, this.sliders.clampedFractionStep(1.4F));
        assertEquals(0, this.sliders.clampedFractionStep(-0.2F));
    }

    @Test
    void pathsEditReachesEveryPatternButTheMaze() {
        ShaftVariation maze = ShippedShaftData.variation("stone");
        assertSame(maze.pattern(), this.edits.withPaths(maze, paths -> paths.withCutting(true)).pattern());
        assertEquals(Optional.empty(), PresetEdits.paths(maze.pattern()));
        for (PatternType type : List.of(PatternType.BRICKS, PatternType.TILES, PatternType.SCALES, PatternType.CHAOS)) {
            ShaftVariation edited = this.edits.withPaths(this.edits.withPatternType(maze, type), paths -> paths.withCutting(true));
            assertTrue(PresetEdits.paths(edited.pattern()).orElseThrow().cutting(), type.getSerializedName());
        }
    }

    @Test
    void lineMazeHidesBiasAndStrength() {
        ShaftVariation lines = ShippedShaftData.variation("stone").withPattern(MazeSettings.DEFAULT.withType(MazeSettings.Type.LINES));
        assertFalse(PresetEdits.showsBias(lines));
        assertTrue(PresetEdits.showsBias(ShippedShaftData.variation("stone")));
        assertFalse(PresetEdits.showsStrength(MazeSettings.Type.UNIFORM));
        assertFalse(PresetEdits.showsStrength(MazeSettings.Type.LINES));
        assertTrue(PresetEdits.showsStrength(MazeSettings.Type.SERPENTINE));
        assertTrue(PresetEdits.showsStrength(MazeSettings.Type.RIBS));
        assertTrue(PresetEdits.showsStrength(MazeSettings.Type.GILBERT));
    }

    @Test
    void chaosSlidersMapToTheirRanges() {
        assertEquals(ChaosSettings.MIN_SPACING, this.sliders.chaosSpacing(0));
        assertEquals(ChaosSettings.MAX_SPACING, this.sliders.chaosSpacing(PresetSliders.CHAOS_SPACING_STEPS));
        assertEquals(0, this.sliders.chaosSpacingStep(ChaosSettings.DEFAULT.spacing()));
        assertEquals(ChaosSettings.MAX_CORNER_RADIUS, this.sliders.cornerRadius(PresetSliders.CORNER_RADIUS_STEPS));
        assertEquals(1, this.sliders.cornerRadiusStep(ChaosSettings.DEFAULT.cornerRadius()));
    }

    @Test
    void editedVariationSurvivesExportAndImport() {
        ShaftVariation variation = ShippedShaftData.variation("stone");
        variation = this.edits.withPatternType(variation, PatternType.TILES);
        variation = this.edits.withPattern(variation, TilesSettings.class, tiles -> tiles.withSize(TilesSettings.Size.SMALL).withStagger(TilesSettings.Stagger.Y));
        variation = this.edits.withPaths(variation, paths -> this.edits.withPathChanceStep(paths.withSurfacePaths(true).withCutting(true), 9));
        variation = variation.withShape(PillarShape.BRIDGE).withSurface(Blocks.DIRT_PATH.defaultBlockState()).withTrusses(Blocks.CHAIN.defaultBlockState());
        SettingsJson json = new SettingsJson();
        assertEquals(variation, json.parseVariation(json.exportVariation(variation)).getOrThrow());
    }

}
