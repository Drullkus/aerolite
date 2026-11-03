package us.drullk.aerolite.devtools.providers;

/**
 * The block state provider types of Minecraft 1.21.1, with the names of the mock.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum ProviderType {
    SIMPLE("Simple state", "minecraft:simple_state_provider"),
    WEIGHTED("Weighted", "minecraft:weighted_state_provider"),
    NOISE_THRESHOLD("Noise threshold", "minecraft:noise_threshold_provider"),
    NOISE("Noise", "minecraft:noise_provider"),
    DUAL_NOISE("Dual noise", "minecraft:dual_noise_provider"),
    ROTATED("Rotated block", "minecraft:rotated_block_provider"),
    RANDOMIZED_INT("Randomized int", "minecraft:randomized_int_state_provider");

    private final String displayName;
    private final String id;

    ProviderType(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public String displayName() {
        return this.displayName;
    }

    public String id() {
        return this.id;
    }

}
