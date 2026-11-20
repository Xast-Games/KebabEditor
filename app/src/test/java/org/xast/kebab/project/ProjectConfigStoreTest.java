package org.xast.kebab.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigStoreTest {
    @TempDir Path dir;
    final ProjectConfigStore store = new ProjectConfigStore(new ObjectMapper());
    static final String VALID = """
        {"version":1,"assetRoot":".","assetFolders":["assets"],"scenes":["level.gltf","scenes/other.gltf"]}
        """;
    @Test void readsAndResolvesConfigurationIncludingNestedScene() throws Exception {
        Files.writeString(dir.resolve(ProjectConfigStore.FILE_NAME), VALID);
        Files.createDirectories(dir.resolve("scenes"));
        ProjectAssets project = store.loadForScene(dir.resolve("scenes/other.gltf"));
        assertEquals(dir, project.root());
        assertEquals(List.of(dir.resolve("assets")), project.folders());
        assertTrue(project.isScene(dir.resolve("level.gltf")));
        assertTrue(project.isScene(dir.resolve("scenes/other.gltf")));
    }
    @Test void aConfigWithoutScenesFolderDefaultsToScenes() throws Exception {
        Files.writeString(dir.resolve(ProjectConfigStore.FILE_NAME), VALID);
        ProjectConfig config = store.read(dir.resolve(ProjectConfigStore.FILE_NAME));
        assertEquals(ProjectConfig.DEFAULT_SCENES_FOLDER, config.scenesFolder());
        assertEquals(dir.resolve("scenes"), store.resolve(dir, config).scenesFolder());
    }
    @Test void scenesFolderMustBeAStringInsideTheRoot() throws Exception {
        Path file = dir.resolve(ProjectConfigStore.FILE_NAME);
        Files.writeString(file, VALID.replace("}", ",\"scenesFolder\":42}"));
        assertThrows(IOException.class, () -> store.read(file));
        Files.writeString(file, VALID.replace("}", ",\"scenesFolder\":\"../out\"}"));
        assertThrows(IOException.class, () -> store.read(file));
        Files.writeString(file, VALID.replace("}", ",\"scenesFolder\":\".\"}"));
        assertEquals(dir, store.resolve(dir, store.read(file)).scenesFolder());
    }
    @Test void missingConfigUsesLegacySceneAndDirectory() throws Exception {
        Path scene = dir.resolve("legacy.gltf");
        ProjectAssets project = store.loadForScene(scene);
        assertEquals(List.of(dir), project.folders());
        assertEquals(List.of(scene), project.scenes());
        assertEquals(dir, project.scenesFolder());
        assertFalse(Files.exists(dir.resolve(ProjectConfigStore.FILE_NAME)));
    }
    @Test void rejectsMalformedVersionsAndFieldTypes() throws Exception {
        Path file = dir.resolve(ProjectConfigStore.FILE_NAME);
        for (String invalid : List.of("{", "null", "[]", VALID.replace("\"version\":1", "\"version\":2"),
            VALID.replace("\"version\":1", "\"version\":1.0"), VALID.replace("\"assets\"", "null"),
            VALID.replace("\"assetRoot\":\".\"", "\"assetRoot\":42"))) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> store.read(file), invalid);
        }
    }
    @Test void resolvesAssetRootAndMissingDirectories() throws Exception {
        ProjectAssets project = store.resolve(dir, new ProjectConfig(1, "content", List.of("models"), List.of("level.gltf")));
        assertEquals(dir.resolve("content"), project.root());
        assertEquals(List.of(dir.resolve("content/models")), project.folders());
    }
    @Test void rejectsTraversalAbsoluteAndSymlinkPathsIncludingMissingChildren() throws Exception {
        for (String bad : List.of("../escape", dir.resolve("absolute").toString(), "https://example.com", "C:\\escape")) {
            assertThrows(IOException.class, () -> store.resolve(dir, new ProjectConfig(1, bad, List.of(), List.of())));
            assertThrows(IOException.class, () -> store.resolve(dir, new ProjectConfig(1, ".", List.of(bad), List.of())));
            assertThrows(IOException.class, () -> store.resolve(dir, new ProjectConfig(1, ".", List.of(), List.of(bad))));
        }
        Path outside = Files.createTempDirectory(dir.getParent(), "kebab-outside-");
        try {
            Files.createSymbolicLink(dir.resolve("alias"), outside);
            assertThrows(IOException.class, () -> ProjectAssets.resolveInside(dir, "alias/missing/file"));
            Files.createSymbolicLink(dir.resolve("broken"), outside.resolve("missing"));
            assertThrows(IOException.class, () -> ProjectAssets.resolveInside(dir, "broken/file"));
        } finally { Files.deleteIfExists(outside); }
    }
    @Test void creationDoesNotOverwriteConfigSceneOrArchetypes() throws Exception {
        var writer = new FileProjectWriter(new org.xast.kebab.scene.io.GltfSceneWriter(new ObjectMapper()), new ObjectMapper());
        Files.writeString(dir.resolve("archetypes.json"), "KEEP");
        Path project = writer.createProject(dir);
        assertEquals(dir.resolve(ProjectConfigStore.FILE_NAME), project);
        assertTrue(Files.isDirectory(dir.resolve("assets")));
        assertTrue(store.read(project).scenes().isEmpty());
        Path scene = writer.createScene(project, "", "level");
        assertTrue(store.loadForScene(scene).isScene(scene));
        String originalConfig = Files.readString(project);
        assertThrows(FileAlreadyExistsException.class, () -> writer.createProject(dir));
        assertThrows(FileAlreadyExistsException.class, () -> writer.createScene(project, "", "level"));
        assertEquals("KEEP", Files.readString(dir.resolve("archetypes.json")));
        assertEquals(originalConfig, Files.readString(project));
        assertEquals(ProjectTemplates.MINIMAL_GLTF, Files.readString(scene));
    }
}
