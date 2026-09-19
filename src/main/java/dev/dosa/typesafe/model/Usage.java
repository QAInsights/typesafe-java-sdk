package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Token accounting reported by the API for a System One call.
 *
 * @param inputTokens tokens consumed by the request
 * @param outputTokens tokens produced by the response
 */
public record Usage(int inputTokens, int outputTokens) {

    static Usage fromJson(JsonNode node) {
        return new Usage(node.path("input_tokens").asInt(), node.path("output_tokens").asInt());
    }
}
