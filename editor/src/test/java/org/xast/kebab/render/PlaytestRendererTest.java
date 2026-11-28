package org.xast.kebab.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.playtest.ActorSettings;

class PlaytestRendererTest {

    private static final ActorSettings SETTINGS = ActorSettings.DEFAULT;
    private static final Vec3 FEET = new Vec3(2, 1, -3);
    /** The closest third-person camera distance. */
    private static final float CLOSEST_ORBIT = 1.5f;

    @Test
    void theActorIsHiddenFromEyesInsideIt() {
        assertTrue(PlaytestRenderer.insideActor(FEET.add(new Vec3(0, SETTINGS.height() * 0.92f, 0)), FEET, SETTINGS));
        assertTrue(PlaytestRenderer.insideActor(FEET.add(new Vec3(SETTINGS.radius(), 0.5f, 0)), FEET, SETTINGS));
    }

    @Test
    void theActorIsShownFromOutside() {
        assertFalse(PlaytestRenderer.insideActor(FEET.add(new Vec3(0, 1, CLOSEST_ORBIT)), FEET, SETTINGS));
        assertFalse(PlaytestRenderer.insideActor(FEET.add(new Vec3(0, SETTINGS.height() + 1, 0)), FEET, SETTINGS));
        assertFalse(PlaytestRenderer.insideActor(FEET.add(new Vec3(0, -1, 0)), FEET, SETTINGS));
    }
}
