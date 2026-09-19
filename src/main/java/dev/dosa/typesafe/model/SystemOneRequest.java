package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A request to the System One API ({@code POST /v1/systemone}).
 *
 * <p>Instances are immutable and created via {@link #builder()} - the builder
 * performs all client-side validation, so a successfully built request is
 * always wire-valid:</p>
 *
 * <pre>{@code
 * SystemOneRequest request = SystemOneRequest.builder()
 *         .state(Map.of("ticket", "Refund please!"))
 *         .model("jev-latest")
 *         .question("urgent", Question.noul("Does this convey urgency?"))
 *         .question("dept", Question.choice("Which team?")
 *                 .option("billing", "Payment issues")
 *                 .option("technical", "Bugs"))
 *         .build();
 * }</pre>
 */
public final class SystemOneRequest {

    private final JsonNode state;
    private final String model;
    private final LinkedHashMap<String, Question> questions;

    SystemOneRequest(JsonNode state, String model, LinkedHashMap<String, Question> questions) {
        this.state = state;
        this.model = model;
        this.questions = questions;
    }

    /**
     * Creates a new request builder.
     *
     * @return a {@link SystemOneRequestBuilder}
     */
    public static SystemOneRequestBuilder builder() {
        return new SystemOneRequestBuilder();
    }

    /**
     * Returns the state payload sent to the model. May be any JSON value -
     * string, object, or array.
     *
     * @return the state as a JSON tree
     */
    public JsonNode state() {
        return state;
    }

    /**
     * Returns the requested model, or {@code null} when unset (in which case the
     * client's default model - or ultimately the server's - is used).
     *
     * @return the model name, or {@code null}
     */
    public String model() {
        return model;
    }

    /**
     * Returns the questions, keyed by name, in insertion order.
     *
     * @return an unmodifiable map of question name to question
     */
    public Map<String, Question> questions() {
        return Collections.unmodifiableMap(questions);
    }

    /**
     * Serializes this request to its wire representation:
     * {@code {"state": ..., "model": ..., "questions": {...}}}.
     *
     * <p>Note: when {@link #model()} is {@code null} the {@code model} field is
     * emitted as an empty string; {@code TypeSafeClient} overlays its configured
     * default model at send time.</p>
     *
     * @return the request body as a JSON object
     */
    public ObjectNode toJson() {
        ObjectNode root = Json.MAPPER.createObjectNode();
        root.set("state", state);
        root.put("model", model == null ? "" : model);
        ObjectNode qs = root.putObject("questions");
        questions.forEach((name, question) -> qs.set(name, question.toJson()));
        return root;
    }
}
