package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.model.Scene;

public interface ProjectWriter {

    /**
     * Creates a project in dir: kebab-project.json and archetypes.json, with a first scene in scenesFolder.
     *
     * @param scenesFolder relative to dir; empty puts the scene in dir itself
     * @return the project's kebab-project.json
     */
    Path createProject(Path dir, String scenesFolder, String name) throws IOException;

    default Path createProject(Path dir, String name) throws IOException {
        return createProject(dir, "", name);
    }

    void saveScene(Path path, Scene sceneData) throws SceneFormatException, IOException;

    ProjectAssets saveAssetScope(Path scene, Path root, List<String> folders, List<String> scenes)
        throws IOException;

    /** @param projectFile the kebab-project.json, or the scene of a project without one */
    void saveArchetypes(Path projectFile, ArchetypeRegistry registry) throws IOException;
}
