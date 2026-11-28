package org.xast.kebab.render;

/** What a multi-object selection rotates and scales around. Moving always moves all of them together. */
public enum GizmoPivot {
    /** Each object turns and grows around its own origin; the gizmo sits on the primary object. */
    INDIVIDUAL,
    /** The selection turns and grows like one object around the center of its origins. */
    SELECTION_CENTER,
}
