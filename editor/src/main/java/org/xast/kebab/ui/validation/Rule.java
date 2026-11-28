package org.xast.kebab.ui.validation;

import org.xast.kebab.utils.Maybe;

@FunctionalInterface
public interface Rule {

    /** 
     * Returns an error message, or None if the text is valid. 
     */
    Maybe<String> check(String text);
}
