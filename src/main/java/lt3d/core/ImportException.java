package lt3d.core;

/** Thrown when a model cannot be read or converted. The message is meant to be shown to the player. */
public class ImportException extends Exception {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
