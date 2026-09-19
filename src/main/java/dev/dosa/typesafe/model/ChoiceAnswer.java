package dev.dosa.typesafe.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answer to a {@link ChoiceQuestion}.
 *
 * @param choice the option key the model picked
 * @param probabilities the model's probability for each option key
 * @param confidence the model's confidence in its pick, between 0 and 1
 */
public record ChoiceAnswer(String choice, Map<String, Double> probabilities, double confidence)
        implements Answer {

    /**
     * Canonical constructor. A {@code null} probabilities map is normalized to
     * an empty map; the map is always stored as an unmodifiable copy.
     */
    public ChoiceAnswer {
        probabilities = probabilities == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
    }

    @Override
    public String type() {
        return "choice";
    }
}
