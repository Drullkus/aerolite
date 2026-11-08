package us.drullk.aerolite.world.island.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * A cubic spline of the vanilla type {@code minecraft:spline}, without its coordinates.
 * The coordinate of each multipoint level is an input pin of the function node. Its pin name is the path of the level and {@code coordinate}.
 * The numbers stay doubles, so a save writes the loaded text. Vanilla reads them as floats.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public sealed interface SplineValue {

    /** The pin name element of a spline coordinate. */
    String COORDINATE = "coordinate";

    /** Adds the coordinate pin names of this spline and its nested splines, depth first, to the list. */
    void coordinatePins(String path, List<String> pins);

    default List<String> coordinatePins(String path) {
        List<String> pins = new ArrayList<>();
        this.coordinatePins(path, pins);
        return List.copyOf(pins);
    }

    /** A spline of one value. Its JSON is a bare number. */
    record Constant(double value) implements SplineValue {

        @Override
        public void coordinatePins(String path, List<String> pins) {
        }
    }

    /** A spline over a coordinate. Its points hold a location, a value and a derivative. */
    record Multipoint(List<Point> points) implements SplineValue {

        public Multipoint {
            points = List.copyOf(points);
        }

        @Override
        public void coordinatePins(String path, List<String> pins) {
            pins.add(path + "/" + COORDINATE);
            for (int index = 0; index < this.points.size(); index++) {
                this.points.get(index).value().coordinatePins(path + "/points/" + index + "/value", pins);
            }
        }
    }

    record Point(double location, SplineValue value, double derivative) {
    }

}
