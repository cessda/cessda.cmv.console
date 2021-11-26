package eu.cessda.cmv.console;

import java.io.IOException;
import java.util.Objects;

/**
 * Thrown when an IO error prevents the CMV profile from being loaded.
 */
public class ProfileLoadFailedException extends RuntimeException {
    /**
     * Constructs a new {@link ProfileLoadFailedException} with the specified cause.
     *
     * @param e the cause, must not be null.
     */
    public ProfileLoadFailedException(IOException e) {
        super(Objects.requireNonNull(e));
    }
}
