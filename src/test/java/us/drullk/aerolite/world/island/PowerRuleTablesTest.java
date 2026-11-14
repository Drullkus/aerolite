package us.drullk.aerolite.world.island;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the power rule math against the tables of the prototype, {@code prototypes/cells/tables.json}.
 * The test reads the file from the nearest project folder. A missing file fails the test.
 * Each case holds explicit sites, and every site of a case is a cell.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class PowerRuleTablesTest {

    private static final String TABLES = "prototypes/cells/tables.json";

    @Test
    void everyCaseAndQueryMatchesThePrototype() throws IOException {
        JsonObject tables = JsonParser.parseString(Files.readString(tablesFile())).getAsJsonObject();
        double tolerance = tables.get("tolerance").getAsDouble();
        JsonArray cases = tables.getAsJsonArray("cases");
        assertEquals(6, cases.size(), "The tables hold another case count");
        int queries = 0;
        for (JsonElement element : cases) {
            queries += checkCase(element.getAsJsonObject(), tolerance);
        }
        assertTrue(queries >= 40, "Only " + queries + " queries");
    }

    /** Checks one case and gives its query count. */
    private static int checkCase(JsonObject table, double tolerance) {
        String name = table.get("name").getAsString();
        double cellSize = table.get("cell_size").getAsDouble();
        double yScale = cellSize / table.get("cell_height").getAsDouble();
        JsonArray siteArray = table.getAsJsonArray("sites");
        int count = siteArray.size();
        double[] sites = new double[3 * count];
        double[] weights = new double[count];
        double[] fills = new double[count];
        int[] rows = new int[count];
        for (int i = 0; i < count; i++) {
            JsonObject site = siteArray.get(i).getAsJsonObject();
            sites[3 * i] = site.get("x").getAsDouble();
            sites[3 * i + 1] = site.get("y").getAsDouble() * yScale;
            sites[3 * i + 2] = site.get("z").getAsDouble();
            weights[i] = site.get("weight").getAsDouble();
            fills[i] = site.get("fill").getAsDouble();
            rows[i] = site.get("row").getAsInt();
        }
        PowerRule.Walls[] walls = new PowerRule.Walls[count];
        JsonArray lostArray = table.getAsJsonArray("lost");
        for (int i = 0; i < count; i++) {
            walls[i] = wallsOf(i, sites, weights, cellSize, yScale);
            assertEquals(lostArray.get(i).getAsBoolean(), walls[i].lost(), name + ": the lost flag of site " + i);
        }
        JsonArray queries = table.getAsJsonArray("queries");
        for (int q = 0; q < queries.size(); q++) {
            JsonObject query = queries.get(q).getAsJsonObject();
            String where = name + ", query " + q;
            JsonArray point = query.getAsJsonArray("point");
            double px = point.get(0).getAsDouble();
            double py = point.get(1).getAsDouble() * yScale;
            double pz = point.get(2).getAsDouble();
            int owner = PowerRule.owner(px, py, pz, sites, weights, count, cellSize);
            assertEquals(query.get("owner").getAsInt(), owner, where + ": the owner");
            JsonArray power = query.getAsJsonArray("power");
            for (int i = 0; i < count; i++) {
                double value = PowerRule.value(px, py, pz, sites[3 * i], sites[3 * i + 1], sites[3 * i + 2], weights[i], cellSize);
                near(power.get(i).getAsDouble(), value, tolerance, where + ": the power rule value of site " + i);
            }
            for (JsonElement wallElement : query.getAsJsonArray("walls")) {
                JsonObject wall = wallElement.getAsJsonObject();
                int other = wall.get("other").getAsInt();
                double h = PowerRule.h(sites[3 * other] - sites[3 * owner], sites[3 * other + 1] - sites[3 * owner + 1], sites[3 * other + 2] - sites[3 * owner + 2],
                        weights[owner], weights[other], cellSize);
                near(wall.get("h").getAsDouble(), h, tolerance, where + ": h toward site " + other);
            }
            PowerRule.Walls own = walls[owner];
            assertEquals(query.get("lost").getAsBoolean(), own.lost(), where + ": the lost flag");
            if (own.lost()) {
                for (String key : new String[] {"gauge", "wall_distance", "cell_radius", "island_body"}) {
                    assertTrue(query.get(key).isJsonNull(), where + ": " + key + " of a lost owner");
                }
                assertTrue(Double.isNaN(own.gauge(px, py, pz)) && Double.isNaN(own.wallDistance(px, py, pz)) && Double.isNaN(own.cellRadius()), where + ": a value of a lost owner");
                continue;
            }
            double gauge = own.gauge(px, py, pz);
            near(query.get("gauge").getAsDouble(), gauge, tolerance, where + ": the gauge");
            near(query.get("wall_distance").getAsDouble(), own.wallDistance(px, py, pz), tolerance, where + ": the wall distance");
            near(query.get("cell_radius").getAsDouble(), own.cellRadius(), tolerance, where + ": the cell radius");
            if (rows[owner] == 0) {
                near(query.get("island_body").getAsDouble(), PowerRule.islandBody(fills[owner], gauge, own.cellRadius()), tolerance, where + ": the island body");
            } else {
                assertTrue(query.get("island_body").isJsonNull(), where + ": an island body outside the row 0");
            }
        }
        return queries.size();
    }

    /** The walls of one site toward every other site of the case. */
    private static PowerRule.Walls wallsOf(int index, double[] sites, double[] weights, double cellSize, double yScale) {
        int count = weights.length - 1;
        double[] others = new double[3 * count];
        double[] otherWeights = new double[count];
        int at = 0;
        for (int i = 0; i < weights.length; i++) {
            if (i == index) {
                continue;
            }
            System.arraycopy(sites, 3 * i, others, 3 * at, 3);
            otherWeights[at++] = weights[i];
        }
        return PowerRule.walls(sites[3 * index], sites[3 * index + 1], sites[3 * index + 2], weights[index], others, otherWeights, count, cellSize, yScale);
    }

    private static void near(double expected, double actual, double tolerance, String message) {
        assertTrue(Math.abs(actual - expected) <= tolerance * Math.max(1, Math.abs(expected)), message + ": expected " + expected + ", got " + actual);
    }

    /** The tables file of the nearest project folder above the working folder. */
    static Path tablesFile() {
        Path project = Path.of("").toAbsolutePath();
        while (project != null && !Files.isRegularFile(project.resolve("settings.gradle"))) {
            project = project.getParent();
        }
        if (project == null) {
            throw new AssertionError("No project folder above " + Path.of("").toAbsolutePath());
        }
        Path file = project.resolve(TABLES);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("No " + TABLES + " in the project folder " + project);
        }
        return file;
    }

}
