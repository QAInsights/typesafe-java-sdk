package dev.dosa.typesafe.exception;

/**
 * Base unchecked exception for all errors raised by the TypeSafe SDK.
 *
 * <p>Catch this type to handle any SDK failure uniformly, or catch one of the
 * more specific subclasses to react to a particular failure mode.</p>
 */
public class TypeSafeException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message a human-readable description of the failure
     */
    public TypeSafeException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message a human-readable description of the failure
     * @param cause the underlying cause
     */
    public TypeSafeException(String message, Throwable cause) {
        super(message, cause);
    }
}
