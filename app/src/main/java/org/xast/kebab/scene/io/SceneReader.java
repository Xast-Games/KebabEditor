package org.xast.kebab.scene.io;

import java.io.IOException;
import java.nio.file.Path;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.scene.model.Scene;

public interface SceneReader {

    Scene read(Path file, ArchetypeCatalog catalog) throws SceneException, IOException;
}
