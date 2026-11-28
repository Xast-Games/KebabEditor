package org.xast.kebab.scene.model;

import java.util.Objects;

import org.xast.kebab.math.Vec3;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A blockout primitive attached to a node, stored as
 * {@code extras.kebab.greybox = { "shape": "box", "size": [x, y, z], "pattern": 1, "color": "orange" }}.
 *
 * The size is per-axis and applied on top of the node transform, whose scale
 * is uniform, so platforms of any proportion are possible. The surface is one of
 * Kenney's prototype textures: a pattern in one of its colours, repeating once per metre.
 *
 * @param pattern 1 to {@link #PATTERNS}; pattern 1 is the "1x1 meter" wall
 */
public record Greybox(GreyboxShape shape, Vec3 size, GreyboxColor color, int pattern) {

    public static final int PATTERNS = 13;
    public static final GreyboxColor DEFAULT_COLOR = GreyboxColor.ORANGE;
    public static final int DEFAULT_PATTERN = 1;

    private static final String KEY = "greybox";
    private static final String SHAPE = "shape";
    private static final String SIZE = "size";
    private static final String COLOR = "color";
    private static final String PATTERN = "pattern";

    public Greybox {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        if (pattern < 1 || pattern > PATTERNS) {
            throw new IllegalArgumentException("pattern must be 1 to " + PATTERNS + ", got " + pattern);
        }
        size = size.copy();
    }

    /** A greybox with the default colour and pattern. */
    public Greybox(GreyboxShape shape, Vec3 size) {
        this(shape, size, DEFAULT_COLOR, DEFAULT_PATTERN);
    }

    public static Greybox unit(GreyboxShape shape) {
        return new Greybox(shape, Vec3.one());
    }

    @Override
    public Vec3 size() {
        return size.copy();
    }

    public Greybox withShape(GreyboxShape newShape) {
        return new Greybox(newShape, size, color, pattern);
    }

    public Greybox withSize(Vec3 newSize) {
        return new Greybox(shape, newSize, color, pattern);
    }

    public Greybox withColor(GreyboxColor newColor) {
        return new Greybox(shape, size, newColor, pattern);
    }

    public Greybox withPattern(int newPattern) {
        return new Greybox(shape, size, color, newPattern);
    }

    /** The bundled prototype texture of this pattern in this colour, as a classpath resource. */
    public String textureResource() {
        return textureResource(color, pattern);
    }

    public static String textureResource(GreyboxColor color, int pattern) {
        return String.format("textures/prototype/%s/pattern_%02d.png", color.jsonName(), pattern);
    }

    /** The node's greybox, or none when it has none or the stored data is malformed. */
    public static Maybe<Greybox> of(SceneNode node) {
        JsonNode json = KebabExtras.read(node, KEY);
        if (!json.isObject()) {
            return Maybe.none();
        }

        Maybe<GreyboxShape> shape = GreyboxShape.fromJson(json.path(SHAPE).asText(""));
        float[] size = numbers(json.path(SIZE), 3, new float[] { 1, 1, 1 });
        Maybe<GreyboxColor> color = color(json.path(COLOR));
        int pattern = pattern(json.path(PATTERN));

        if (shape.isNone() || size == null || color.isNone() || pattern == 0) {
            return Maybe.none();
        }

        return Maybe.some(new Greybox(shape.unwrap(), new Vec3(size[0], size[1], size[2]), color.unwrap(), pattern));
    }

    public void writeTo(SceneNode node) {
        ObjectNode json = KebabExtras.writable(node).putObject(KEY);
        json.put(SHAPE, shape.jsonName());

        ArrayNode sizeJson = json.putArray(SIZE);
        sizeJson.add(size.getX()).add(size.getY()).add(size.getZ());

        json.put(PATTERN, pattern);
        json.put(COLOR, color.jsonName());
    }

    // A colour name, or the [r, g, b, a] of greyboxes saved before the prototype textures,
    // which gets the closest texture colour
    private static Maybe<GreyboxColor> color(JsonNode json) {
        if (json.isMissingNode()) {
            return Maybe.some(DEFAULT_COLOR);
        }
        if (json.isTextual()) {
            return GreyboxColor.fromJson(json.textValue());
        }
        float[] rgba = numbers(json, 4, null);
        return rgba == null ? Maybe.none() : Maybe.some(GreyboxColor.nearest(rgba[0], rgba[1], rgba[2]));
    }

    // 0 when present but not a pattern number
    private static int pattern(JsonNode json) {
        if (json.isMissingNode()) {
            return DEFAULT_PATTERN;
        }
        if (!json.isIntegralNumber() || json.intValue() < 1 || json.intValue() > PATTERNS) {
            return 0;
        }
        return json.intValue();
    }

    // A missing value falls back; a present but malformed one is rejected (null).
    private static float[] numbers(JsonNode array, int count, float[] fallback) {
        if (array.isMissingNode()) {
            return fallback;
        }
        if (!array.isArray() || array.size() != count) {
            return null;
        }

        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            if (!array.get(i).isNumber()) {
                return null;
            }
            values[i] = array.get(i).floatValue();
        }
        return values;
    }
}
