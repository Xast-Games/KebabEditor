package org.xast.kebab.behavior.instance;

import java.awt.Color;
import java.util.List;

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
import org.xast.kebab.model.Meshes;
import org.xast.kebab.playtest.physics.PhysicsVolume;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;
import org.xast.kebab.utils.Params;

public class ColliderSphereBehavior implements ArchetypeBehavior {

    private static final String RADIUS = "radius";
    private static final Material MATERIAL = Material.solid(new Color(80, 220, 120, 200));

    @Override
    public List<Drawable> drawableObjects(
        SceneNode node, 
        ArchetypeInstance archetype, 
        BehaviorContext context
    ) {
        return validRadius(archetype)
            .map(radius -> List.of(
                new Drawable(
                    Meshes.SPHERE_LINES,
                    matrix(node, archetype, radius),
                    MATERIAL,
                    DrawMode.OVERLAY
                )
            ))
            .unwrapOr(List.of());
    }

    @Override
    public List<PhysicsVolume> physicsVolumes(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return validRadius(archetype)
            .map(radius -> List.of(new PhysicsVolume(
                node.getName(),
                PhysicsVolume.Shape.SPHERE,
                matrix(node, archetype, radius),
                PhysicsVolume.Role.SOLID
            )))
            .unwrapOr(List.of());
    }

    // The unit sphere has radius 0.5, so scaling by the diameter gives exactly this radius,
    // centered on the offset in the node's local space
    private static Mat4 matrix(SceneNode node, ArchetypeInstance archetype, float radius) {
        return node.worldMatrix()
            .mulLocal(Mat4.translation(Params.offset(archetype)))
            .mulLocal(Mat4.scaling(2f * radius));
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(
        SceneNode node, 
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return validRadius(archetype)
            .map(radius -> new GizmoShape.GizmoSphere(radius, Params.offset(archetype)));
    }

    @Override
    public Maybe<Aabb> bounds(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return validRadius(archetype).map(radius -> {
            Mat4 world = node.worldMatrix();
            float scale = world.transformDirection(Vec3.unitX()).length();

            return Aabb.centered(world.transformPoint(Params.offset(archetype)), radius * scale);
        });
    }

    @Override
    public List<BehaviorProblem> validate(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        Maybe<Float> radius = Params.number(archetype, RADIUS);

        if (radius.isNone()) {
            return List.of(new PlainBehaviorProblem("collider_sphere: 'radius' is missing or not a Float"));
        }

        if (validRadius(archetype).isNone()) {
            return List.of(new PlainBehaviorProblem(
                "collider_sphere: radius must be positive, got " + radius.unwrap()
            ));
        }

        return Params.offsetProblem("collider_sphere", archetype)
            .<List<BehaviorProblem>>map(message -> List.of(new PlainBehaviorProblem(message)))
            .unwrapOr(List.of());
    }

    private static Maybe<Float> validRadius(ArchetypeInstance archetype) {
        return Params.number(archetype, RADIUS).
            filter(Params::positive);
    }
}
