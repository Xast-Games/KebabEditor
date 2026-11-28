package org.xast.kebab.project;

import java.util.List;

/**
 * Paths are relative to assetRoot; assetRoot is relative to the configuration directory.
 *
 * @param scenesFolder where new scenes go by default; "." is the asset root itself
 */
public record ProjectConfig(
    int version,
    String assetRoot,
    List<String> assetFolders,
    List<String> scenes,
    String scenesFolder
) {

    /** What a project made before scenesFolder existed uses. */
    public static final String DEFAULT_SCENES_FOLDER = "scenes";

    public ProjectConfig {
        assetFolders = List.copyOf(assetFolders);
        scenes = List.copyOf(scenes);
    }

    public ProjectConfig(int version, String assetRoot, List<String> assetFolders, List<String> scenes) {
        this(version, assetRoot, assetFolders, scenes, DEFAULT_SCENES_FOLDER);
    }
}
