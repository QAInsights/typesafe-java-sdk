package dev.dosa.typesafe.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Answer to a {@link ScoreQuestion}.
 *
 * @param score the score the model assigned, on the question's rubric scale
 * @param legend rubric level descriptions keyed by score index as a string
 *        (e.g. {@code "0"}, {@code "1"})
 * @param probabilities the model's probability for each rubric level, keyed the
 *        same way as {@code legend}; empty when the server omitted the field
 * @param confidence the model's confidence in its score, between 0 and 1
 */
public record ScoreAnswer(
        double score,
        Map<String, String> legend,
        Optional<Map<String, Double>> probabilities,
        double confidence)
        implements Answer {

    /**
     * Canonical constructor. {@code null} maps are normalized - {@code legend}
     * to an empty map and {@code probabilities} to {@link Optional#empty()} -
     * and maps are stored as unmodifiable copies.
     */
    public ScoreAnswer {
        legend = legend == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(legend));
        probabilities = probabilities == null
                ? Optional.empty()
                : probabilities.map(p -> Collections.unmodifiableMap(new LinkedHashMap<>(p)));
    }

    @Override
    public String type() {
        return "score";
    }
}
