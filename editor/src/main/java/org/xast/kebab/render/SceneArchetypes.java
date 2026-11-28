package org.xast.kebab.render;

import java.util.stream.Stream;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.behavior.instance.BuiltinBehaviors;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.model.SceneNode;

/**
 * Every archetype instance in a scene that has a behavior, i.e. that knows how
 * to draw itself. Shared by drawing and picking so they always agree.
 */
final class SceneArchetypes {

    record Entry(SceneNode node, ArchetypeInstance archetype, ArchetypeBehavior behavior) {}

    private SceneArchetypes() {}

    static Stream<Entry> of(Scene scene) {
        return scene.traverse().flatMap(node -> node.getArchetypes().stream()
            .filter(ArchetypeInstance.class::isInstance)
            .map(ArchetypeInstance.class::cast)
            .flatMap(archetype -> BuiltinBehaviors.behaviorOf(archetype.archetypeId())
                .mapOr(Stream.<Entry>empty(), behavior -> Stream.of(new Entry(node, archetype, behavior)))));
    }
}
