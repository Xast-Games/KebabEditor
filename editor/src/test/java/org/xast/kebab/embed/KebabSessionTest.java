package org.xast.kebab.embed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KebabSessionTest {

    @TempDir
    Path directory;

    @Test
    void onlyScenesRegisteredInAProjectAreScenes() throws Exception {
        Files.writeString(directory.resolve("kebab-project.json"),
            "{\"version\":1,\"assetRoot\":\".\",\"assetFolders\":[],\"scenes\":[\"scenes/level.gltf\"]}");
        Path scenes = Files.createDirectories(directory.resolve("scenes"));
        Path level = Files.writeString(scenes.resolve("level.gltf"), "{\"asset\":{\"version\":\"2.0\"}}");
        Path model = Files.writeString(scenes.resolve("chair.gltf"), "{\"asset\":{\"version\":\"2.0\"}}");

        assertTrue(KebabSession.isScene(level));
        assertFalse(KebabSession.isScene(model));
    }

    @Test
    void aSceneWithoutAProjectIsNotRegistered() throws Exception {
        Path level = Files.writeString(directory.resolve("level.gltf"), "{\"asset\":{\"version\":\"2.0\"}}");

        assertFalse(KebabSession.isScene(level));
    }
}
