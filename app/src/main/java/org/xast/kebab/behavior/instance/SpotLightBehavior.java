package org.xast.kebab.behavior.instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.behavior.BehaviorProblem;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Mat4;
import org.xast.kebab.model.GizmoShape;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

public class SpotLightBehavior implements ArchetypeBehavior {

    private static final String LABEL = "spot_light";

    @Override
    public List<Drawable> drawableObjects(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return gizmos(node.worldMatrix(), archetype);
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return Maybe.some(new GizmoShape.GizmoSphere(LightGizmos.MARKER_DIAMETER / 2f));
    }

    @Override
    public Maybe<Aabb> bounds(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return LightGizmos.boundsOf(gizmos(node.worldMatrix(), archetype));
    }

    @Override
    public List<BehaviorProblem> validate(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        List<BehaviorProblem> problems = new ArrayList<>(LightGizmos.validateIntensity(LABEL, archetype));
        problems.addAll(LightGizmos.validateRange(LABEL, archetype));
        problems.addAll(LightGizmos.validateAngles(LABEL, archetype));

        return problems;
    }

    private static List<Drawable> gizmos(Mat4 world, ArchetypeInstance archetype) {
        Color color = LightGizmos.colorOf(archetype);
        List<Drawable> gizmos = new ArrayList<>();

        gizmos.add(LightGizmos.marker(world, color));

        Maybe<Float> range = LightGizmos.validRange(archetype);
        Maybe<Float> outer = LightGizmos.validOuterAngle(archetype);

        if (range.isSome() && outer.isSome()) {
            gizmos.add(LightGizmos.cone(world, color, range.unwrap(), outer.unwrap(), false));

            LightGizmos.validInnerAngle(archetype)
                .filter(inner -> inner > 0f)
                .ifSome(inner -> gizmos.add(LightGizmos.cone(world, color, range.unwrap(), inner, true)));
        }

        return gizmos;
    }
}
