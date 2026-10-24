package org.xast.kebab.behavior.instance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;

class BuiltinBehaviorsTest {

    @Test
    void unknownIdsHaveNoBehavior() {
        assertTrue(BuiltinBehaviors.behaviorOf("user_made").isNone());
    }

    @Test
    void everyBuiltinArchetypeHasABehavior() {
        for (var definition : BuiltinArchetypes.all()) {
            assertTrue(BuiltinBehaviors.behaviorOf(definition.getId()).isSome(), definition.getId());
        }
    }
}
