package org.xast.kebab.behavior.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.CONTEXT;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.EPS;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.instanceOf;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.nodeAt;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.instance.PlayerStartBehavior;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.playtest.ActorSettings;
import org.xast.kebab.scene.model.SceneNode;

class PlayerStartBehaviorTest {

    private final PlayerStartBehavior behavior = new PlayerStartBehavior();

    @Test
    void boundsAreTheActorStandingOnTheNodeIgnoringItsScale() {
        SceneNode node = nodeAt(new Vec3(2, 1, 0), 3f);
        ArchetypeInstance start = instanceOf(BuiltinArchetypes.PLAYER_START);

        Aabb bounds = behavior.bounds(node, start, CONTEXT).unwrap();
        ActorSettings settings = ActorSettings.DEFAULT;

        assertEquals(1f, bounds.getMinY(), EPS);
        assertEquals(1f + settings.height(), bounds.getMaxY(), EPS);
        assertEquals(2f - settings.radius(), bounds.getMinX(), EPS);
    }

    @Test
    void defaultsAreValid() {
        SceneNode node = nodeAt(Vec3.zero(), 1f);

        assertTrue(behavior.validate(node, instanceOf(BuiltinArchetypes.PLAYER_START), CONTEXT).isEmpty());
        assertEquals(2, behavior.drawableObjects(node, instanceOf(BuiltinArchetypes.PLAYER_START), CONTEXT).size());
    }

    @Test
    void nonPositiveMetricsAreProblems() throws Exception {
        ArchetypeInstance start = instanceOf(BuiltinArchetypes.PLAYER_START);
        start.set(ActorSettings.GRAVITY, 0f);
        start.set(ActorSettings.RUN_SPEED, -1f);

        assertEquals(2, behavior.validate(nodeAt(Vec3.zero(), 1f), start, CONTEXT).size());
    }
}
