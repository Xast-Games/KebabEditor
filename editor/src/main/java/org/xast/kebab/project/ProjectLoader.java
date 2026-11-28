package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Path;

import org.xast.kebab.scene.error.SceneException;

public interface ProjectLoader {

    /** Which scene opening this file loads: a kebab-project.json's first scene, or the scene file itself. */
    SceneScope scopeFor(Path file) throws IOException;

    ProjectRepository load(SceneScope scope) throws SceneException, IOException;

    default ProjectRepository load(Path file) throws SceneException, IOException {
        return load(scopeFor(file));
    }
}
