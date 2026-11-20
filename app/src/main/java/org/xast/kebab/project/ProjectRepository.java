package org.xast.kebab.project;

import java.nio.file.Path;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.utils.Maybe;

/**
 * An open project and the scene being edited in it, if any.
 *
 * @param projectFile its kebab-project.json, or the scene for a project without one
 */
public record ProjectRepository(
    Maybe<OpenScene> scene,
    ArchetypeCatalog archetypeCatalog,
    ModelResolver modelResolver,
    PrefabResolver prefabResolver,
    ProjectAssets assets,
    Path projectFile
) {

    /** A scene file and what was read from it. */
    public record OpenScene(Path file, Scene data) {}

    public Maybe<Path> filePath() {
        return scene.map(OpenScene::file);
    }

    public Maybe<Scene> sceneData() {
        return scene.map(OpenScene::data);
    }

    public ProjectRepository withAssets(ProjectAssets newAssets, Path newProjectFile) {
        return new ProjectRepository(
            scene, archetypeCatalog, modelResolver, prefabResolver, newAssets, newProjectFile
        );
    }
}
