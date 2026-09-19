package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dosa.typesafe.exception.InvalidRequestException;

/**
 * Base type for the three System One question kinds: noul (yes/no), choice
 * (pick one of N options), and score (rate against an ordered rubric).
 *
 * <p>This is a sealed hierarchy — questions can only be created through the
 * static factories {@link #noul(String)}, {@link #choice(String)}, and
 * {@link #score(String)}, each of which returns a fluent builder that is itself
 * the question:</p>
 *
 * <pre>{@code
 * Question urgent = Question.noul("Does this convey urgency?")
 *         .whenTrue("Explicitly time-sensitive")
 *         .whenFalse("No time pressure");
 *
 * Question dept = Question.choice("Which team?")
 *         .option("billing", "Payment issues")
 *         .option("technical", "Bugs");
 *
 * Question frustration = Question.score("How frustrated?")
 *         .level("Calm")
 *         .level("Frustrated")
 *         .level("Very angry");
 * }</pre>
 */
public abstract sealed class Question permits NoulQuestion, ChoiceQuestion, ScoreQuestion {

    private final String instructions;

    Question(String instructions) {
        if (instructions == null) {
            throw new InvalidRequestException("Question instructions must not be null");
        }
        this.instructions = instructions;
    }

    /**
     * Creates a noul (yes/no) question.
     *
     * @param instructions the prompt the model evaluates
     * @return a fluent {@link NoulQuestion} builder
     */
    public static NoulQuestion noul(String instructions) {
        return new NoulQuestion(instructions);
    }

    /**
     * Creates a choice (pick one of N options) question.
     *
     * @param instructions the prompt the model evaluates
     * @return a fluent {@link ChoiceQuestion} builder
     */
    public static ChoiceQuestion choice(String instructions) {
        return new ChoiceQuestion(instructions);
    }

    /**
     * Creates a score (ordered rubric) question.
     *
     * @param instructions the prompt the model evaluates
     * @return a fluent {@link ScoreQuestion} builder
     */
    public static ScoreQuestion score(String instructions) {
        return new ScoreQuestion(instructions);
    }

    /**
     * Returns the instructions (prompt) for this question.
     *
     * @return the instructions
     */
    public String instructions() {
        return instructions;
    }

    /**
     * Serializes this question to its wire representation —
     * {@code {"type": ..., "instructions": ..., "criteria": ...}}.
     *
     * @return the question as a JSON object
     */
    public ObjectNode toJson() {
        ObjectNode node = Json.MAPPER.createObjectNode();
        node.put("type", type());
        node.put("instructions", instructions);
        JsonNode criteria = criteriaJson();
        if (criteria != null) {
            node.set("criteria", criteria);
        }
        return node;
    }

    abstract String type();

    abstract JsonNode criteriaJson();

    void validate() {
    }
}
