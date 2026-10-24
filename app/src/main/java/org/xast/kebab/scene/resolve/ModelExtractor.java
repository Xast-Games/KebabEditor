package org.xast.kebab.scene.resolve;

import java.nio.file.Path;
import java.util.List;

import org.xast.kebab.model.ModelPart;
import org.xast.kebab.scene.error.ModelLoadException;

public interface ModelExtractor {

    boolean supports(Path file);

    List<ModelPart> extract(Path file, Path assetRoot) throws ModelLoadException;
}
