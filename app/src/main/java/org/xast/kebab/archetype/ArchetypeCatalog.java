package org.xast.kebab.archetype;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.utils.Maybe;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor 
public class ArchetypeCatalog {

    @Getter 
    private final ArchetypeRegistry registry;
    
    public Maybe<ArchetypeDefinition> find(String id) {
        return BuiltinArchetypes.find(id)
            .orElse(() -> registry.find(id));
    }

    public boolean isBuiltin(String id) {
        return BuiltinArchetypes.find(id).isSome();
    }

    public Collection<ArchetypeDefinition> all() {
        Set<ArchetypeDefinition> all = new LinkedHashSet<>(BuiltinArchetypes.all());
        all.addAll(registry.getArchetypes());
        return all;
    }
}
