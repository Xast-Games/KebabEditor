package org.xast.kebab.behavior.instance;

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
import org.xast.kebab.model.Meshes;
import org.xast.kebab.model.ModelPart;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.ModelResult;
import org.xast.kebab.scene.resolve.ModelResult.BrokenReference;
import org.xast.kebab.scene.resolve.ModelResult.ResolvedModel;
import org.xast.kebab.utils.Maybe;
import org.xast.kebab.utils.Params;

public class ModelRefBehavior implements ArchetypeBehavior {

    private static final String PATH = "path";

    @Override
    public List<Drawable> drawableObjects(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        Mat4 world = node.worldMatrix();

        return switch (resolve(archetype, context)) {
            case ResolvedModel model -> model.parts().stream()
                .map(part -> drawable(part, world))
                .toList();

            case BrokenReference broken -> List.of(Placeholders.brokenCube(world, broken.detail()));
        };
    }

    @Override
    public Maybe<GizmoShape> gizmoSelectionShape(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return Maybe.some(switch (resolve(archetype, context)) {
            case ResolvedModel model -> new GizmoShape.GizmoAabb(model.bounds());
            case BrokenReference _   -> new GizmoShape.GizmoBox(Vec3.one());
        });
    }

    @Override
    public Maybe<Aabb> bounds(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        Aabb local = switch (resolve(archetype, context)) {
            case ResolvedModel model -> model.bounds();
            case BrokenReference _ -> Meshes.CUBE_TRIANGLES.bounds();
        };

        return Maybe.some(local.transformed(node.worldMatrix()));
    }

    @Override
    public List<BehaviorProblem> validate(
        SceneNode node,
        ArchetypeInstance archetype,
        BehaviorContext context
    ) {
        return switch (resolve(archetype, context)) {
            case BrokenReference broken -> List.of(new PlainBehaviorProblem("model_ref: " + broken.detail()));

            case ResolvedModel model when model.parts().isEmpty() ->
                List.of(new PlainBehaviorProblem("model_ref: " + model.path().getFileName() + " has nothing to draw"));

            case ResolvedModel _ -> List.of();
        };
    }

    private static ModelResult resolve(ArchetypeInstance archetype, BehaviorContext context) {
        String path = Params.text(archetype, PATH).unwrapOr("");

        return context.models().resolve(context.scene(), path);
    }

    private static Drawable drawable(ModelPart part, Mat4 world) {
        return new Drawable(part.mesh(), world.mul(part.transform()), part.material(), DrawMode.SOLID);
    }
}
