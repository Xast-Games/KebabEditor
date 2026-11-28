package org.xast.kebab.behavior.instance;

import java.nio.file.Path;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.math.Quat;
import org.xast.kebab.math.Transform;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.ModelResult;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.scene.resolve.PrefabResult;
import org.xast.kebab.scene.resolve.ModelResult.BrokenReference;
import org.xast.kebab.scene.resolve.ModelResult.BrokenReference.Reason;
import org.xast.kebab.scene.resolve.PrefabResult.BrokenPrefab;

final class BehaviorTestSupport {

    static final float EPS = 1e-4f;

    static final PrefabResolver NO_PREFABS = new PrefabResolver() {
        @Override
        public PrefabResult resolve(Path scene, String relPath) {
            return new BrokenPrefab(scene, Reason.MISSING, "no prefabs in this test");
        }

        @Override
        public boolean wouldCycle(Path from, Path to) {
            return false;
        }

        @Override
        public void invalidateAll() {}
    };

    static final BehaviorContext CONTEXT = new BehaviorContext(
        new ModelResolver() {
            @Override
            public ModelResult resolve(Path scene, String relPath) {
                return new BrokenReference(scene, Reason.MISSING, "no models in this test");
            }

            @Override
            public boolean wouldCycle(Path from, Path to) {
                return false;
            }
        },
        NO_PREFABS,
        Path.of("level.gltf").toAbsolutePath()
    );

    private BehaviorTestSupport() {}

    static ArchetypeInstance instanceOf(String archetypeId) {
        return new ArchetypeInstance(BuiltinArchetypes.find(archetypeId).unwrap());
    }

    static SceneNode nodeAt(Vec3 translation, float scale) {
        SceneNode node = new SceneNode("node");
        node.setTransform(new Transform(translation, Quat.identity(), scale));

        return node;
    }
}
