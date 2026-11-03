package us.drullk.aerolite.devtools.providers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The seed, the noise parameters and the scale of one noise of a sample provider.
 * Each text field keeps its text. The value keeps the last good parse of that text.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class NoiseSample {

    /** The scale of each step of the scale slider. */
    public static final double[] SCALES = {0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1};
    public static final int MIN_OCTAVE = -10;

    public String seedText;
    public long seed;
    public int firstOctave;
    public String amplitudesText;
    public List<Double> amplitudes;
    public int scaleStep;

    public NoiseSample(long seed, int firstOctave, String amplitudes, int scaleStep) {
        this.seedText = Long.toString(seed);
        this.seed = seed;
        this.firstOctave = firstOctave;
        this.amplitudesText = amplitudes;
        List<Double> parsed = parseAmplitudes(amplitudes);
        this.amplitudes = parsed == null ? new ArrayList<>(List.of(1.0)) : parsed;
        this.scaleStep = scaleStep;
    }

    private NoiseSample(NoiseSample other) {
        this.seedText = other.seedText;
        this.seed = other.seed;
        this.firstOctave = other.firstOctave;
        this.amplitudesText = other.amplitudesText;
        this.amplitudes = new ArrayList<>(other.amplitudes);
        this.scaleStep = other.scaleStep;
    }

    public NoiseSample copy() {
        return new NoiseSample(this);
    }

    public double scale() {
        return SCALES[this.scaleStep];
    }

    /** The seed of the text, or null when the text is no whole number. */
    @Nullable
    public static Long parseSeed(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** The amplitudes of a comma list, or null when one entry is no number. */
    @Nullable
    public static List<Double> parseAmplitudes(String text) {
        List<Double> values = new ArrayList<>();
        for (String part : text.split(",")) {
            try {
                values.add(Double.parseDouble(part.trim()));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return values;
    }

}
