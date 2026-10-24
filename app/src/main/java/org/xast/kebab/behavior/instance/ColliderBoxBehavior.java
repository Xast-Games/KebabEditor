package org.xast.kebab.behavior.instance;

import java.awt.Color;
import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.behavior.BehaviorProblem;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.model.GizmoShape;
import org.xast.kebab.model.Material;
import org.xast.kebab.playtest.physics.PhysicsVolume;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.BoxVolumes;
import org.xast.kebab.utils.Maybe;

public class ColliderBoxBehavior implements ArchetypeBehavior {

    private static final Material MATERIAL = Material.solid(new Color(80, 220, 120, 200));

    @Override
    public List<Drawable> drawableObjects(
        SceneNode node, 
        ArchetypeInstance archetype, 
        BehaviorContext context
    ) {
        return BoxVolumes.drawableObjects(node, archetype, MATERIAL);
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(
        SceneNode node, 
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return BoxVolumes.selectionShape(archetype);
    }

    @Override
    public Maybe<Aabb> bounds(
        SceneNode node, 
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return BoxVolumes.bounds(node, archetype);
    }

    @Override
    public List<BehaviorProblem> validate(
        SceneNode node, 
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return BoxVolumes.validate("collider_box", archetype);
    }

    @Override
    public List<PhysicsVolume> physicsVolumes(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return BoxVolumes.physicsVolumes(node, archetype, PhysicsVolume.Role.SOLID);
    }
}
