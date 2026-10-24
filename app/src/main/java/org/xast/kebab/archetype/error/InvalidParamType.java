package org.xast.kebab.archetype.error;

import org.xast.kebab.error.KebabException;

public class InvalidParamType extends KebabException {

    public InvalidParamType(Class<?> found, Class<?> expected) {
        super(
            "Invalid param type: `"
                + (found == null ? "NULL" : found.getSimpleName())
                + "`, expected: `" + expected.getSimpleName() + "`"
        );
    }
}
