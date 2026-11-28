package org.xast.kebab.ui.playtest;

import org.xast.kebab.math.Vec3;
import org.xast.kebab.playtest.physics.PhysicsWorld;
import org.xast.kebab.render.Camera;

/**
 * A camera orbiting a target at a distance. It stops short of solids between it
 * and the target, so walls do not hide the actor.
 *
 * Zooming in past the closest distance switches to first person: the camera sits at
 * the actor's eyes and turns in place. Zooming out again goes back to third person.
 */
public final class ThirdPersonRig {

    static final float START_PITCH = (float) Math.toRadians(-20);
    static final float MIN_PITCH = (float) Math.toRadians(-80);
    static final float MAX_PITCH = (float) Math.toRadians(30);
    /** First person may look almost straight up and down. */
    static final float MIN_FIRST_PERSON_PITCH = (float) Math.toRadians(-85);
    static final float MAX_FIRST_PERSON_PITCH = (float) Math.toRadians(85);
    static final float START_DISTANCE = 6f;
    static final float MIN_DISTANCE = 1.5f;
    static final float MAX_DISTANCE = 20f;
    /** Gap kept between the camera and a solid it would otherwise sit in. */
    static final float CLEARANCE = 0.25f;

    private static final double LOOK_DEGREES_PER_PIXEL = 0.25;
    private static final float WHEEL_STEP = 0.8f;
    private static final float CLOSEST = 0.1f;

    private float yaw;
    private float pitch = START_PITCH;
    private float distance = START_DISTANCE;
    private boolean firstPerson;

    /** @param yaw start looking the way the actor faces */
    public ThirdPersonRig(float yaw) {
        this.yaw = yaw;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float distance() {
        return distance;
    }

    public boolean isFirstPerson() {
        return firstPerson;
    }

    /** Turn by a mouse drag: dragging right turns right, dragging up looks up. */
    public void orbit(int dxPixels, int dyPixels) {
        yaw -= (float) Math.toRadians(dxPixels * LOOK_DEGREES_PER_PIXEL);
        pitch = clampPitch(pitch - (float) Math.toRadians(dyPixels * LOOK_DEGREES_PER_PIXEL));
    }

    /**
     * Wheel towards the user moves the camera back. Wheeling away while already at the closest
     * distance goes to first person, so one quick scroll stops at the closest distance first.
     */
    public void zoom(double wheelRotation) {
        if (firstPerson) {
            if (wheelRotation > 0) {
                firstPerson = false;
                distance = MIN_DISTANCE;
                pitch = clampPitch(pitch);
            }
            return;
        }
        if (wheelRotation < 0 && distance <= MIN_DISTANCE) {
            firstPerson = true;
            return;
        }
        distance = Math.clamp(distance + (float) wheelRotation * WHEEL_STEP, MIN_DISTANCE, MAX_DISTANCE);
    }

    private float clampPitch(float value) {
        return firstPerson
            ? Math.clamp(value, MIN_FIRST_PERSON_PITCH, MAX_FIRST_PERSON_PITCH)
            : Math.clamp(value, MIN_PITCH, MAX_PITCH);
    }

    /** Third person orbiting {@code target}, as {@link #place(Camera, Vec3, Vec3, PhysicsWorld)} with the eyes there too. */
    public void place(Camera camera, Vec3 target, PhysicsWorld world) {
        place(camera, target, target, world);
    }

    /**
     * Point the camera at {@code target} from behind, in front of any solid in the way;
     * in first person, look out from {@code eye} instead.
     */
    public void place(Camera camera, Vec3 target, Vec3 eye, PhysicsWorld world) {
        camera.setOrientation(yaw, pitch);
        if (firstPerson) {
            camera.getPosition().set(eye);
            return;
        }

        Vec3 back = camera.forward().negateLocal();
        float reach = world.raycast(target, back, distance + CLEARANCE)
            .map(hit -> Math.max(hit - CLEARANCE, CLOSEST))
            .unwrapOr(distance);

        camera.getPosition().set(target.add(back.mulLocal(Math.min(reach, distance))));
    }
}
