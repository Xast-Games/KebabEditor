package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.utils.Maybe;

public interface ProjectWriter {

    /**
     * Creates a project in dir: kebab-project.json and archetypes.json, with no scenes yet.
     *
     * @param scenesFolder where new scenes go by default, relative to dir; empty for dir itself
     * @return the project's kebab-project.json
     */
    Path createProject(Path dir, String scenesFolder) throws IOException;

    default Path createProject(Path dir) throws IOException {
        return createProject(dir, ProjectConfig.DEFAULT_SCENES_FOLDER);
    }

    /**
     * Creates an empty scene and registers it in the project.
     *
     * @param projectFile the kebab-project.json, or the scene of a project without one
     * @param folder      relative to the asset root; empty puts the scene in the root itself
     * @param name        the file name, with or without the .gltf extension
     * @return the new scene file
     */
    Path createScene(Path projectFile, String folder, String name) throws IOException;

    void saveScene(Path path, Scene sceneData) throws SceneFormatException, IOException;

    /**
     * @param projectFile the kebab-project.json, or the scene of a project without one
     * @param scene        the open scene, if any
     * @param scenesFolder where new scenes go by default, relative to root; empty for root itself
     * @return the project with the kebab-project.json that now holds its scope
     */
    SceneScope saveAssetScope(Path projectFile, Maybe<Path> scene, Path root, List<String> folders,
        List<String> scenes, String scenesFolder) throws IOException;

    /** @param projectFile the kebab-project.json, or the scene of a project without one */
    void saveArchetypes(Path projectFile, ArchetypeRegistry registry) throws IOException;
}
