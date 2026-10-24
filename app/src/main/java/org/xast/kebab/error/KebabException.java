package org.xast.kebab.error;

public class KebabException extends Exception {

    public KebabException(String message) {
        super(message);
    }

    public KebabException(String message, Throwable cause) {
        super(message, cause);
    }
}
