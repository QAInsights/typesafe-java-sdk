package dev.dosa.typesafe.exception;

import java.util.Optional;

/**
 * Thrown when the API returns a non-retryable error status (any 4xx other than
 * 401/403/429, or a 5xx that survived all retry attempts), or when the response
 * body cannot be parsed.
 */
public class ApiException extends TypeSafeException {

    private static final int MAX_BODY_SNIPPET = 300;

    private final int status;
    private final String body;
    private final String requestId;

    /**
     * Creates a new exception.
     *
     * @param status the HTTP status returned by the server
     * @param body the raw response body, or {@code null}
     * @param requestId the {@code x-typesafe-request-id} header value, or {@code null}
     */
    public ApiException(int status, String body, String requestId) {
        this(status, body, requestId, null);
    }

    /**
     * Creates a new exception with a custom leading message.
     *
     * @param status the HTTP status returned by the server
     * @param body the raw response body, or {@code null}
     * @param requestId the {@code x-typesafe-request-id} header value, or {@code null}
     * @param message an optional custom message; when {@code null} a default
     *                message including status and a body snippet is used
     */
    public ApiException(int status, String body, String requestId, String message) {
        super(buildMessage(status, body, requestId, message));
        this.status = status;
        this.body = body;
        this.requestId = requestId;
    }

    /**
     * Returns the HTTP status code returned by the server.
     *
     * @return the status code
     */
    public int status() {
        return status;
    }

    /**
     * Returns the raw response body, if the server sent one.
     *
     * @return the response body
     */
    public Optional<String> body() {
        return Optional.ofNullable(body);
    }

    /**
     * Returns the {@code x-typesafe-request-id} header value, if the server sent one.
     *
     * @return the request id
     */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    private static String buildMessage(int status, String body, String requestId, String message) {
        StringBuilder sb = new StringBuilder();
        if (message != null) {
            sb.append(message).append(' ');
        }
        sb.append("(HTTP ").append(status).append(')');
        if (requestId != null) {
            sb.append(" [request id: ").append(requestId).append(']');
        }
        if (body != null && !body.isEmpty()) {
            String snippet = body.length() <= MAX_BODY_SNIPPET
                    ? body
                    : body.substring(0, MAX_BODY_SNIPPET) + "...";
            sb.append(" body: ").append(snippet);
        }
        return sb.toString();
    }
}
