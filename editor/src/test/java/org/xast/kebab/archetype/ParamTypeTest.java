package org.xast.kebab.archetype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.archetype.instance.ArchetypeInstanceJson;
import org.xast.kebab.archetype.instance.NodeArchetype;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Map;

class ParamTypeTest {

    @Test
    void everyDefaultValueIsAnInstanceOfTheTypesJavaClass() {
        for (ParamType type : ParamType.values()) {
            assertTrue(
                type.javaClass().isInstance(type.defaultValue()),
                type + " defaults to " + type.defaultValue().getClass() + " but declares " + type.javaClass()
            );
        }
    }

    @Test
    void aRelPathSurvivesALoadAndSaveRoundTrip() throws Exception {
        ArchetypeCatalog catalog = new ArchetypeCatalog(new org.xast.kebab.archetype.definition.ArchetypeRegistry());
        ArchetypeInstanceJson saved = new ArchetypeInstanceJson(
            BuiltinArchetypes.MODEL_REF,
            Map.of("path", JsonNodeFactory.instance.textNode("models/crate.gltf"))
        );

        NodeArchetype loaded = saved.toInstance(catalog);

        ArchetypeInstance instance = org.junit.jupiter.api.Assertions.assertInstanceOf(ArchetypeInstance.class, loaded);
        assertEquals("models/crate.gltf", instance.get("path"));
        assertEquals("models/crate.gltf", ArchetypeInstanceJson.from(instance).params().get("path").textValue());
    }
}
