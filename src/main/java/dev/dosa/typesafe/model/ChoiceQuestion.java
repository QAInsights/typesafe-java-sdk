package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dosa.typesafe.exception.InvalidRequestException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A choice question: the model picks exactly one of the defined options.
 *
 * <p>Created via {@link Question#choice(String)}. Requires between
 * {@value #MIN_OPTIONS} and {@value #MAX_OPTIONS} options - validated when the
 * request is built, before any network call. Options are stored in a
 * {@link LinkedHashMap}, so the wire payload preserves the order in which
 * {@link #option(String, String)} was called.</p>
 */
public final class ChoiceQuestion extends Question {

    /** Minimum number of options a choice question must define. */
    public static final int MIN_OPTIONS = 2;

    /** Maximum number of options a choice question may define. */
    public static final int MAX_OPTIONS = 255;

    private final LinkedHashMap<String, String> options = new LinkedHashMap<>();

    ChoiceQuestion(String instructions) {
        super(instructions);
    }

    /**
     * Adds an option the model may pick.
     *
     * @param key the option key returned in the answer
     * @param description a description of what this option means
     * @return this question, for chaining
     * @throws InvalidRequestException if {@code key} or {@code description} is
     *         null or empty
     */
    public ChoiceQuestion option(String key, String description) {
        if (key == null || key.isEmpty()) {
            throw new InvalidRequestException("Choice option key must not be null or empty");
        }
        if (description == null || description.isEmpty()) {
            throw new InvalidRequestException("Choice option description must not be null or empty");
        }
        options.put(key, description);
        return this;
    }

    /**
     * Returns the defined options in insertion order.
     *
     * @return an unmodifiable map of option key to description
     */
    public Map<String, String> options() {
        return Collections.unmodifiableMap(options);
    }

    @Override
    String type() {
        return "choice";
    }

    @Override
    JsonNode criteriaJson() {
        ObjectNode node = Json.MAPPER.createObjectNode();
        options.forEach(node::put);
        return node;
    }

    @Override
    void validate() {
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new InvalidRequestException("Choice question \"" + instructions()
                    + "\" requires between " + MIN_OPTIONS + " and " + MAX_OPTIONS
                    + " options, got " + options.size());
        }
    }
}
