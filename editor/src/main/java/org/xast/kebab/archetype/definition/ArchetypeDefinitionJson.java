package org.xast.kebab.archetype.definition;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.xast.kebab.archetype.ArchetypeIcon;
import org.xast.kebab.archetype.ParamSpec;
import org.xast.kebab.utils.Maybe;

public record ArchetypeDefinitionJson(
    String id,
    String displayName,
    @Nullable String icon,
    boolean single,
    Map<String, ParamSpec> params
) {
    public static ArchetypeDefinitionJson from(ArchetypeDefinition def) {
        // only a handle has a path that can be stored
        String icon = null;
        if (def.getIcon() instanceof Maybe.Some<ArchetypeIcon>(var value)
            && value instanceof ArchetypeIcon.Handle handle
        ) {
            icon = handle.path();
        }

        return new ArchetypeDefinitionJson(
            def.getId(), 
            def.getDisplayName(), 
            icon, 
            def.isSingle(),
            def.getParams()
        );
    }

    public ArchetypeDefinition toDefinition() {
        return new ArchetypeDefinition(
            id,
            displayName,
            icon == null
                ? Maybe.none()
                : Maybe.some(new ArchetypeIcon.Handle(icon)),
            single,
            params
        );
    }
}
