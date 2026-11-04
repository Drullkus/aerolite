package us.drullk.aerolite.world.shaft.pattern.scales;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.pattern.PatternCache;
import us.drullk.aerolite.world.shaft.pattern.tiles.TilesPattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;

import java.util.List;

/**
 * The scales pattern: small tiles with stagger X plus a tail block on each tile.
 *
 * <p>The rules follow {@code prototypes/tiles/README.md}.
 * The tail is the block of the ring groove row next to the tile, in the tile center column, on the tail side.
 * It stays wall and caps the axis groove of the next course. The pillar random picks the tail side.
 * The rationing never removes a tail.
 * With paths over tails on, a surface path can run from the tile center over the tail into the axis groove beyond it.
 * A cut path then carves the tail. That tail has path on all four sides, the one accepted exception to the plus rule.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ScalesPattern extends TilesPattern {

    /** The tail points toward the positive axis. */
    public static final int TAIL_DOWN = 1;
    /** The tail points toward the negative axis. */
    public static final int TAIL_UP = -1;

    private final int tailSign;
    private final boolean overTails;

    /** A pattern with a cache of its own. */
    public ScalesPattern(PatternContext context, ScalesSettings settings) {
        this(context, settings, 0, new PatternCache());
    }

    public ScalesPattern(PatternContext context, ScalesSettings settings, PatternCache cache) {
        this(context, settings, 0, cache);
    }

    /**
     * @param tailSign {@link #TAIL_DOWN}, {@link #TAIL_UP}, or 0 to let the pillar random pick
     */
    public ScalesPattern(PatternContext context, ScalesSettings settings, int tailSign, PatternCache cache) {
        super(context, TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, settings.paths(), cache);
        this.tailSign = tailSign != 0 ? Integer.signum(tailSign) : this.pillarBit(SALT_TAIL) ? TAIL_DOWN : TAIL_UP;
        this.overTails = settings.pathsOverTails();
    }

    public int tailSign() {
        return this.tailSign;
    }

    @Override
    protected Object options() {
        return List.of(ScalesPattern.class, this.tailSign, this.overTails, this.paths());
    }

    @Override
    protected long tailOf(int ring, int k, int center) {
        int period = this.period();
        return this.pos(ring, center, this.tailSign > 0 ? period * k + period - 1 : period * k - 1);
    }

    @Override
    protected LongSet tails(Band band, int ring) {
        LongSet out = new LongOpenHashSet();
        for (int k = course(band.axis0()) - 1; k <= course(band.axisEnd()) + 1; k++) {
            for (IntList run : this.xRuns(ring, k)) {
                long tail = this.tailOf(ring, k, run.getInt(run.size() / 2));
                if (band.inside(tail)) {
                    out.add(tail);
                }
            }
        }
        return out;
    }

    @Override
    protected boolean pathsOverTails() {
        return this.overTails && this.placesPaths();
    }

}
