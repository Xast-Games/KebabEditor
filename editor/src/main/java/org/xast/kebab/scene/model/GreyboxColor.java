package org.xast.kebab.scene.model;

import java.awt.Color;
import java.util.Locale;

import org.xast.kebab.utils.Maybe;

/** The colours Kenney's prototype textures come in; every pattern exists in each of them. */
public enum GreyboxColor {
    DARK("Dark", new Color(51, 51, 53)),
    LIGHT("Light", new Color(211, 214, 217)),
    GREEN("Green", new Color(27, 217, 119)),
    ORANGE("Orange", new Color(255, 140, 0)),
    PURPLE("Purple", new Color(157, 34, 250)),
    RED("Red", new Color(255, 0, 56));

    private final String displayName;
    private final Color swatch;

    GreyboxColor(String displayName, Color swatch) {
        this.displayName = displayName;
        this.swatch = swatch;
    }

    public String displayName() {
        return displayName;
    }

    /** The texture's background colour, e.g. for edges and colour pickers. */
    public Color swatch() {
        return swatch;
    }

    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Maybe<GreyboxColor> fromJson(String value) {
        for (GreyboxColor color : values()) {
            if (color.jsonName().equals(value)) {
                return Maybe.some(color);
            }
        }
        return Maybe.none();
    }

    /** The closest one to a free colour, for greyboxes saved before the prototype textures. */
    public static GreyboxColor nearest(float red, float green, float blue) {
        GreyboxColor best = DARK;
        double bestDistance = Double.MAX_VALUE;
        for (GreyboxColor color : values()) {
            float[] rgb = color.swatch.getRGBColorComponents(null);
            double distance = Math.pow(rgb[0] - red, 2) + Math.pow(rgb[1] - green, 2) + Math.pow(rgb[2] - blue, 2);
            if (distance < bestDistance) {
                best = color;
                bestDistance = distance;
            }
        }
        return best;
    }
}
