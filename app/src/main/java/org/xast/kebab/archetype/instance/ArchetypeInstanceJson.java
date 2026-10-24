package org.xast.kebab.archetype.instance;

import java.util.LinkedHashMap;
import java.util.Map;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.archetype.error.UnknownParamException;
import org.xast.kebab.error.KebabException;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record ArchetypeInstanceJson(
    String id,
    Map<String, JsonNode> params
) {
    public static ArchetypeInstanceJson from(ArchetypeInstance instance) {
        Map<String, JsonNode> params = new LinkedHashMap<>();

        for (var entry : instance.getDefinition().getParams().entrySet()) {
            try {
                params.put(
                    entry.getKey(),
                    ParamJson.encode(entry.getValue().type(), instance.get(entry.getKey()))
                );
            } catch (UnknownParamException e) {
                throw new IllegalStateException("Param vanished from its own definition", e);
            }
        }

        return new ArchetypeInstanceJson(instance.archetypeId(), params);
    }

    public NodeArchetype toInstance(ArchetypeCatalog catalog) {        
        switch (catalog.find(id)) {
            case Maybe.None<ArchetypeDefinition> _: {
                LogService.get().warn("Unknown archetype `" + id + "`, keeping it unresolved");
                return unresolved();
            }

            case Maybe.Some<ArchetypeDefinition> found: {
                ArchetypeDefinition def = found.value();
                ArchetypeInstance instance = new ArchetypeInstance(def);

                try {
                    for (var entry : def.getParams().entrySet()) {
                        JsonNode raw = params.get(entry.getKey());

                        if (raw != null) {
                            instance.set(entry.getKey(), ParamJson.decode(entry.getValue().type(), raw));
                        }
                    }
                } catch (KebabException e) {
                    LogService.get().warn("Archetype `" + id + "` is unresolved: " + e.getMessage());
                    return unresolved();
                }

                return instance;
            }
        }
    }

    public UnresolvedArchetype unresolved() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("id", id);
        raw.set("params", JsonNodeFactory.instance.objectNode().setAll(params));
        
        return new UnresolvedArchetype(id, raw);
    }
}
