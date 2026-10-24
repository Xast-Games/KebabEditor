package org.xast.kebab.playtest;

import java.nio.file.Path;

import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.ModelResult;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.scene.resolve.PrefabResult;
import org.xast.kebab.scene.resolve.ModelResult.BrokenReference;
import org.xast.kebab.scene.resolve.ModelResult.BrokenReference.Reason;
import org.xast.kebab.scene.resolve.PrefabResult.BrokenPrefab;

public final class PhysicsTestSupport {

    /** Resolves nothing: models and prefabs are all missing. */
    public static final BehaviorContext NO_ASSETS = new BehaviorContext(
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
        new PrefabResolver() {
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
        },
        Path.of("level.gltf")
    );

    private PhysicsTestSupport() {}
}
