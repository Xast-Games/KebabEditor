package org.xast.kebab.project;

import java.nio.file.Path;

/**
 * A scene to load and the project it belongs to.
 *
 * @param scene       the glTF scene file
 * @param projectFile the kebab-project.json that registers the scene, or the scene itself for a project without one
 * @param assets      the project's asset scope
 */
public record SceneScope(Path scene, Path projectFile, ProjectAssets assets) {

    public SceneScope {
        scene = scene.toAbsolutePath().normalize();
        projectFile = projectFile.toAbsolutePath().normalize();
    }
}
