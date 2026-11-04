package us.drullk.aerolite.world.shaft.shape;

import tamaized.beanification.Component;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.RingShape;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives the ring description of each pillar shape.
 * A shape without its own ring description takes the Pillar ring and logs one line.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ShapeRings {

    private final Set<PillarShape> fallbacks = ConcurrentHashMap.newKeySet();

    public RingShape ring(PillarShape shape) {
        return switch (shape) {
            case PILLAR -> PillarRing.INSTANCE;
            case I_BEAM, BRIDGE -> this.fallback(shape);
        };
    }

    private RingShape fallback(PillarShape shape) {
        if (this.fallbacks.add(shape)) {
            Aerolite.LOGGER.warn("The shape {} has no ring description yet. It paints as a pillar.", shape.getSerializedName());
        }
        return PillarRing.INSTANCE;
    }

}
