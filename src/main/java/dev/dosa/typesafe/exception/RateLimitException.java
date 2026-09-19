package dev.dosa.typesafe.exception;

import java.util.Optional;

/**
 * Thrown when the API returns HTTP 429 (rate limited) and all retry attempts
 * have been exhausted.
 */
public class RateLimitException extends TypeSafeException {

    private final String requestId;
    private final int attempts;

    /**
     * Creates a new exception.
     *
     * @param requestId the {@code x-typesafe-request-id} header value from the final
     *                  attempt, or {@code null}
     * @param attempts the number of HTTP attempts that were made
     */
    public RateLimitException(String requestId, int attempts) {
        super("Rate limited (HTTP 429) after " + attempts + " attempt(s)"
                + (requestId != null ? " [request id: " + requestId + "]" : ""));
        this.requestId = requestId;
        this.attempts = attempts;
    }

    /**
     * Returns the {@code x-typesafe-request-id} header value from the final attempt,
     * if the server sent one.
     *
     * @return the request id
     */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    /**
     * Returns the number of HTTP attempts that were made before giving up.
     *
     * @return the attempt count
     */
    public int attempts() {
        return attempts;
    }
}
