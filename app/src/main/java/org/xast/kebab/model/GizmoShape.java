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
    /** A box of {@code size} around {@code center}, both in the node's local space. */
    record GizmoBox(Vec3 size, Vec3 center) implements GizmoShape {

        public GizmoBox(Vec3 size) {
            this(size, Vec3.zero());
        }
    }

    /** A sphere of {@code radius} around {@code center}, both in the node's local space. */
    record GizmoSphere(float radius, Vec3 center) implements GizmoShape {

        public GizmoSphere(float radius) {
            this(radius, Vec3.zero());
        }
    }

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
