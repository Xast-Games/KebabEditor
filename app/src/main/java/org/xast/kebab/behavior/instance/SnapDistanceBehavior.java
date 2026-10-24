package org.xast.kebab.behavior.instance;

import java.awt.Color;
import java.util.List;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.behavior.BehaviorProblem;
import org.xast.kebab.behavior.DrawMode;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.behavior.PlainBehaviorProblem;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Mat4;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.model.GizmoShape;
import org.xast.kebab.model.Material;
import org.xast.kebab.model.MeshData;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;
import org.xast.kebab.utils.Params;

public class SnapDistanceBehavior implements ArchetypeBehavior {

    static final String DISTANCE = "distance";

    private static final Material MATERIAL = Material.solid(new Color(250, 220, 70));
    private static final float TICK = 0.3f;

    // A unit ruler along +X with a cross-shaped tick at each end; scaled to the distance.
    private static final MeshData RULER = MeshData.lines(
        new float[] {
            0, 0, 0,     1, 0, 0,
            0, -0.5f, 0, 0, 0.5f, 0,   0, 0, -0.5f, 0, 0, 0.5f,
            1, -0.5f, 0, 1, 0.5f, 0,   1, 0, -0.5f, 1, 0, 0.5f,
        },
        new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }
    );

    public static Maybe<Float> distance(ArchetypeInstance archetype) {
        return Params.number(archetype, DISTANCE).filter(Params::positive);
    }

    @Override
    public List<Drawable> drawableObjects(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return distance(archetype)
            .map(d -> List.of(new Drawable(RULER, matrix(node, d), MATERIAL, DrawMode.OVERLAY)))
            .unwrapOr(List.of());
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return distance(archetype).map(d -> new GizmoShape.GizmoAabb(localBox(d)));
    }

    @Override
    public Maybe<Aabb> bounds(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return distance(archetype).map(d -> localBox(d).transformed(node.worldMatrix()));
    }

    @Override
    public List<BehaviorProblem> validate(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        if (distance(archetype).isNone()) {
            return List.of(new PlainBehaviorProblem(
                BuiltinArchetypes.SNAP_DISTANCE + ": 'distance' must be a positive number"
            ));
        }
        return List.of();
    }

    private static Mat4 matrix(SceneNode node, float distance) {
        return node.worldMatrix().mul(Mat4.scaling(new Vec3(distance, TICK, TICK)));
    }

    private static Aabb localBox(float distance) {
        return new Aabb(0, distance, -TICK / 2, TICK / 2, -TICK / 2, TICK / 2);
    }
}
