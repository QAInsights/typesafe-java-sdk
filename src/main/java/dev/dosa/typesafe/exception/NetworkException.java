package dev.dosa.typesafe.exception;

/**
 * Thrown when a request fails at the transport level — DNS errors, connection
 * refusals, timeouts, and other {@link java.io.IOException}s raised by the
 * underlying HTTP client.
 */
public class NetworkException extends TypeSafeException {

    /**
     * Creates a new exception wrapping the given I/O failure.
     *
     * @param message a human-readable description of the failure
     * @param cause the underlying {@link java.io.IOException}
     */
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
