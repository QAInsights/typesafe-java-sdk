package dev.dosa.typesafe.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.dosa.typesafe.exception.ApiException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A parsed response from the System One API.
 *
 * <p>Answers are keyed by the question names from the request. Use the typed
 * accessors — {@link #noul(String)}, {@link #choice(String)},
 * {@link #score(String)} — to look up a specific answer, or the
 * {@link #nouls()}, {@link #choices()}, {@link #scores()} streams to iterate
 * all answers of one kind. Unrecognized answer types are preserved as
 * {@link UnknownAnswer} and reachable via {@link #answers()}.</p>
 *
 * <p>Transport metadata — the {@code x-typesafe-request-id} header, final HTTP
 * status, and number of attempts made — is exposed separately from the answer
 * data via {@link #requestId()}, {@link #httpStatus()}, and
 * {@link #attempts()}.</p>
 */
public final class SystemOneResponse {

    private final String model;
    private final LinkedHashMap<String, Answer> answers;
    private final Usage usage;
    private final String requestId;
    private final int httpStatus;
    private final int attempts;

    private SystemOneResponse(String model, LinkedHashMap<String, Answer> answers, Usage usage,
            String requestId, int httpStatus, int attempts) {
        this.model = model;
        this.answers = answers;
        this.usage = usage;
        this.requestId = requestId;
        this.httpStatus = httpStatus;
        this.attempts = attempts;
    }

    /**
     * Parses a raw System One response body, attaching transport metadata.
     *
     * <p>Intended for the SDK's transport layer and tests; most callers receive
     * responses from {@code TypeSafeClient} directly.</p>
     *
     * @param body the raw JSON response body
     * @param httpStatus the final HTTP status code
     * @param requestId the {@code x-typesafe-request-id} header value, or {@code null}
     * @param attempts the number of HTTP attempts made
     * @return the parsed response
     * @throws ApiException if the body is not a valid System One JSON object
     */
    public static SystemOneResponse parse(String body, int httpStatus, String requestId, int attempts) {
        JsonNode root;
        try {
            root = Json.MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ApiException(httpStatus, body, requestId, "Response body is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new ApiException(httpStatus, body, requestId, "Response body is not a JSON object");
        }

        String model = root.path("model").isTextual() ? root.get("model").asText() : null;

        LinkedHashMap<String, Answer> answers = new LinkedHashMap<>();
        JsonNode answersNode = root.path("answers");
        if (answersNode.isObject()) {
            answersNode.fields().forEachRemaining(e -> answers.put(e.getKey(), parseAnswer(e.getValue())));
        }

        JsonNode usageNode = root.path("usage");
        Usage usage = usageNode.isObject() ? Usage.fromJson(usageNode) : null;

        return new SystemOneResponse(model, answers, usage, requestId, httpStatus, attempts);
    }

    private static Answer parseAnswer(JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "noul" -> new NoulAnswer(node.path("noul").asDouble());
            case "choice" -> {
                String choice = node.path("choice").isTextual() ? node.get("choice").asText() : null;
                yield new ChoiceAnswer(choice, stringDoubleMap(node.path("probabilities")),
                        node.path("confidence").asDouble());
            }
            case "score" -> {
                Map<String, String> legend = new LinkedHashMap<>();
                JsonNode legendNode = node.path("legend");
                if (legendNode.isObject()) {
                    legendNode.fields().forEachRemaining(e -> legend.put(e.getKey(), e.getValue().asText()));
                }
                JsonNode probsNode = node.path("probabilities");
                Optional<Map<String, Double>> probs = probsNode.isObject()
                        ? Optional.of(stringDoubleMap(probsNode))
                        : Optional.empty();
                yield new ScoreAnswer(node.path("score").asDouble(), legend, probs,
                        node.path("confidence").asDouble());
            }
            default -> new UnknownAnswer(type, node);
        };
    }

    private static Map<String, Double> stringDoubleMap(JsonNode node) {
        LinkedHashMap<String, Double> map = new LinkedHashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asDouble()));
        }
        return map;
    }

    /**
     * Returns the model that produced these answers.
     *
     * @return the model name, or {@code null} if absent from the response
     */
    public String model() {
        return model;
    }

    /**
     * Returns all answers keyed by question name, in response order.
     *
     * @return an unmodifiable map of question name to {@link Answer}
     */
    public Map<String, Answer> answers() {
        return Collections.unmodifiableMap(answers);
    }

    /**
     * Looks up a single answer by question name, regardless of type.
     *
     * @param name the question name
     * @return the answer, or empty if absent
     */
    public Optional<Answer> answer(String name) {
        return Optional.ofNullable(answers.get(name));
    }

    /**
     * Looks up a noul answer by question name.
     *
     * @param name the question name
     * @return the noul probability (0–1), or empty if absent or not a noul answer
     */
    public Optional<Double> noul(String name) {
        return answers.get(name) instanceof NoulAnswer n ? Optional.of(n.value()) : Optional.empty();
    }

    /**
     * Looks up a choice answer by question name.
     *
     * @param name the question name
     * @return the {@link ChoiceAnswer}, or empty if absent or not a choice answer
     */
    public Optional<ChoiceAnswer> choice(String name) {
        return answers.get(name) instanceof ChoiceAnswer c ? Optional.of(c) : Optional.empty();
    }

    /**
     * Looks up a score answer by question name.
     *
     * @param name the question name
     * @return the {@link ScoreAnswer}, or empty if absent or not a score answer
     */
    public Optional<ScoreAnswer> score(String name) {
        return answers.get(name) instanceof ScoreAnswer s ? Optional.of(s) : Optional.empty();
    }

    /**
     * Streams all noul answers with their question names.
     *
     * @return a stream of name → {@link NoulAnswer} entries
     */
    public Stream<Map.Entry<String, NoulAnswer>> nouls() {
        return typed(NoulAnswer.class);
    }

    /**
     * Streams all choice answers with their question names.
     *
     * @return a stream of name → {@link ChoiceAnswer} entries
     */
    public Stream<Map.Entry<String, ChoiceAnswer>> choices() {
        return typed(ChoiceAnswer.class);
    }

    /**
     * Streams all score answers with their question names.
     *
     * @return a stream of name → {@link ScoreAnswer} entries
     */
    public Stream<Map.Entry<String, ScoreAnswer>> scores() {
        return typed(ScoreAnswer.class);
    }

    private <A extends Answer> Stream<Map.Entry<String, A>> typed(Class<A> kind) {
        return answers.entrySet().stream()
                .filter(e -> kind.isInstance(e.getValue()))
                .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), kind.cast(e.getValue())));
    }

    /**
     * Returns token usage reported by the API, when present.
     *
     * @return the {@link Usage}, or empty if the response omitted it
     */
    public Optional<Usage> usage() {
        return Optional.ofNullable(usage);
    }

    /**
     * Returns the {@code x-typesafe-request-id} response header, when sent.
     *
     * @return the request id
     */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    /**
     * Returns the final HTTP status code of the request that produced this
     * response (after any retries).
     *
     * @return the HTTP status code
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Returns the number of HTTP attempts made to produce this response —
     * {@code 1} when no retry was needed.
     *
     * @return the attempt count
     */
    public int attempts() {
        return attempts;
    }
}
