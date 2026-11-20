package org.xast.kebab.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.scene.io.GltfSceneReader;
import org.xast.kebab.scene.io.GltfSceneWriter;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.CompositeModelExtractor;
import org.xast.kebab.scene.resolve.GltfMeshExtractor;
import org.xast.kebab.scene.resolve.ModelResult;

import com.fasterxml.jackson.databind.ObjectMapper;

class FileProjectIoTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectWriter writer = new FileProjectWriter(new GltfSceneWriter(mapper), mapper);
    private final ProjectLoader loader = new FileProjectLoader(
        new GltfSceneReader(mapper),
        mapper,
        new CompositeModelExtractor(List.of(new GltfMeshExtractor()))
    );

    @TempDir
    Path directory;

    @Test
    void newProjectLoadsAsAnEmptySceneWithBuiltinsAvailable() throws Exception {
        Path file = projectWithScene(directory, "", "level");

        ProjectRepository project = loader.load(file);

        assertEquals(directory.resolve(ProjectConfigStore.FILE_NAME), file);
        assertEquals(file, project.projectFile());
        assertEquals(directory.resolve("level.gltf"), project.filePath().unwrap());
        assertTrue(project.sceneData().unwrap().getRootNodes().isEmpty());
        assertTrue(project.archetypeCatalog().find("collider_box").isSome());
        assertTrue(project.archetypeCatalog().getRegistry().getArchetypes().isEmpty());
    }

    @Test
    void savedArchetypesComeBackButBuiltinsAreNotWrittenToTheFile() throws Exception {
        Path file = projectWithScene(directory, "", "level");
        ProjectRepository project = loader.load(file);
        project.archetypeCatalog().getRegistry().add(new ArchetypeDefinition("mine"));

        writer.saveArchetypes(project.filePath().unwrap(), project.archetypeCatalog().getRegistry());

        assertTrue(loader.load(file).archetypeCatalog().find("mine").isSome());
        assertTrue(!Files.readString(ProjectTemplates.archetypesFileFor(file)).contains("collider_box"));
    }

    @Test
    void savedSceneComesBack() throws Exception {
        Path file = projectWithScene(directory, "", "level");
        ProjectRepository project = loader.load(file);
        project.sceneData().unwrap().add(new SceneNode("crate"));

        writer.saveScene(project.filePath().unwrap(), project.sceneData().unwrap());

        ProjectRepository reloaded = loader.load(file);
        assertEquals(1, reloaded.sceneData().unwrap().getRootNodes().size());
        assertEquals("crate", reloaded.sceneData().unwrap().getRootNodes().get(0).getName());
    }

    @Test
    void eachLoadedProjectGetsItsOwnResolverRootedAtItsFolder() throws Exception {
        Path first = projectWithScene(directory.resolve("one"), "", "level");
        Path second = projectWithScene(directory.resolve("two"), "", "level");
        Files.writeString(directory.resolve("one/tri.gltf"), TRIANGLE_GLTF);

        ProjectRepository one = loader.load(first);
        ProjectRepository two = loader.load(second);

        assertNotSame(one.modelResolver(), two.modelResolver());
        assertInstanceOf(ModelResult.ResolvedModel.class, one.modelResolver().resolve(first, "tri.gltf"));
        // The other project cannot see the first project's files
        assertInstanceOf(ModelResult.BrokenReference.class, two.modelResolver().resolve(second, "../one/tri.gltf"));
    }

    @Test
    void aScenesFolderHoldsTheFirstSceneAndIsBrowsable() throws Exception {
        Path file = projectWithScene(directory, "scenes", "world");

        ProjectRepository project = loader.load(file);

        Path scene = directory.resolve("scenes/world.gltf");
        assertEquals(scene, project.filePath().unwrap());
        assertEquals(file, project.projectFile());
        assertTrue(Files.isRegularFile(directory.resolve(ProjectTemplates.ARCHETYPES_FILE)));
        assertTrue(!Files.exists(directory.resolve("scenes/" + ProjectTemplates.ARCHETYPES_FILE)));
        assertTrue(project.assets().isScene(scene));
        assertTrue(project.assets().folders().contains(directory.resolve("scenes")));
        assertEquals(project.assets().root(), directory);
    }

    @Test
    void aSceneFileOpensInsideTheProjectThatRegistersIt() throws Exception {
        Path file = projectWithScene(directory, "scenes", "world");

        ProjectRepository project = loader.load(directory.resolve("scenes/world.gltf"));

        assertEquals(file, project.projectFile());
    }

    @Test
    void aNewProjectOpensWithNoScene() throws Exception {
        Path file = writer.createProject(directory);

        ProjectRepository project = loader.load(file);

        assertEquals(directory.resolve(ProjectConfigStore.FILE_NAME), file);
        assertTrue(project.scene().isNone());
        assertTrue(project.assets().scenes().isEmpty());
        assertTrue(Files.isRegularFile(directory.resolve(ProjectTemplates.ARCHETYPES_FILE)));
        assertTrue(Files.isDirectory(directory.resolve("assets")));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".gltf")));
        }
    }

    @Test
    void aNewProjectsScenesFolderIsBrowsableBeforeItHoldsAScene() throws Exception {
        Path file = writer.createProject(directory, "levels/main");

        ProjectAssets assets = loader.load(file).assets();

        assertEquals(directory.resolve("levels/main"), assets.scenesFolder());
        assertTrue(Files.isDirectory(directory.resolve("levels/main")));
        assertTrue(assets.folders().contains(directory.resolve("levels/main")));
        assertEquals("levels/main", mapper.readTree(file.toFile()).get("scenesFolder").textValue());
    }

    @Test
    void anEmptyScenesFolderKeepsScenesInTheProjectFolder() throws Exception {
        Path file = writer.createProject(directory, "");

        ProjectAssets assets = loader.load(file).assets();

        assertEquals(directory, assets.scenesFolder());
        assertFalse(assets.folders().contains(directory));
        assertEquals(".", mapper.readTree(file.toFile()).get("scenesFolder").textValue());
    }

    @Test
    void registeringAScenesKeepsTheScenesFolder() throws Exception {
        Path file = writer.createProject(directory, "levels");

        writer.createScene(file, "elsewhere", "one");

        assertEquals(directory.resolve("levels"), loader.load(file).assets().scenesFolder());
    }

    @Test
    void aProjectWhoseScenesAreMissingOpensWithNoScene() throws Exception {
        Path file = projectWithScene(directory, "", "level");
        Files.delete(directory.resolve("level.gltf"));

        assertTrue(loader.load(file).scene().isNone());
    }

    @Test
    void aProjectIsNotCreatedOverAnother() throws Exception {
        Path file = writer.createProject(directory);
        String config = Files.readString(file);

        assertThrows(FileAlreadyExistsException.class, () -> writer.createProject(directory));
        assertEquals(config, Files.readString(file));
    }

    @Test
    void aNewSceneIsRegisteredAndItsFolderBrowsable() throws Exception {
        Path file = writer.createProject(directory);

        Path scene = writer.createScene(file, "levels/towns", "harbor.gltf");

        assertEquals(directory.resolve("levels/towns/harbor.gltf"), scene);
        assertEquals(ProjectTemplates.MINIMAL_GLTF, Files.readString(scene));
        ProjectRepository project = loader.load(file);
        assertEquals(scene, project.filePath().unwrap());
        assertTrue(project.assets().isScene(scene));
        assertTrue(project.assets().folders().contains(directory.resolve("levels/towns")));

        // A second scene next to it needs no new folder
        Path second = writer.createScene(file, "levels/towns", "market");
        ProjectAssets assets = loader.load(file).assets();
        assertTrue(assets.isScene(second));
        assertEquals(1, assets.folders().stream().filter(directory.resolve("levels/towns")::equals).count());
    }

    @Test
    void aSceneIsNotCreatedOverAFileOrOutsideTheProject() throws Exception {
        Path file = writer.createProject(directory);
        Path scene = writer.createScene(file, "", "level");
        String config = Files.readString(file);

        assertThrows(FileAlreadyExistsException.class, () -> writer.createScene(file, "", "level"));
        for (String bad : List.of("", " ", "..", "a/b", "a\\b")) {
            assertThrows(IOException.class, () -> writer.createScene(file, "", bad), bad);
        }
        assertThrows(IOException.class, () -> writer.createScene(file, "../outside", "level"));
        assertEquals(config, Files.readString(file));
        assertEquals(ProjectTemplates.MINIMAL_GLTF, Files.readString(scene));
        assertFalse(Files.exists(directory.getParent().resolve("outside")));
    }

    @Test
    void aNewSceneNextToABareSceneStartsAProjectFileForBoth() throws Exception {
        Path bare = directory.resolve("old.gltf");
        Files.writeString(bare, ProjectTemplates.MINIMAL_GLTF);

        Path scene = writer.createScene(bare, "", "new");

        ProjectRepository project = loader.load(scene);
        assertEquals(directory.resolve(ProjectConfigStore.FILE_NAME), project.projectFile());
        assertTrue(project.assets().isScene(bare));
        assertTrue(project.assets().isScene(scene));
        assertTrue(project.assets().visible(directory.resolve("anything/deeper.gltf"), false));
    }

    @Test
    void scenesInAnyFolderShareTheProjectsArchetypes() throws Exception {
        Path file = projectWithScene(directory, "scenes", "world");
        ProjectRepository world = loader.load(file);
        world.archetypeCatalog().getRegistry().add(new ArchetypeDefinition("door"));
        writer.saveArchetypes(world.projectFile(), world.archetypeCatalog().getRegistry());
        Path town = directory.resolve("scenes/locations/town.gltf");
        Files.createDirectories(town.getParent());
        Files.copy(world.filePath().unwrap(), town);

        ProjectRepository opened = loader.load(new SceneScope(town, file, world.assets()));

        assertTrue(opened.archetypeCatalog().find("door").isSome());
    }

    @Test
    void archetypesNextToAnOlderProjectsSceneAreReadUntilSaved() throws Exception {
        Path file = projectWithScene(directory, "scenes", "world");
        Files.delete(directory.resolve(ProjectTemplates.ARCHETYPES_FILE));
        Path scene = directory.resolve("scenes/world.gltf");
        var old = new org.xast.kebab.archetype.definition.ArchetypeRegistry();
        old.add(new ArchetypeDefinition("legacy"));
        writer.saveArchetypes(scene, old);

        ProjectRepository project = loader.load(file);
        assertTrue(project.archetypeCatalog().find("legacy").isSome());

        writer.saveArchetypes(project.projectFile(), project.archetypeCatalog().getRegistry());
        assertTrue(Files.readString(directory.resolve(ProjectTemplates.ARCHETYPES_FILE)).contains("legacy"));
    }

    private Path projectWithScene(Path dir, String folder, String name) throws IOException {
        Path project = writer.createProject(dir);
        writer.createScene(project, folder, name);
        return project;
    }

    // Three float positions in a base64 buffer: the smallest drawable glTF
    private static final String TRIANGLE_GLTF = "{\"asset\":{\"version\":\"2.0\"},"
        + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
        + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,"
        + java.util.Base64.getEncoder().encodeToString(triangleBytes()) + "\",\"byteLength\":36}],"
        + "\"bufferViews\":[{\"buffer\":0,\"byteLength\":36}],"
        + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}]}";

    private static byte[] triangleBytes() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(36).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }
}
