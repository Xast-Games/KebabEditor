package org.xast.kebab.scene.error;

import org.xast.kebab.error.KebabException;

public class SceneException extends KebabException {

    public SceneException(String message) {
        super(message);
    }

    public SceneException(String message, Throwable cause) {
        super(message, cause);
    }
}
