package us.drullk.aerolite.world.shaft;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.shaft.maze.MazeEdges;
import us.drullk.aerolite.world.shaft.maze.MazePattern;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.shaft.shape.BridgeRing;
import us.drullk.aerolite.world.shaft.shape.IBeamRing;
import us.drullk.aerolite.world.shaft.shape.WebPlane;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the pattern dispatch and the maze surface.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class PatternBindingsTest {

    private static final String BINDING_PATH = "us/drullk/aerolite/world/shaft/binding";
    private static final String TEST_SOURCES = "src/test/java/" + BINDING_PATH;

    private final ShaftMazes mazes = new ShaftMazes(new MazeEdges());

    /**
     * The directory scan of the collector initializes every class under the binding package during mod construction.
     * A dev run loads the test classes as part of the mod, so no test class or test resource lives there.
     * The test classes share one union file system with the main classes, so this check reads the project folders.
     */
    @Test
    void noTestClassLivesUnderTheBindingPackage() {
        Path project = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (project != null && !Files.isDirectory(project.resolve("src/test/java"))) {
            project = project.getParent();
        }
        assertNotNull(project, "The project folder is not above " + System.getProperty("user.dir"));
        assertTrue(Files.isDirectory(project.resolve(TEST_SOURCES).getParent()), "The test sources of the shaft package are missing");
        for (String folder : List.of(TEST_SOURCES, "src/test/resources/" + BINDING_PATH, "build/classes/java/test/" + BINDING_PATH, "build/resources/test/" + BINDING_PATH)) {
            assertFalse(Files.exists(project.resolve(folder)), "A test file lives under the binding package: " + folder);
        }
    }

    @Test
    void mazeSettingsMakeAMazePattern() {
        SurfacePattern pattern = this.mazes.pattern(TestSettings.STONE.withBias(0.3F), (min, max) -> List.of(), new XoroshiroRandomSource(1).forkPositional(), -10, 10);
        assertInstanceOf(MazePattern.class, pattern);
        assertEquals(0.3F, pattern.bias());
        assertSame(PillarRing.INSTANCE, pattern.ring());
    }

    @Test
    void settingsWithoutBindingFallBackToTheMaze() {
        SurfacePattern pattern = this.mazes.pattern(TestSettings.STONE.withPattern(BricksSettings.DEFAULT), (min, max) -> List.of(), new XoroshiroRandomSource(1).forkPositional(), -10, 10);
        assertInstanceOf(MazePattern.class, pattern);
    }

    @Test
    void eachShapeHasItsRing() {
        assertSame(PillarRing.INSTANCE, this.mazes.ring(PillarShape.PILLAR));
        assertSame(IBeamRing.STACK_FIRST, this.mazes.ring(PillarShape.I_BEAM));
        assertSame(BridgeRing.STACK_FIRST, this.mazes.ring(PillarShape.BRIDGE));
        assertSame(IBeamRing.STACK_SECOND, this.mazes.ring(PillarShape.I_BEAM, WebPlane.Z));
        assertSame(BridgeRing.STACK_SECOND, this.mazes.ring(PillarShape.BRIDGE, WebPlane.Z));
    }

    /** The box rows are box cells. Every ruling node is a groove cell. */
    @Test
    void mazeSurfaceMarksBoxRowsAndNodes() {
        ShaftMaze.Box box = new ShaftMaze.Box(203, 1);
        SurfacePattern pattern = this.mazes.pattern(TestSettings.STONE, (min, max) -> box.center() >= min && box.center() <= max ? List.of(box) : List.of(),
                new XoroshiroRandomSource(3).forkPositional(), -100, 100);
        SegmentSurface surface = pattern.segmentAt(12);
        assertEquals(List.of(box), surface.boxes());
        Band band = surface.band(0);
        assertEquals(ShaftMaze.CHUNK * surface.firstChunk(), band.axis0());
        assertEquals(ShaftMaze.CHUNK * (surface.lastChunk() + 1) + 1, band.axisEnd());
        for (int index = 0; index < band.ringLength(); index++) {
            assertEquals(BandKind.BOX, band.get(index, box.center()));
        }
        int lastRuling = ShaftMaze.CHUNK * surface.lastChunk() + ShaftMaze.CHUNK - 1;
        assertEquals(BandKind.GROOVE, band.get(2, lastRuling), "A face node of the last ruling does not carve");
        assertTrue(band.grooveNodes().contains(band.pos(2, lastRuling)));
        assertEquals(BandKind.GROOVE, surface.patch(box.center()).get(0, 0), "The patch center node does not carve");
    }

}
