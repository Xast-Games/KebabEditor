package org.xast.kebab.render;

import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.archetype.instance.NodeArchetype;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.behavior.instance.BuiltinBehaviors;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Mat4;
import org.xast.kebab.model.Meshes;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

/** World-space boxes around nodes, e.g. to point the camera at them. */
public final class NodeBounds {

    // A node with nothing drawn, like an empty, still gets a box this big around its origin
    private static final float POINT_HALF_EXTENT = 0.5f;

    private NodeBounds() {}

    /** Everything the nodes and their children draw, or none for an empty list. */
    public static Maybe<Aabb> of(List<SceneNode> nodes, Maybe<BehaviorContext> context) {
        Aabb union = null;
        for (SceneNode node : nodes) {
            Aabb box = withChildren(node, context);
            union = union == null ? box : union.union(box);
        }
        return Maybe.ofNullable(union);
    }

    private static Aabb withChildren(SceneNode node, Maybe<BehaviorContext> context) {
        Aabb union = own(node, context).unwrapOr(null);
        for (SceneNode child : node.getChildren()) {
            Aabb box = withChildren(child, context);
            union = union == null ? box : union.union(box);
        }
        return union != null ? union : Aabb.centered(node.worldMatrix().getTranslation(), POINT_HALF_EXTENT);
    }

    private static Maybe<Aabb> own(SceneNode node, Maybe<BehaviorContext> context) {
        Aabb union = Greybox.of(node)
            .map(greybox -> Meshes.CUBE_TRIANGLES.bounds()
                .transformed(node.worldMatrix().mul(Mat4.scaling(greybox.size()))))
            .unwrapOr(null);

        if (context.isSome()) {
            for (NodeArchetype archetype : node.getArchetypes()) {
                if (!(archetype instanceof ArchetypeInstance instance)) {
                    continue;
                }
                Maybe<Aabb> box = BuiltinBehaviors.behaviorOf(instance.archetypeId())
                    .andThen(behavior -> behavior.bounds(node, instance, context.unwrap()));
                if (box.isSome()) {
                    union = union == null ? box.unwrap() : union.union(box.unwrap());
                }
            }
        }
        return Maybe.ofNullable(union);
    }
}
