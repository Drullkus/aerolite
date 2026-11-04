package us.drullk.aerolite.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.shaft.binding.PatternBindings;
import us.drullk.aerolite.world.shaft.maze.MazePattern;
import us.drullk.aerolite.world.shaft.binding.maze.MazePatternBinding;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

import java.util.List;

/**
 * GameTests of the pattern bindings in the bean context.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class PatternGameTests {

    // GameTest methods are static. Beanification fills these fields.
    @Autowired
    private static PatternBindings bindings;
    @Autowired
    private static ShaftMazes mazes;

    private PatternGameTests() {
    }

    @GameTest(template = "empty")
    public static void collectorFindsTheMazeBinding(GameTestHelper helper) {
        helper.assertTrue(bindings.bindings().stream().anyMatch(MazePatternBinding.class::isInstance), "The collector holds no maze binding");
        helper.assertTrue(bindings.binding(MazeSettings.class) instanceof MazePatternBinding, "The maze settings do not pick the maze binding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patternWithoutBindingPaintsTheMaze(GameTestHelper helper) {
        helper.assertTrue(mazes.pattern(TestSettings.STONE.withPattern(BricksSettings.DEFAULT), (min, max) -> List.of(), new XoroshiroRandomSource(1).forkPositional(), -4, 4)
                instanceof MazePattern, "The bricks pattern does not fall back to the maze");
        helper.succeed();
    }

}
