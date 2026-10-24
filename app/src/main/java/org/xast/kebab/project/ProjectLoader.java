package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Path;

import org.xast.kebab.scene.error.SceneException;

public interface ProjectLoader {

    ProjectRepository load(Path file) throws SceneException, IOException;
}
