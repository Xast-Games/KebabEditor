package org.xast.kebab.behavior.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.CONTEXT;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.instanceOf;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.nodeAt;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.DrawMode;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.model.Meshes;
import org.xast.kebab.scene.model.SceneNode;

class TriggerBehaviorTest {

    private final TriggerBehavior behavior = new TriggerBehavior();

    @Test
    void drawsAnOverlayBoxInADifferentColourFromColliders() throws Exception {
        ArchetypeInstance trigger = instanceOf(BuiltinArchetypes.TRIGGER);
        trigger.set("size", new Vec3(2, 2, 2));
        SceneNode node = nodeAt(Vec3.zero(), 1f);

        Drawable drawable = behavior.drawableObjects(node, trigger, CONTEXT).get(0);

        assertEquals(Meshes.CUBE_LINES, drawable.mesh());
        assertEquals(DrawMode.OVERLAY, drawable.mode());

        ArchetypeInstance box = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        box.set("size", new Vec3(2, 2, 2));
        Drawable boxDrawable = new ColliderBoxBehavior().drawableObjects(node, box, CONTEXT).get(0);
        assertNotEquals(boxDrawable.material().baseColor(), drawable.material().baseColor());
    }

    @Test
    void validationMessageNamesTheTrigger() throws Exception {
        ArchetypeInstance trigger = instanceOf(BuiltinArchetypes.TRIGGER);
        trigger.set("size", Vec3.zero());

        var problems = behavior.validate(nodeAt(Vec3.zero(), 1f), trigger, CONTEXT);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).getMessage().startsWith("trigger:"));
    }
}
