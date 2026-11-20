package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.archetype.definition.ArchetypeRegistryJson;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.io.SceneWriter;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.utils.AtomicFiles;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileProjectWriter implements ProjectWriter {

    private final SceneWriter sceneWriter;
    private final ObjectMapper mapper;

    @Override
    public Path createProject(Path dir, String scenesFolder) throws IOException {
        Files.createDirectories(dir);
        Path project = new ProjectConfigStore(mapper).createProject(dir, scenesFolder);

        Path archetypes = ProjectTemplates.archetypesFileFor(project);
        if (!Files.exists(archetypes)) {
            Files.writeString(archetypes, ProjectTemplates.EMPTY_ARCHETYPES, StandardOpenOption.CREATE_NEW);
        }

        return project;
    }

    @Override
    public Path createScene(Path projectFile, String folder, String name) throws IOException {
        String fileName = sceneFileName(name);
        ProjectConfigStore store = new ProjectConfigStore(mapper);
        Path root = store.assetsOf(projectFile).root();
        Path sceneDir = ProjectAssets.resolveFolder(root, folder);

        Path scene = sceneDir.resolve(fileName);
        ProjectAssets.requireInside(root, scene);
        if (Files.exists(scene, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(scene.toString());
        }
        Files.createDirectories(sceneDir);
        Files.writeString(scene, ProjectTemplates.MINIMAL_GLTF, StandardOpenOption.CREATE_NEW);
        try {
            store.registerScene(projectFile, scene);
        } catch (IOException e) {
            Files.deleteIfExists(scene);
            throw e;
        }
        return scene;
    }

    // A plain file name; the .gltf extension is added unless it was typed already
    private static String sceneFileName(String name) throws IOException {
        String stripped = name.strip();
        if (stripped.toLowerCase(Locale.ROOT).endsWith(".gltf")) {
            stripped = stripped.substring(0, stripped.length() - ".gltf".length()).strip();
        }
        boolean invalid = stripped.isEmpty()
            || stripped.equals(".")
            || stripped.equals("..")
            || stripped.contains("/")
            || stripped.contains("\\");
        if (invalid) {
            throw new IOException("Not a valid scene name: '" + name + "'");
        }
        return stripped + ".gltf";
    }

    @Override
    public void saveScene(Path path, Scene sceneData) throws SceneFormatException, IOException {
        sceneWriter.write(sceneData, path);
    }

    @Override
    public SceneScope saveAssetScope(Path projectFile, Maybe<Path> scene, Path root, List<String> folders,
        List<String> scenes, String scenesFolder) throws IOException {
        return new ProjectConfigStore(mapper).saveScope(projectFile, scene, root, folders, scenes, scenesFolder);
    }

    @Override
    public void saveArchetypes(Path projectFile, ArchetypeRegistry registry) throws IOException {
        AtomicFiles.write(
            ProjectTemplates.archetypesFileFor(projectFile),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(ArchetypeRegistryJson.from(registry))
        );
    }
}
