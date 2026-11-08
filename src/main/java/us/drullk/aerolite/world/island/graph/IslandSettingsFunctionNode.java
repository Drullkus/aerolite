package us.drullk.aerolite.world.island.graph;

import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.IslandSettings;

import java.util.Optional;

/**
 * Maps the island settings to and from the fields of an {@code aerolite:voronoi_islands} function node. The noise field stays as it is.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class IslandSettingsFunctionNode {

    private IslandSettingsFunctionNode() {
    }

    public static boolean isIslands(FunctionNode functionNode) {
        return functionNode instanceof FunctionNode.Typed typed && typed.type().id().equals(AeroliteWorldgen.VORONOI_ISLANDS_TYPE);
    }

    /** The island settings of the function node. Empty for another type or a field without a value of its kind. */
    public static Optional<IslandSettings> read(FunctionNode functionNode) {
        if (!isIslands(functionNode)) {
            return Optional.empty();
        }
        FunctionNode.Typed typed = (FunctionNode.Typed) functionNode;
        if (!(typed.values().get("cell_size") instanceof FieldValue.Int cellSize)
                || !(typed.values().get("cell_height") instanceof FieldValue.Int cellHeight)
                || !(typed.values().get("layer_min_y") instanceof FieldValue.Int layerMinY)
                || !(typed.values().get("jitter") instanceof FieldValue.Number jitter)
                || !(typed.values().get("fill_min") instanceof FieldValue.Number fillMin)
                || !(typed.values().get("fill_max") instanceof FieldValue.Number fillMax)
                || !(typed.values().get("empty_chance") instanceof FieldValue.Number emptyChance)
                || !(typed.values().get("distortion") instanceof FieldValue.Number distortion)
                || !(typed.values().get("distortion_scale") instanceof FieldValue.Int distortionScale)
                || !(typed.values().get("distortion_octaves") instanceof FieldValue.Int distortionOctaves)) {
            return Optional.empty();
        }
        return Optional.of(new IslandSettings(cellSize.value(), cellHeight.value(), layerMinY.value(), jitter.value(), fillMin.value(), fillMax.value(),
                emptyChance.value(), distortion.value(), distortionScale.value(), distortionOctaves.value()));
    }

    /** The function node with the island settings in its fields. */
    public static FunctionNode.Typed write(FunctionNode.Typed functionNode, IslandSettings settings) {
        if (!isIslands(functionNode)) {
            throw new IllegalArgumentException("The function node is not of the type " + AeroliteWorldgen.VORONOI_ISLANDS_TYPE);
        }
        return functionNode
                .with("cell_size", new FieldValue.Int(settings.cellSize()))
                .with("cell_height", new FieldValue.Int(settings.cellHeight()))
                .with("layer_min_y", new FieldValue.Int(settings.layerMinY()))
                .with("jitter", new FieldValue.Number(settings.jitter()))
                .with("fill_min", new FieldValue.Number(settings.fillMin()))
                .with("fill_max", new FieldValue.Number(settings.fillMax()))
                .with("empty_chance", new FieldValue.Number(settings.emptyChance()))
                .with("distortion", new FieldValue.Number(settings.distortion()))
                .with("distortion_scale", new FieldValue.Int(settings.distortionScale()))
                .with("distortion_octaves", new FieldValue.Int(settings.distortionOctaves()));
    }

}
