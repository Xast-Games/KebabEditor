package org.xast.kebab.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.behavior.DrawMode;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Mat4;
import org.xast.kebab.model.Material;
import org.xast.kebab.model.MeshData;
import org.xast.kebab.model.Meshes;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.scene.model.SceneNode;

import com.jogamp.opengl.GL3;

import lombok.RequiredArgsConstructor;

/**
 * Draws greyboxes as solid shapes with darker edges. A greybox with the no_volume
 * archetype has no solid; its edges show only while selected or while
 * {@code renderNoVolume} says so.
 */
@RequiredArgsConstructor
public class GreyboxRenderer implements RenderPass {

    private final DrawableRenderer drawableObjects;
    private final BooleanSupplier renderNoVolume;

    @Override
    public void render(GL3 gl, RenderContext context) {
        if (context.scene().isNone()) {
            return;
        }

        List<Drawable> solids = new ArrayList<>();
        List<Drawable> edges = new ArrayList<>();
        boolean wireframes = renderNoVolume.getAsBoolean();

        context.scene().unwrap().traverse().forEach(node ->
            Greybox.of(node).ifSome(greybox -> collect(context, node, greybox, wireframes, solids, edges))
        );

        drawableObjects.draw(gl, context, solids, true);
        drawableObjects.draw(gl, context, edges, false);
    }

    private static void collect(
        RenderContext context,
        SceneNode node,
        Greybox greybox,
        boolean wireframes,
        List<Drawable> solids,
        List<Drawable> edges
    ) {
        Mat4 world = node.worldMatrix().mul(Mat4.scaling(greybox.size()));
        boolean selected = context.isSelected(node);
        boolean noVolume = node.hasArchetype(BuiltinArchetypes.NO_VOLUME);

        if (noVolume && !wireframes && !selected) {
            return;
        }
        if (!noVolume) {
            solids.add(new Drawable(solidMesh(greybox.shape()), world, Material.solid(greybox.color()), DrawMode.SOLID));
        }
        edges.add(new Drawable(
            edgeMesh(greybox.shape()),
            world,
            // The greybox's own color is the most visible line color when there is no solid behind it.
            Material.solid(selected ? Palette.SELECTION : noVolume ? greybox.color() : edgeColor(greybox.color())),
            selected ? DrawMode.OVERLAY : DrawMode.SOLID
        ));
    }

    @Override
    public void dispose(GL3 gl) {
        // GPU meshes are owned by the shared DrawableRenderer.
    }

    @Override
    public void init(GL3 gl) {
        // Uses the shared DrawableRenderer, which the viewport initializes.
    }

    static MeshData solidMesh(GreyboxShape shape) {
        return switch (shape) {
            case BOX -> Meshes.CUBE_TRIANGLES;
            case SPHERE -> Meshes.SPHERE_TRIANGLES;
        };
    }

    static MeshData edgeMesh(GreyboxShape shape) {
        return switch (shape) {
            case BOX -> Meshes.CUBE_LINES;
            case SPHERE -> Meshes.SPHERE_LINES;
        };
    }

    private static Color edgeColor(Color base) {
        return new Color(
            (int) (base.getRed() * 0.45f),
            (int) (base.getGreen() * 0.45f),
            (int) (base.getBlue() * 0.45f),
            base.getAlpha()
        );
    }
}
