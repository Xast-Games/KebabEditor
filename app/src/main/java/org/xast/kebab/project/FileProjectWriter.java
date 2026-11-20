package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.archetype.definition.ArchetypeRegistryJson;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.io.SceneWriter;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.utils.AtomicFiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileProjectWriter implements ProjectWriter {

    private final SceneWriter sceneWriter;
    private final ObjectMapper mapper;

    @Override
    public Path createProject(Path dir, String scenesFolder, String name) throws IOException {
        Files.createDirectories(dir);
        Path projectDir = dir.toAbsolutePath().normalize();
        String folder = scenesFolder.strip().replace('\\', '/');
        Path sceneDir = folder.isEmpty() || folder.equals(".")
            ? projectDir
            : ProjectAssets.resolveInside(projectDir, folder);

        Path scene = sceneDir.resolve(name + ".gltf");
        ProjectAssets.requireInside(sceneDir, scene);
        if (Files.exists(scene, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(scene.toString());
        }
        new ProjectConfigStore(mapper).prepareNewScene(projectDir, scene);
        Files.createDirectories(sceneDir);
        Files.writeString(scene, ProjectTemplates.MINIMAL_GLTF, StandardOpenOption.CREATE_NEW);

        Path project = projectDir.resolve(ProjectConfigStore.FILE_NAME);
        Path archetypes = ProjectTemplates.archetypesFileFor(project);
        if (!Files.exists(archetypes)) {
            Files.writeString(archetypes, ProjectTemplates.EMPTY_ARCHETYPES, StandardOpenOption.CREATE_NEW);
        }

        return project;
    }

    @Override
    public void saveScene(Path path, Scene sceneData) throws SceneFormatException, IOException {
        sceneWriter.write(sceneData, path);
    }

    @Override
    public ProjectAssets saveAssetScope(Path scene, Path root, List<String> folders, List<String> scenes)
        throws IOException {
        return new ProjectConfigStore(mapper).saveScope(scene, root, folders, scenes);
    }

    @Override
    public void saveArchetypes(Path projectFile, ArchetypeRegistry registry) throws IOException {
        AtomicFiles.write(
            ProjectTemplates.archetypesFileFor(projectFile),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(ArchetypeRegistryJson.from(registry))
        );
    }
}
