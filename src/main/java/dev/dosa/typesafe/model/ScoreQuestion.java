package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.dosa.typesafe.exception.InvalidRequestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A score question: the model rates the state against an ordered rubric.
 *
 * <p>Created via {@link Question#score(String)}. Each {@link #level(String)}
 * call appends one rubric level; the level's index in the list is the integer
 * score it represents. Requires at least {@value #MIN_LEVELS} levels - validated
 * when the request is built, before any network call.</p>
 */
public final class ScoreQuestion extends Question {

    /** Minimum number of rubric levels a score question must define. */
    public static final int MIN_LEVELS = 2;

    private final List<String> levels = new ArrayList<>();

    ScoreQuestion(String instructions) {
        super(instructions);
    }

    /**
     * Appends a rubric level. The first level added is score 0, the next is
     * score 1, and so on.
     *
     * @param description a description of what this level means
     * @return this question, for chaining
     * @throws InvalidRequestException if {@code description} is null or empty
     */
    public ScoreQuestion level(String description) {
        if (description == null || description.isEmpty()) {
            throw new InvalidRequestException("Score level description must not be null or empty");
        }
        levels.add(description);
        return this;
    }

    /**
     * Returns the rubric levels in order; index equals score.
     *
     * @return an unmodifiable list of level descriptions
     */
    public List<String> levels() {
        return Collections.unmodifiableList(levels);
    }

    @Override
    String type() {
        return "score";
    }

    @Override
    JsonNode criteriaJson() {
        ArrayNode node = Json.MAPPER.createArrayNode();
        levels.forEach(node::add);
        return node;
    }

    @Override
    void validate() {
        if (levels.size() < MIN_LEVELS) {
            throw new InvalidRequestException("Score question \"" + instructions()
                    + "\" requires at least " + MIN_LEVELS + " levels, got " + levels.size());
        }
    }
}
