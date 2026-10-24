package org.xast.kebab.archetype.instance;

public sealed interface NodeArchetype 
    permits 
        ArchetypeInstance,
        UnresolvedArchetype
{
    String archetypeId();
}
