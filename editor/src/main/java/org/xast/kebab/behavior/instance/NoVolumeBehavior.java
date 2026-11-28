package org.xast.kebab.behavior.instance;

import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.behavior.BehaviorProblem;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.model.GizmoShape;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

/**
 * A flag on its object: the object's greybox is not drawn (or only as a wireframe,
 * see View > Render No Volume) and not solid in a playtest. The greybox renderer,
 * picker and playtest level read the flag; the archetype itself adds nothing.
 */
public class NoVolumeBehavior implements ArchetypeBehavior {

    @Override
    public List<Drawable> drawableObjects(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return List.of();
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return Maybe.none();
    }

    @Override
    public Maybe<Aabb> bounds(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return Maybe.none();
    }

    @Override
    public List<BehaviorProblem> validate(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return List.of();
    }
}
