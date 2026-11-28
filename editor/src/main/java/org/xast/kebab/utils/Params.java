package org.xast.kebab.utils;

import java.awt.Color;

import org.xast.kebab.archetype.error.UnknownParamException;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.math.Vec3;

public final class Params {

    private Params() {}

    public static Maybe<Vec3> vec3(ArchetypeInstance archetype, String name) {
        try {
            return archetype.get(name) instanceof Vec3 value ? Maybe.some(value) : Maybe.none();
        } catch (UnknownParamException e) {
            return Maybe.none();
        }
    }

    public static Maybe<Float> number(ArchetypeInstance archetype, String name) {
        try {
            return archetype.get(name) instanceof Float value ? Maybe.some(value) : Maybe.none();
        } catch (UnknownParamException e) {
            return Maybe.none();
        }
    }

    public static Maybe<Color> color(ArchetypeInstance archetype, String name) {
        try {
            return archetype.get(name) instanceof Color value ? Maybe.some(value) : Maybe.none();
        } catch (UnknownParamException e) {
            return Maybe.none();
        }
    }

    public static Maybe<String> text(ArchetypeInstance archetype, String name) {
        try {
            return archetype.get(name) instanceof String value ? Maybe.some(value) : Maybe.none();
        } catch (UnknownParamException e) {
            return Maybe.none();
        }
    }

    /** Name of the Vec3 param that shifts a collider or trigger volume from the node's origin, in its local space. */
    public static final String OFFSET = "offset";

    /** The volume's offset from the node's origin; zero when it is missing, not a Vec3 or not finite. */
    public static Vec3 offset(ArchetypeInstance archetype) {
        return vec3(archetype, OFFSET).filter(Params::finite).unwrapOr(Vec3.zero());
    }

    /** A problem message when the offset is set but unusable. */
    public static Maybe<String> offsetProblem(String label, ArchetypeInstance archetype) {
        Maybe<Vec3> offset = vec3(archetype, OFFSET);
        if (offset.isNone()) {
            return Maybe.some(label + ": 'offset' is not a Vec3");
        }
        if (!finite(offset.unwrap())) {
            return Maybe.some(label + ": offset must be a finite number on every axis, got " + offset.unwrap());
        }
        return Maybe.none();
    }

    private static boolean finite(Vec3 v) {
        return Float.isFinite(v.getX()) && Float.isFinite(v.getY()) && Float.isFinite(v.getZ());
    }

    public static boolean positive(float value) {
        return Float.isFinite(value) && value > 0f;
    }
}
