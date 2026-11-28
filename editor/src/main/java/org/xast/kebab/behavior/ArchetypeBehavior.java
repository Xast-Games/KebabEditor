package org.xast.kebab.behavior;

import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.model.GizmoShape;
import org.xast.kebab.playtest.physics.PhysicsVolume;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

public interface ArchetypeBehavior {
    
    List<Drawable> drawableObjects(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    );

    Maybe<GizmoShape> gizmoSelectionShape(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    );

    Maybe<Aabb> bounds(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    );

    List<BehaviorProblem> validate(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    );

    /**
     * What the archetype adds to playtest physics, built from the same
     * parameters and matrix as its drawing. Most archetypes add nothing.
     */
    default List<PhysicsVolume> physicsVolumes(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return List.of();
    }
}
