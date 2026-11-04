package us.drullk.aerolite.world.shaft.surface;

/**
 * One solid block of a cross section.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param outer true when the block is on the outer layer, which holds the wall layer of the pattern
 */
public record SectionBlock(int first, int second, boolean outer) {
}
