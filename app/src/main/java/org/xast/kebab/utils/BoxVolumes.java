package org.xast.kebab.utils;

import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
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

public final class BoxVolumes {

    static final String SIZE = "size";

    private BoxVolumes() {}

    public static List<Drawable> drawableObjects(
        SceneNode node, 
        ArchetypeInstance archetype, 
        Material material
    ) {
        return validSize(archetype)
            .map(size -> List.of(new Drawable(
                Meshes.CUBE_LINES,
                matrix(node, size),
                material,
                DrawMode.OVERLAY
            )))
            .unwrapOr(List.of());
    }

    /** The box as a physics volume, placed exactly where it is drawn. */
    public static List<PhysicsVolume> physicsVolumes(
        SceneNode node,
        ArchetypeInstance archetype,
        PhysicsVolume.Role role
    ) {
        return validSize(archetype)
            .map(size -> List.of(new PhysicsVolume(node.getName(), PhysicsVolume.Shape.BOX, matrix(node, size), role)))
            .unwrapOr(List.of());
    }

    public static Maybe<GizmoShape> selectionShape(ArchetypeInstance archetype) {
        return validSize(archetype).map(GizmoShape.GizmoBox::new);
    }

    public static Maybe<Aabb> bounds(
        SceneNode node, 
        ArchetypeInstance archetype
    ) {
        return validSize(archetype)
            .map(size -> Meshes.CUBE_LINES.bounds().transformed(matrix(node, size)));
    }

    public static List<BehaviorProblem> validate(String label, ArchetypeInstance archetype) {
        Maybe<Vec3> size = Params.vec3(archetype, SIZE);

        if (size.isNone()) {
            return List.of(new PlainBehaviorProblem(label + ": 'size' is missing or not a Vec3"));
        }

        if (validSize(archetype).isNone()) {
            return List.of(new PlainBehaviorProblem(
                label + ": size must be positive on every axis, got " + size.unwrap()
            ));
        }

        return List.of();
    }

    private static Maybe<Vec3> validSize(ArchetypeInstance archetype) {
        return Params.vec3(archetype, SIZE).filter(size ->
            Params.positive(size.getX()) && Params.positive(size.getY()) && Params.positive(size.getZ())
        );
    }

    // The unit cube spans [-0.5, 0.5], so scaling by size gives a box of exactly that size
    private static Mat4 matrix(SceneNode node, Vec3 size) {
        return node.worldMatrix().mul(Mat4.scaling(size));
    }
}
