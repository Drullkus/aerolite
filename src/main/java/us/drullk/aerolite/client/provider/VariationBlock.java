package us.drullk.aerolite.client.provider;

import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import us.drullk.aerolite.client.PresetEdits;
import us.drullk.aerolite.world.ShaftVariation;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The block fields of a variation, in the sequence of the screens: wall, path, interior, junction, surface and trusses.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum VariationBlock {
    WALL("wall_block", ShaftVariation::wall, ShaftVariation::withWall),
    PATH("path_block", ShaftVariation::path, ShaftVariation::withPath),
    INTERIOR("interior_block", ShaftVariation::interior, ShaftVariation::withInterior),
    JUNCTION("junction_block", ShaftVariation::junction, ShaftVariation::withJunction),
    SURFACE("surface_block", ShaftVariation::surface, ShaftVariation::withSurface),
    TRUSSES("trusses_block", ShaftVariation::trusses, ShaftVariation::withTrusses);

    private final String key;
    private final Function<ShaftVariation, BlockStateProvider> getter;
    private final BiFunction<ShaftVariation, BlockStateProvider, ShaftVariation> setter;

    VariationBlock(String key, Function<ShaftVariation, BlockStateProvider> getter, BiFunction<ShaftVariation, BlockStateProvider, ShaftVariation> setter) {
        this.key = key;
        this.getter = getter;
        this.setter = setter;
    }

    /** The lang key of the field label, such as {@code createWorld.customize.aerolite.wall_block}. */
    public String key() {
        return "createWorld.customize.aerolite." + this.key;
    }

    public BlockStateProvider get(ShaftVariation variation) {
        return this.getter.apply(variation);
    }

    public ShaftVariation with(ShaftVariation variation, BlockStateProvider provider) {
        return this.setter.apply(variation, provider);
    }

    /** The fields that a variation shows. Only the shape Bridge shows the trusses. */
    public static List<VariationBlock> shown(ShaftVariation variation) {
        return Arrays.stream(values()).filter(block -> block != TRUSSES || PresetEdits.showsTrusses(variation)).toList();
    }

}
