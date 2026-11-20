package org.xast.kebab.project;

import java.nio.file.Path;

public class ProjectTemplates {
    
    public static final String ARCHETYPES_FILE = "archetypes.json";

    /**
     * One archetypes file serves the whole project, so its scenes and the prefabs they
     * reference resolve the same archetypes, whatever folder they are in.
     *
     * @param projectFile the kebab-project.json, or the scene of a project without one
     */
    public static Path archetypesFileFor(Path projectFile) {
        return projectFile.toAbsolutePath().resolveSibling(ARCHETYPES_FILE);
    }

    public static final String MINIMAL_GLTF = """
        {
          "asset": {
            "version": "2.0"
          }
        }
        """;

    public static final String EMPTY_ARCHETYPES = """
        {
          "archetypes": []
        }
        """;
}
