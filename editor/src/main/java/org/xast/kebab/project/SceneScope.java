package org.xast.kebab.project;

import java.nio.file.Path;

import org.xast.kebab.utils.Maybe;

/**
 * A project to load and the scene to open in it.
 *
 * @param scene       the glTF scene file, or none for a project that has no scene yet
 * @param projectFile the kebab-project.json that registers the scene, or the scene itself for a project without one
 * @param assets      the project's asset scope
 */
public record SceneScope(Maybe<Path> scene, Path projectFile, ProjectAssets assets) {

    public SceneScope {
        scene = scene.map(file -> file.toAbsolutePath().normalize());
        projectFile = projectFile.toAbsolutePath().normalize();
    }

    public SceneScope(Path scene, Path projectFile, ProjectAssets assets) {
        this(Maybe.some(scene), projectFile, assets);
    }
}
