package org.xast.kebab.behavior.instance;

import java.awt.Color;

import org.xast.kebab.behavior.DrawMode;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Mat4;
import org.xast.kebab.model.AlphaMode;
import org.xast.kebab.model.Material;
import org.xast.kebab.model.Meshes;
import org.xast.kebab.model.TextureSlot;

public final class Placeholders {

    private Placeholders() {}

    public static Drawable brokenCube(Mat4 world, String detail) {
        return new Drawable(
            Meshes.CUBE_TRIANGLES,
            world,
            new Material(Color.WHITE, new TextureSlot.Missing(detail), AlphaMode.OPAQUE, true),
            DrawMode.SOLID
        );
    }

    public static Aabb brokenBounds(Mat4 world) {
        return Meshes.CUBE_TRIANGLES.bounds().transformed(world);
    }
}
