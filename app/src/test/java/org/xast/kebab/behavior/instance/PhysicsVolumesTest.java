package org.xast.kebab.behavior.instance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.CONTEXT;
import static org.xast.kebab.behavior.instance.BehaviorTestSupport.instanceOf;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.behavior.Drawable;
import org.xast.kebab.behavior.instance.BuiltinBehaviors;
import org.xast.kebab.math.Aabb;
import org.xast.kebab.math.Quat;
import org.xast.kebab.math.Transform;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.playtest.physics.PhysicsVolume;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.scene.resolve.PrefabResult;
import org.xast.kebab.scene.resolve.PrefabResult.LoadedPrefab;

/** What collides in a playtest must be exactly what the viewport shows. */
class PhysicsVolumesTest {

    private static final float EPS = 1e-3f;

    private static SceneNode turnedNode() {
        SceneNode node = new SceneNode("thing");
        node.setTransform(new Transform(
            new Vec3(3, 1, -2),
            Quat.fromAxisAngle(new Vec3(1, 1, 0), 0.7f),
            1.5f
        ));
        return node;
    }

    private static ArchetypeBehavior behavior(String id) {
        return BuiltinBehaviors.behaviorOf(id).unwrap();
    }

    private static void assertSameBox(Aabb expected, Aabb actual) {
        assertEquals(expected.getMinX(), actual.getMinX(), EPS);
        assertEquals(expected.getMaxX(), actual.getMaxX(), EPS);
        assertEquals(expected.getMinY(), actual.getMinY(), EPS);
        assertEquals(expected.getMaxY(), actual.getMaxY(), EPS);
        assertEquals(expected.getMinZ(), actual.getMinZ(), EPS);
        assertEquals(expected.getMaxZ(), actual.getMaxZ(), EPS);
    }

    @Test
    void boxArchetypesCollideExactlyWhereTheyAreDrawn() throws Exception {
        for (String id : List.of(BuiltinArchetypes.COLLIDER_BOX, BuiltinArchetypes.TRIGGER)) {
            SceneNode node = turnedNode();
            ArchetypeInstance archetype = instanceOf(id);
            archetype.set("size", new Vec3(2, 1, 3));

            List<PhysicsVolume> volumes = behavior(id).physicsVolumes(node, archetype, CONTEXT);
            Drawable drawn = behavior(id).drawableObjects(node, archetype, CONTEXT).getFirst();

            assertEquals(1, volumes.size(), id);
            assertEquals("thing", volumes.getFirst().name(), id);
            assertEquals(PhysicsVolume.Shape.BOX, volumes.getFirst().shape(), id);
            assertArrayEquals(drawn.world().toArray(), volumes.getFirst().world().toArray(), 1e-5f, id);
            assertSameBox(
                behavior(id).bounds(node, archetype, CONTEXT).unwrap(),
                volumes.getFirst().convexShape().unwrap().bounds()
            );
        }
    }

    @Test
    void colliderIsSolidAndTriggerIsNot() {
        SceneNode node = turnedNode();

        assertEquals(PhysicsVolume.Role.SOLID, behavior(BuiltinArchetypes.COLLIDER_BOX)
            .physicsVolumes(node, instanceOf(BuiltinArchetypes.COLLIDER_BOX), CONTEXT).getFirst().role());
        assertEquals(PhysicsVolume.Role.TRIGGER, behavior(BuiltinArchetypes.TRIGGER)
            .physicsVolumes(node, instanceOf(BuiltinArchetypes.TRIGGER), CONTEXT).getFirst().role());
    }

    @Test
    void sphereColliderCollidesExactlyWhereItIsDrawn() throws Exception {
        SceneNode node = turnedNode();
        ArchetypeInstance archetype = instanceOf(BuiltinArchetypes.COLLIDER_SPHERE);
        archetype.set("radius", 0.8f);
        ArchetypeBehavior behavior = behavior(BuiltinArchetypes.COLLIDER_SPHERE);

        PhysicsVolume volume = behavior.physicsVolumes(node, archetype, CONTEXT).getFirst();
        Drawable drawn = behavior.drawableObjects(node, archetype, CONTEXT).getFirst();

        assertEquals(PhysicsVolume.Shape.SPHERE, volume.shape());
        assertEquals(PhysicsVolume.Role.SOLID, volume.role());
        assertArrayEquals(drawn.world().toArray(), volume.world().toArray(), 1e-5f);
        assertSameBox(behavior.bounds(node, archetype, CONTEXT).unwrap(), volume.convexShape().unwrap().bounds());
    }

    @Test
    void invalidSizesAddNothing() throws Exception {
        ArchetypeInstance flat = instanceOf(BuiltinArchetypes.COLLIDER_BOX);
        flat.set("size", new Vec3(1, 0, 1));
        ArchetypeInstance shrunk = instanceOf(BuiltinArchetypes.COLLIDER_SPHERE);
        shrunk.set("radius", -1f);

        assertTrue(behavior(BuiltinArchetypes.COLLIDER_BOX).physicsVolumes(turnedNode(), flat, CONTEXT).isEmpty());
        assertTrue(behavior(BuiltinArchetypes.COLLIDER_SPHERE).physicsVolumes(turnedNode(), shrunk, CONTEXT).isEmpty());
    }

    @Test
    void nonPhysicalArchetypesAddNothing() {
        for (String id : List.of(
            BuiltinArchetypes.MODEL_REF,
            BuiltinArchetypes.POINT_LIGHT,
            BuiltinArchetypes.DIR_LIGHT,
            BuiltinArchetypes.SPOT_LIGHT,
            BuiltinArchetypes.SNAP_DISTANCE,
            BuiltinArchetypes.PLAYER_START
        )) {
            assertTrue(behavior(id).physicsVolumes(turnedNode(), instanceOf(id), CONTEXT).isEmpty(), id);
        }
    }

    @Test
    void prefabCollidesLikeItIsDrawn() throws Exception {
        Scene prefab = new Scene();
        SceneNode wall = new SceneNode("wall");
        wall.getTransform().setTranslation(new Vec3(1, 0, 0));
        wall.addArchetype(instanceOf(BuiltinArchetypes.COLLIDER_BOX));
        prefab.add(wall);
        // The prefab reference does not draw greyboxes, so it must not collide with them either.
        SceneNode block = new SceneNode("block");
        new Greybox(GreyboxShape.BOX, Vec3.one(), Color.GRAY).writeTo(block);
        prefab.add(block);

        SceneNode reference = new SceneNode("tower");
        reference.getTransform().setTranslation(new Vec3(10, 0, 0));
        ArchetypeInstance archetype = instanceOf(BuiltinArchetypes.PREFAB_REF);
        archetype.set("path", "tower.gltf");

        List<PhysicsVolume> volumes = behavior(BuiltinArchetypes.PREFAB_REF)
            .physicsVolumes(reference, archetype, withPrefab(prefab));

        assertEquals(List.of("tower > wall"), volumes.stream().map(PhysicsVolume::name).toList());
        assertEquals(PhysicsVolume.Role.SOLID, volumes.getFirst().role());
        // The wall is 1 m right of the prefab root, which is 10 m right in the level.
        assertTrue(new Vec3(11, 0, 0).approxEquals(volumes.getFirst().world().getTranslation(), EPS));
    }

    @Test
    void brokenPrefabAddsNothing() throws Exception {
        ArchetypeInstance archetype = instanceOf(BuiltinArchetypes.PREFAB_REF);
        archetype.set("path", "missing.gltf");

        assertTrue(behavior(BuiltinArchetypes.PREFAB_REF).physicsVolumes(turnedNode(), archetype, CONTEXT).isEmpty());
    }

    private static BehaviorContext withPrefab(Scene prefab) {
        PrefabResolver prefabs = new PrefabResolver() {
            @Override
            public PrefabResult resolve(Path scene, String relPath) {
                return new LoadedPrefab(Path.of(relPath).toAbsolutePath(), prefab);
            }

            @Override
            public boolean wouldCycle(Path from, Path to) {
                return false;
            }

            @Override
            public void invalidateAll() {}
        };

        return new BehaviorContext(CONTEXT.models(), prefabs, Path.of("level.gltf"));
    }
}
