package dev.dosa.typesafe.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.dosa.typesafe.exception.ApiException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * A model available on the TypeSafe API, as returned by
 * {@code GET /v1/models}.
 *
 * @param name the model identifier to pass in requests (e.g.
 *        {@code "jev-latest"})
 * @param description a human-readable description of the model, or {@code null}
 * @param releaseDate when the model was released, or {@code null} if absent or
 *        unparseable
 */
public record ModelInfo(String name, String description, OffsetDateTime releaseDate) {

    /**
     * Parses a {@code GET /v1/models} response body
     * ({@code {"models": [{...}, ...]}}).
     *
     * <p>Intended for the SDK's transport layer and tests; most callers use
     * {@code TypeSafeClient.models()}.</p>
     *
     * @param body the raw JSON response body
     * @return the parsed models, in server order
     * @throws ApiException if the body is not valid JSON or has no {@code models} array
     */
    public static List<ModelInfo> parseList(String body) {
        JsonNode root;
        try {
            root = Json.MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ApiException(200, body, null, "Response body is not valid JSON");
        }
        JsonNode models = root != null ? root.path("models") : null;
        if (models == null || !models.isArray()) {
            throw new ApiException(200, body, null, "Response body has no \"models\" array");
        }
        List<ModelInfo> result = new ArrayList<>();
        for (JsonNode node : models) {
            result.add(new ModelInfo(
                    node.path("name").asText(null),
                    node.path("description").isTextual() ? node.get("description").asText() : null,
                    parseDate(node.path("release_date"))));
        }
        return result;
    }

    private static OffsetDateTime parseDate(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
