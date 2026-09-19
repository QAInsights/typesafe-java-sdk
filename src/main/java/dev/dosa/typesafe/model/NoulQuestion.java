package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A noul (yes/no) question. The model answers with a probability between 0 and 1
 * that the answer is "true".
 *
 * <p>Created via {@link Question#noul(String)}. Criteria for each outcome are
 * optional — when neither is set, the {@code criteria} field is omitted from the
 * wire payload entirely.</p>
 */
public final class NoulQuestion extends Question {

    private String whenTrue;
    private String whenFalse;

    NoulQuestion(String instructions) {
        super(instructions);
    }

    /**
     * Describes what the "true" outcome means.
     *
     * @param criteria description of the true case
     * @return this question, for chaining
     */
    public NoulQuestion whenTrue(String criteria) {
        this.whenTrue = criteria;
        return this;
    }

    /**
     * Describes what the "false" outcome means.
     *
     * @param criteria description of the false case
     * @return this question, for chaining
     */
    public NoulQuestion whenFalse(String criteria) {
        this.whenFalse = criteria;
        return this;
    }

    /**
     * Returns the criteria describing the "true" outcome, if set.
     *
     * @return the true-case criteria, or {@code null}
     */
    public String trueCriteria() {
        return whenTrue;
    }

    /**
     * Returns the criteria describing the "false" outcome, if set.
     *
     * @return the false-case criteria, or {@code null}
     */
    public String falseCriteria() {
        return whenFalse;
    }

    @Override
    String type() {
        return "noul";
    }

    @Override
    JsonNode criteriaJson() {
        if (whenTrue == null && whenFalse == null) {
            return null;
        }
        ObjectNode node = Json.MAPPER.createObjectNode();
        if (whenTrue != null) {
            node.put("true", whenTrue);
        }
        if (whenFalse != null) {
            node.put("false", whenFalse);
        }
        return node;
    }
}
