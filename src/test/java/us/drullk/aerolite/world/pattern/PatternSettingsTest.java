package us.drullk.aerolite.world.pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON of the pattern settings: the dispatch on the key {@code type}, the defaults, the clamps and the round trip of every option.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class PatternSettingsTest {

    private static final List<String> SHARED_KEYS = List.of("allow_loops", "loop_share", "surface_paths", "cutting", "path_chance");

    private static DataResult<PatternSettings> parse(String json) {
        return PatternSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static JsonObject write(PatternSettings settings) {
        return PatternSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow().getAsJsonObject();
    }

    private static TilesSettings.Stagger stagger(String json) {
        return ((TilesSettings) parse(json).getOrThrow()).stagger();
    }

    private static List<String> keys(PatternSettings settings) {
        return List.copyOf(write(settings).keySet());
    }

    private static List<String> join(List<String> first, List<String> second) {
        List<String> joined = new ArrayList<>(first);
        joined.addAll(second);
        return joined;
    }

    private static void assertRoundTrip(PatternSettings settings) {
        JsonElement json = write(settings);
        assertEquals(settings, PatternSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), json.toString());
    }

    /** Each value of each shared option at least once, with the values that the defaults do not hold. */
    private static List<SurfacePathSettings> sharedVariants() {
        List<SurfacePathSettings> variants = new ArrayList<>();
        for (boolean allowLoops : new boolean[]{true, false}) {
            for (boolean surfacePaths : new boolean[]{true, false}) {
                for (boolean cutting : new boolean[]{true, false}) {
                    for (float share : new float[]{0, 0.35F, 1}) {
                        variants.add(new SurfacePathSettings(allowLoops, share, surfacePaths, cutting, 1 - share));
                    }
                }
            }
        }
        return variants;
    }

    @Test
    void defaultIsTheUniformMaze() {
        assertEquals(MazeSettings.DEFAULT, PatternSettings.standard());
        assertEquals(new MazeSettings(MazeSettings.Type.UNIFORM, 0, MazeSettings.Seam.RANDOM, 1), MazeSettings.DEFAULT);
        assertEquals(new SurfacePathSettings(true, 0.5F, false, false, 0.75F), SurfacePathSettings.DEFAULT);
        assertEquals(new BricksSettings(BricksSettings.Size.LARGE, SurfacePathSettings.DEFAULT), BricksSettings.DEFAULT);
        assertEquals(new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, false, SurfacePathSettings.DEFAULT), TilesSettings.DEFAULT);
        assertEquals(new ScalesSettings(SurfacePathSettings.DEFAULT, false), ScalesSettings.DEFAULT);
        assertEquals(new ChaosSettings(ChaosSettings.Cells.VORONOI, 6, 0.5F, 1, SurfacePathSettings.DEFAULT), ChaosSettings.DEFAULT);
    }

    @Test
    void missingFieldsTakeTheDefaults() {
        assertEquals(MazeSettings.DEFAULT, parse("{\"type\": \"aerolite:mazes\"}").getOrThrow());
        assertEquals(BricksSettings.DEFAULT, parse("{\"type\": \"aerolite:bricks\"}").getOrThrow());
        assertEquals(TilesSettings.DEFAULT, parse("{\"type\": \"aerolite:tiles\"}").getOrThrow());
        assertEquals(ScalesSettings.DEFAULT, parse("{\"type\": \"aerolite:scales\"}").getOrThrow());
        assertEquals(ChaosSettings.DEFAULT, parse("{\"type\": \"aerolite:chaos\"}").getOrThrow());
    }

    @Test
    void everyFieldIsWritten() {
        assertEquals(List.of("type", "maze", "strength", "seam", "spacing"), keys(MazeSettings.DEFAULT));
        assertEquals(join(List.of("type", "size"), SHARED_KEYS), keys(BricksSettings.DEFAULT));
        assertEquals(join(List.of("type", "size", "stagger", "encased"), SHARED_KEYS), keys(TilesSettings.DEFAULT));
        assertEquals(join(join(List.of("type"), SHARED_KEYS), List.of("paths_over_tails")), keys(ScalesSettings.DEFAULT));
        assertEquals(join(List.of("type", "cells", "spacing", "jitter", "corner_radius"), SHARED_KEYS), keys(ChaosSettings.DEFAULT));
        for (PatternType type : PatternType.values()) {
            PatternSettings settings = parse("{\"type\": \"" + type.getSerializedName() + "\"}").getOrThrow();
            assertEquals(type, settings.patternType());
            assertEquals(type.getSerializedName(), write(settings).get("type").getAsString());
        }
    }

    @Test
    void mazesRoundTripWithEveryOption() {
        for (MazeSettings.Type type : MazeSettings.Type.values()) {
            for (MazeSettings.Seam seam : MazeSettings.Seam.values()) {
                for (int spacing = MazeSettings.MIN_SPACING; spacing <= MazeSettings.MAX_SPACING; spacing++) {
                    for (float strength : new float[]{0, 0.05F, 0.35F, 0.95F, 1}) {
                        assertRoundTrip(new MazeSettings(type, strength, seam, spacing));
                    }
                }
            }
        }
    }

    @Test
    void bricksRoundTripWithEveryOption() {
        for (BricksSettings.Size size : BricksSettings.Size.values()) {
            for (SurfacePathSettings paths : sharedVariants()) {
                assertRoundTrip(new BricksSettings(size, paths));
            }
        }
    }

    @Test
    void tilesRoundTripWithEveryOption() {
        for (TilesSettings.Size size : TilesSettings.Size.values()) {
            for (TilesSettings.Stagger stagger : TilesSettings.Stagger.values()) {
                for (boolean encased : new boolean[]{true, false}) {
                    for (SurfacePathSettings paths : sharedVariants()) {
                        assertRoundTrip(new TilesSettings(size, stagger, encased, paths));
                    }
                }
            }
        }
    }

    @Test
    void scalesRoundTripWithEveryOption() {
        for (boolean overTails : new boolean[]{true, false}) {
            for (SurfacePathSettings paths : sharedVariants()) {
                assertRoundTrip(new ScalesSettings(paths, overTails));
            }
        }
    }

    @Test
    void chaosRoundTripsWithEveryOption() {
        for (ChaosSettings.Cells cells : ChaosSettings.Cells.values()) {
            for (int spacing = ChaosSettings.MIN_SPACING; spacing <= ChaosSettings.MAX_SPACING; spacing++) {
                for (int radius = ChaosSettings.MIN_CORNER_RADIUS; radius <= ChaosSettings.MAX_CORNER_RADIUS; radius++) {
                    for (float jitter : new float[]{0, 0.25F, 0.5F, 1}) {
                        assertRoundTrip(new ChaosSettings(cells, spacing, jitter, radius, SurfacePathSettings.DEFAULT));
                    }
                }
            }
            for (SurfacePathSettings paths : sharedVariants()) {
                assertRoundTrip(ChaosSettings.DEFAULT.withCells(cells).withPaths(paths));
            }
        }
    }

    @Test
    void tilesDropAnXStaggerWithLargeOrEncasedTiles() {
        assertEquals(TilesSettings.Stagger.NONE, stagger("{\"type\": \"aerolite:tiles\", \"size\": \"large\", \"stagger\": \"x\"}"));
        assertEquals(TilesSettings.Stagger.NONE, stagger("{\"type\": \"aerolite:tiles\", \"stagger\": \"x\"}"));
        assertEquals(TilesSettings.Stagger.NONE, stagger("{\"type\": \"aerolite:tiles\", \"size\": \"small\", \"stagger\": \"x\", \"encased\": true}"));
        assertEquals(TilesSettings.Stagger.X, stagger("{\"type\": \"aerolite:tiles\", \"size\": \"small\", \"stagger\": \"x\"}"));
        assertEquals(TilesSettings.Stagger.Y, stagger("{\"type\": \"aerolite:tiles\", \"size\": \"large\", \"stagger\": \"y\", \"encased\": true}"));
        TilesSettings small = TilesSettings.DEFAULT.withSize(TilesSettings.Size.SMALL).withStagger(TilesSettings.Stagger.X);
        assertEquals(TilesSettings.Stagger.X, small.stagger());
        assertEquals(TilesSettings.Stagger.NONE, small.withEncased(true).stagger());
        assertEquals(TilesSettings.Stagger.NONE, small.withSize(TilesSettings.Size.LARGE).stagger());
    }

    @Test
    void unknownTypesFail() {
        assertTrue(parse("{\"type\": \"aerolite:waves\"}").isError());
        assertTrue(parse("{\"type\": \"mazes\"}").isError());
        assertTrue(parse("{\"type\": 3}").isError());
        assertTrue(parse("{}").isError());
        assertTrue(parse("{\"maze\": \"ribs\"}").isError());
    }

    @Test
    void unknownChoicesFail() {
        assertTrue(parse("{\"type\": \"aerolite:mazes\", \"maze\": \"spiral\"}").isError());
        assertTrue(parse("{\"type\": \"aerolite:mazes\", \"seam\": \"random_per_segment\"}").isError());
        assertTrue(parse("{\"type\": \"aerolite:bricks\", \"size\": \"huge\"}").isError());
        assertTrue(parse("{\"type\": \"aerolite:tiles\", \"size\": \"jumbled\"}").isError());
        assertTrue(parse("{\"type\": \"aerolite:tiles\", \"stagger\": \"z\"}").isError());
        assertTrue(parse("{\"type\": \"aerolite:chaos\", \"cells\": \"square\"}").isError());
        assertTrue(parse("{\"type\": \"aerolite:scales\", \"paths_over_tails\": \"yes\"}").isError());
    }

    @Test
    void numbersOutsideTheirRangeClamp() {
        assertEquals(MazeSettings.DEFAULT.withStrength(1).withSpacing(2),
                parse("{\"type\": \"aerolite:mazes\", \"strength\": 2, \"spacing\": 3}").getOrThrow());
        assertEquals(MazeSettings.DEFAULT, parse("{\"type\": \"aerolite:mazes\", \"strength\": -1, \"spacing\": 0}").getOrThrow());
        assertEquals(ChaosSettings.DEFAULT.withSpacing(6).withJitter(0).withCornerRadius(3),
                parse("{\"type\": \"aerolite:chaos\", \"spacing\": 4, \"jitter\": -0.5, \"corner_radius\": 5}").getOrThrow());
        assertEquals(ChaosSettings.DEFAULT.withSpacing(12).withJitter(1).withCornerRadius(0),
                parse("{\"type\": \"aerolite:chaos\", \"spacing\": 20, \"jitter\": 1.5, \"corner_radius\": -1}").getOrThrow());
        assertEquals(BricksSettings.DEFAULT.withPaths(SurfacePathSettings.DEFAULT.withLoopShare(1).withPathChance(0)),
                parse("{\"type\": \"aerolite:bricks\", \"loop_share\": 1.5, \"path_chance\": -0.25}").getOrThrow());
    }

}
