package dev.dosa.typesafe.exception;

/**
 * Thrown when a request fails client-side validation.
 *
 * <p>This exception is raised before any network call is made — for example when
 * a {@code SystemOneRequest} is built with no questions, a blank question name, a
 * choice question with fewer than 2 or more than 255 options, or a score question
 * with fewer than 2 levels.</p>
 */
public class InvalidRequestException extends TypeSafeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message a human-readable description of the validation failure
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
