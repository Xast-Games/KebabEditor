package org.xast.kebab.scene.io;

import java.io.IOException;
import java.nio.file.Path;

import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.model.Scene;

public interface SceneWriter {

    void write(Scene scene, Path file) throws SceneFormatException, IOException;
}
