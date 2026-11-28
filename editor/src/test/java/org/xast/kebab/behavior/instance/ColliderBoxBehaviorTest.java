package org.xast.kebab.behavior.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.CONTEXT;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.EPS;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.instanceOf;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.nodeAt;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.DrawMode;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.model.GizmoShape;
import org.xast.kebab.model.Meshes;
import org.xast.kebab.scene.model.SceneNode;

class ColliderBoxBehaviorTest {

    private final ColliderBoxBehavior behavior = new ColliderBoxBehavior();

    @Test
    void drawsUnitCubeScaledToSizeAndPlacedByNode() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", new Vec3(1, 2, 3));
        SceneNode node = nodeAt(new Vec3(1, 2, 3), 2f);

        List<Drawable> drawableObjects = behavior.drawableObjects(node, box, CONTEXT);

        assertEquals(1, drawableObjects.size());
        Drawable drawable = drawableObjects.get(0);
        assertSame(Meshes.CUBE_LINES, drawable.mesh());
        assertEquals(DrawMode.OVERLAY, drawable.mode());
        Vec3 corner = drawable.world().transformPoint(new Vec3(0.5f, 0.5f, 0.5f));
        assertTrue(corner.approxEquals(new Vec3(2, 4, 6), EPS), corner.toString());
    }

    @Test
    void boundsAreWorldSpace() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", new Vec3(1, 2, 3));
        SceneNode node = nodeAt(new Vec3(1, 2, 3), 2f);

        Aabb bounds = behavior.bounds(node, box, CONTEXT).unwrap();

        assertEquals(0f, bounds.getMinX(), EPS);
        assertEquals(2f, bounds.getMaxX(), EPS);
        assertEquals(0f, bounds.getMinY(), EPS);
        assertEquals(4f, bounds.getMaxY(), EPS);
        assertEquals(0f, bounds.getMinZ(), EPS);
        assertEquals(6f, bounds.getMaxZ(), EPS);
    }

    @Test
    void selectionShapeIsLocalBox() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", new Vec3(1, 2, 3));

        GizmoShape shape = behavior.gizmoSelectionShape(nodeAt(Vec3.zero(), 5f), box, CONTEXT).unwrap();

        assertEquals(new GizmoShape.GizmoBox(new Vec3(1, 2, 3)), shape);
    }

    @Test
    void zeroSizeIsReportedAndNotDrawn() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", Vec3.zero());
        SceneNode node = nodeAt(Vec3.zero(), 1f);

        assertEquals(1, behavior.validate(node, box, CONTEXT).size());
        assertTrue(behavior.drawableObjects(node, box, CONTEXT).isEmpty());
        assertTrue(behavior.bounds(node, box, CONTEXT).isNone());
        assertTrue(behavior.gizmoSelectionShape(node, box, CONTEXT).isNone());
    }

    @Test
    void anyNonPositiveAxisIsInvalid() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        SceneNode node = nodeAt(Vec3.zero(), 1f);

        box.set("size", new Vec3(1, -1, 1));
        assertEquals(1, behavior.validate(node, box, CONTEXT).size());

        box.set("size", new Vec3(1, Float.NaN, 1));
        assertEquals(1, behavior.validate(node, box, CONTEXT).size());
    }

    @Test
    void positiveSizeIsValid() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", new Vec3(1, 1, 1));

        assertTrue(behavior.validate(nodeAt(Vec3.zero(), 1f), box, CONTEXT).isEmpty());
    }

    @Test
    void offsetMovesTheBoxInTheNodesLocalSpace() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", new Vec3(1, 1, 1));
        box.set("offset", new Vec3(0, 1, 0));
        // Scale 2 doubles the offset too, so the center sits 2 above the node.
        SceneNode node = nodeAt(new Vec3(1, 2, 3), 2f);

        Vec3 center = behavior.drawableObjects(node, box, CONTEXT).get(0).world().transformPoint(Vec3.zero());
        assertTrue(center.approxEquals(new Vec3(1, 4, 3), EPS), center.toString());

        Vec3 physicsCenter = behavior.physicsVolumes(node, box, CONTEXT).get(0).world().transformPoint(Vec3.zero());
        assertTrue(physicsCenter.approxEquals(new Vec3(1, 4, 3), EPS), physicsCenter.toString());

        Aabb bounds = behavior.bounds(node, box, CONTEXT).unwrap();
        assertEquals(3f, bounds.getMinY(), EPS);
        assertEquals(5f, bounds.getMaxY(), EPS);

        assertEquals(
            new GizmoShape.GizmoBox(new Vec3(1, 1, 1), new Vec3(0, 1, 0)),
            behavior.gizmoSelectionShape(node, box, CONTEXT).unwrap()
        );
    }

    @Test
    void offsetDefaultsToZeroAndMustBeFinite() throws Exception {
        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        SceneNode node = nodeAt(Vec3.zero(), 1f);
        assertEquals(Vec3.zero(), box.get("offset"));

        box.set("offset", new Vec3(0, Float.POSITIVE_INFINITY, 0));

        assertEquals(1, behavior.validate(node, box, CONTEXT).size());
        Vec3 center = behavior.drawableObjects(node, box, CONTEXT).get(0).world().transformPoint(Vec3.zero());
        assertTrue(center.approxEquals(Vec3.zero(), EPS), "an unusable offset is ignored, was " + center);
    }
}
