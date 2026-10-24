package org.xast.kebab.archetype.error;

import org.xast.kebab.archetype.ParamType;
import org.xast.kebab.error.KebabException;

import com.fasterxml.jackson.databind.JsonNode;

public class ParamDecodeException extends KebabException {

    public ParamDecodeException(ParamType type, JsonNode node) {
        super("Cannot decode `" + node + "` as " + type.displayName());
    }
}
