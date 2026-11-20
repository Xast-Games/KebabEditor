package org.xast.kebab.project;

import java.nio.file.Path;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.PrefabResolver;

/** @param filePath the open scene; projectFile is its kebab-project.json, or the scene for a project without one */
public record ProjectRepository(
    Path filePath,
    Scene sceneData,
    ArchetypeCatalog archetypeCatalog,
    ModelResolver modelResolver,
    PrefabResolver prefabResolver,
    ProjectAssets assets,
    Path projectFile
) {

    public ProjectRepository withAssets(ProjectAssets newAssets) {
        return new ProjectRepository(
            filePath, sceneData, archetypeCatalog, modelResolver, prefabResolver, newAssets, projectFile
        );
    }
}
