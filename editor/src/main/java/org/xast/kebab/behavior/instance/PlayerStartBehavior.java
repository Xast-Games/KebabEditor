package org.xast.kebab.behavior.instance;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.xast.kebab.model.Meshes;
import org.xast.kebab.playtest.ActorSettings;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

/**
 * Draws the actor a playtest would spawn: its capsule, sized by the parameters
 * (not the node's scale), and an arrow for where it faces.
 */
public class PlayerStartBehavior implements ArchetypeBehavior {

    private static final Material MATERIAL = Material.solid(new Color(235, 90, 120));
    private static final int SEGMENTS = 24;
    private static final int CACHED_SIZES = 16;

    // Each size gets its own mesh; cached so the GPU cache sees the same instance every frame.
    private final Map<ActorSettings, MeshData> outlines = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ActorSettings, MeshData> eldest) {
            return size() > CACHED_SIZES;
        }
    };

    @Override
    public List<Drawable> drawableObjects(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        ActorSettings settings = settings(archetype);
        Mat4 world = unscaled(node);
        Mat4 arrow = world
            .mul(Mat4.translation(new Vec3(0, settings.height() * 0.5f, 0)))
            .mul(Mat4.scaling(settings.radius() * 3f));

        return List.of(
            new Drawable(outline(settings), world, MATERIAL, DrawMode.OVERLAY),
            new Drawable(Meshes.ARROW_LINES, arrow, MATERIAL, DrawMode.OVERLAY)
        );
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        // The outline is placed with the node's full matrix, so undo its scale here.
        float scale = scaleOf(node.worldMatrix());
        ActorSettings settings = settings(archetype);
        float r = settings.radius() / scale;

        return Maybe.some(new GizmoShape.GizmoAabb(new Aabb(-r, r, 0, settings.height() / scale, -r, r)));
    }

    @Override
    public Maybe<Aabb> bounds(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        ActorSettings settings = settings(archetype);
        float r = settings.radius();

        return Maybe.some(new Aabb(-r, r, 0, settings.height(), -r, r).transformed(unscaled(node)));
    }

    @Override
    public List<BehaviorProblem> validate(SceneNode node, ArchetypeInstance archetype, BehaviorContext context) {
        return ActorSettings.problems(archetype).stream()
            .<BehaviorProblem>map(problem -> new PlainBehaviorProblem("player_start: " + problem))
            .toList();
    }

    private static ActorSettings settings(ArchetypeInstance archetype) {
        return ActorSettings.from(archetype).unwrapOr(ActorSettings.DEFAULT);
    }

    private MeshData outline(ActorSettings settings) {
        return outlines.computeIfAbsent(settings,
            s -> Meshes.capsuleLines(s.radius(), s.height(), SEGMENTS));
    }

    private static Mat4 unscaled(SceneNode node) {
        Mat4 world = node.worldMatrix();
        return world.mul(Mat4.scaling(1f / scaleOf(world)));
    }

    private static float scaleOf(Mat4 world) {
        float scale = world.transformDirection(Vec3.unitX()).length();
        return scale > 1e-6f ? scale : 1f;
    }
}
