package org.xast.kebab.model;

import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.model.GizmoShape.*;

public sealed interface GizmoShape 
    permits
        GizmoBox,
        GizmoSphere,
        GizmoAabb,
        GizmoLines
{
    record GizmoBox(Vec3 size) implements GizmoShape {}

    record GizmoSphere(float radius) implements GizmoShape {}

    record GizmoAabb(Aabb box) implements GizmoShape {

        public GizmoAabb {
            box = box.copy();
        }

        @Override
        public Aabb box() {
            return box.copy();
        }
    }

    record GizmoLines(MeshData lines) implements GizmoShape {}
}
