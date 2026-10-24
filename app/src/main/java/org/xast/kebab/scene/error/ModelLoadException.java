package org.xast.kebab.scene.error;

import org.xast.kebab.error.KebabException;

public class ModelLoadException extends KebabException {

    public ModelLoadException(String message) {
        super(message);
    }

    public ModelLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
