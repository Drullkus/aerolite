package us.drullk.aerolite.world.shaft.shape;

import tamaized.beanification.Component;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.RingShape;

/**
 * Gives the ring description of each pillar shape.
 * A horizontal pillar puts v on its first offset, which is Y. A column puts v on X for web plane X and on Z for web plane Z.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ShapeRings {

    public ShapeRings() {
    }

    /** The ring description of a horizontal pillar of the shape. */
    public RingShape ring(PillarShape shape) {
        return this.ring(shape, WebPlane.X);
    }

    /** The ring description of a column of the shape with its web plane. */
    public RingShape ring(PillarShape shape, WebPlane plane) {
        return switch (shape) {
            case PILLAR -> PillarRing.INSTANCE;
            case I_BEAM -> plane == WebPlane.X ? IBeamRing.STACK_FIRST : IBeamRing.STACK_SECOND;
            case BRIDGE -> plane == WebPlane.X ? BridgeRing.STACK_FIRST : BridgeRing.STACK_SECOND;
        };
    }

}
