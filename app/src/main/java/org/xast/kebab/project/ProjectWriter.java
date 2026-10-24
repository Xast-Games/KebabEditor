package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.model.Scene;

public interface ProjectWriter {

    Path createProject(Path dir, String name) throws IOException;

    void saveScene(Path path, Scene sceneData) throws SceneFormatException, IOException;

    ProjectAssets saveAssetScope(Path scene, Path root, List<String> folders, List<String> scenes)
        throws IOException;

    void saveArchetypes(Path scene, ArchetypeRegistry registry) throws IOException;
}
