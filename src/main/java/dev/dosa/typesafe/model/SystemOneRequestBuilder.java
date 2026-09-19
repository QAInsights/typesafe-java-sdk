package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.dosa.typesafe.exception.InvalidRequestException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for {@link SystemOneRequest}.
 *
 * <p>{@link #build()} validates the request client-side — before any network
 * call — and throws {@link InvalidRequestException} when the state is missing,
 * the questions map is empty, a question name is blank, a choice question has
 * an out-of-range option count, or a score question has too few levels.</p>
 */
public final class SystemOneRequestBuilder {

    private JsonNode state;
    private String model;
    private final LinkedHashMap<String, Question> questions = new LinkedHashMap<>();

    SystemOneRequestBuilder() {
    }

    /**
     * Sets the state payload from an arbitrary JSON tree.
     *
     * @param state any {@link JsonNode} — object, array, or scalar
     * @return this builder
     */
    public SystemOneRequestBuilder state(JsonNode state) {
        this.state = state;
        return this;
    }

    /**
     * Sets the state payload to a plain string value.
     *
     * @param state the state string
     * @return this builder
     */
    public SystemOneRequestBuilder state(String state) {
        this.state = state == null ? null : TextNode.valueOf(state);
        return this;
    }

    /**
     * Sets the state payload by serializing an arbitrary POJO or
     * {@link java.util.Map} to JSON.
     *
     * @param state the state object
     * @return this builder
     */
    public SystemOneRequestBuilder state(Object state) {
        this.state = Json.MAPPER.valueToTree(state);
        return this;
    }

    /**
     * Sets the model to use. An empty string (or leaving this unset) lets the
     * client default — or ultimately the server — pick the model.
     *
     * @param model the model name, e.g. {@code "jev-latest"}
     * @return this builder
     */
    public SystemOneRequestBuilder model(String model) {
        this.model = model;
        return this;
    }

    /**
     * Adds a named question. Question names must not be null or blank, and are
     * retained in insertion order.
     *
     * @param name the question name appearing in the response's answers map
     * @param question the question to ask
     * @return this builder
     */
    public SystemOneRequestBuilder question(String name, Question question) {
        questions.put(name, question);
        return this;
    }

    /**
     * Adds all questions from the given map, in the map's iteration order.
     *
     * @param questions map of question name to question
     * @return this builder
     */
    public SystemOneRequestBuilder questions(Map<String, ? extends Question> questions) {
        this.questions.putAll(questions);
        return this;
    }

    /**
     * Validates and builds the request.
     *
     * @return an immutable {@link SystemOneRequest}
     * @throws InvalidRequestException if the request is invalid; thrown
     *         client-side before any network call
     */
    public SystemOneRequest build() {
        if (state == null) {
            throw new InvalidRequestException("SystemOneRequest requires a state");
        }
        if (questions.isEmpty()) {
            throw new InvalidRequestException("SystemOneRequest requires at least one question");
        }
        questions.forEach((name, question) -> {
            if (name == null || name.isBlank()) {
                throw new InvalidRequestException("Question names must not be null or blank");
            }
            if (question == null) {
                throw new InvalidRequestException("Question \"" + name + "\" must not be null");
            }
            question.validate();
        });
        return new SystemOneRequest(state, model, new LinkedHashMap<>(questions));
    }
}
