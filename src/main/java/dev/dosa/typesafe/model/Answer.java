package dev.dosa.typesafe.model;

/**
 * Base type for a single System One answer.
 *
 * <p>This is a sealed hierarchy with four concrete variants mirroring the
 * question types — {@link NoulAnswer}, {@link ChoiceAnswer},
 * {@link ScoreAnswer} — plus {@link UnknownAnswer}, which captures any
 * unrecognized or future {@code "type"} value so responses remain
 * forward-compatible.</p>
 *
 * <p>Use the typed accessors on {@link SystemOneResponse} (e.g.
 * {@link SystemOneResponse#noul(String)}) rather than casting.</p>
 */
public sealed interface Answer permits NoulAnswer, ChoiceAnswer, ScoreAnswer, UnknownAnswer {

    /**
     * Returns the wire {@code "type"} discriminator of this answer
     * ({@code "noul"}, {@code "choice"}, {@code "score"}, or the raw
     * unrecognized value for {@link UnknownAnswer}).
     *
     * @return the answer type
     */
    String type();
}
