package dev.dosa.typesafe.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Fallback answer variant for unrecognized or future {@code "type"} values.
 *
 * <p>Deserialization never fails on an unknown answer type - the raw JSON is
 * preserved here so the SDK stays forward-compatible as the API adds new
 * answer kinds.</p>
 *
 * @param type the unrecognized wire {@code "type"} value
 * @param raw the answer's raw JSON object, for inspection
 */
public record UnknownAnswer(String type, JsonNode raw) implements Answer {
}
