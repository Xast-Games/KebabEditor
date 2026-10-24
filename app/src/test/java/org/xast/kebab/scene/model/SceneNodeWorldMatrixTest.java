package org.xast.kebab.scene.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.xast.kebab.math.Quat;
import org.xast.kebab.math.Transform;
import org.xast.kebab.math.Vec3;

class SceneNodeWorldMatrixTest {

    private static SceneNode node(Vec3 translation, float scale) {
        SceneNode node = new SceneNode("n");
        node.setTransform(new Transform(translation, Quat.identity(), scale));

        return node;
    }

    @Test
    void rootWorldIsItsLocalTransform() {
        Vec3 point = node(new Vec3(1, 2, 3), 2f).worldMatrix().transformPoint(new Vec3(1, 1, 1));

        assertTrue(point.approxEquals(new Vec3(3, 4, 5), 1e-4f), point.toString());
    }

    @Test
    void childWorldComposesWithParents() throws Exception {
        Scene scene = new Scene();
        SceneNode parent = node(new Vec3(10, 0, 0), 2f);
        SceneNode child = node(new Vec3(1, 0, 0), 1f);
        scene.add(parent);
        scene.add(child, parent);

        Vec3 origin = child.worldMatrix().transformPoint(Vec3.zero());

        // The parent's scale of 2 applies to the child's local offset of 1
        assertTrue(origin.approxEquals(new Vec3(12, 0, 0), 1e-4f), origin.toString());
    }
}
