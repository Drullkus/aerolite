package us.drullk.aerolite.world.shaft;

import tamaized.beanification.Component;

/**
 * Makes the frame of a horizontal shaft or a column.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component
public final class ShaftFrames {

    public ShaftFrame of(ShaftLayout.Shaft shaft) {
        return shaft.axis() == ShaftLayout.Axis.X
                ? new ShaftFrame(ShaftFrame.Kind.ALONG_X, shaft.centerY(), shaft.position())
                : new ShaftFrame(ShaftFrame.Kind.ALONG_Z, shaft.centerY(), shaft.position());
    }

    public ShaftFrame of(ShaftLayout.Column column) {
        return new ShaftFrame(ShaftFrame.Kind.COLUMN, column.x(), column.z());
    }

}
