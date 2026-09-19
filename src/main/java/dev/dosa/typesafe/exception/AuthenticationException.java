package dev.dosa.typesafe.exception;

import java.util.Optional;

/**
 * Thrown when the API rejects the request's credentials (HTTP 401 or 403).
 *
 * <p>Authentication failures are never retried.</p>
 */
public class AuthenticationException extends TypeSafeException {

    private final int status;
    private final String requestId;

    /**
     * Creates a new exception.
     *
     * @param status the HTTP status returned by the server (401 or 403)
     * @param requestId the {@code x-typesafe-request-id} header value, or {@code null}
     */
    public AuthenticationException(int status, String requestId) {
        super("Authentication failed (HTTP " + status + ")"
                + (requestId != null ? " [request id: " + requestId + "]" : ""));
        this.status = status;
        this.requestId = requestId;
    }

    /**
     * Returns the HTTP status code returned by the server.
     *
     * @return 401 or 403
     */
    public int status() {
        return status;
    }

    /**
     * Returns the {@code x-typesafe-request-id} header value, if the server sent one.
     *
     * @return the request id
     */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }
}
