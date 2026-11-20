package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.xast.kebab.logging.LogService;
import org.xast.kebab.utils.AtomicFiles;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ProjectConfigStore {

    public static final String FILE_NAME = "kebab-project.json";
    private static final String ASSETS_DIR = "assets";
    private static final int VERSION = 1;

    private final ObjectMapper mapper;

    public ProjectConfigStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private record Located(Path directory, ProjectConfig config, ProjectAssets assets) {}

    public static boolean isProjectFile(Path file) {
        Path name = file.getFileName();
        return name != null && name.toString().equals(FILE_NAME);
    }

    /**
     * What opening a file means: a kebab-project.json opens its first registered scene,
     * and a scene file opens inside the project that registers it.
     */
    public SceneScope scopeFor(Path file) throws IOException {
        Path path = file.toAbsolutePath().normalize();
        if (isProjectFile(path)) {
            ProjectAssets assets = resolve(path.getParent(), read(path));
            return new SceneScope(mainScene(assets, path), path, assets);
        }
        Maybe<Located> located = locate(path);
        if (located.isSome()) {
            Located found = located.unwrap();
            return new SceneScope(path, found.directory().resolve(FILE_NAME), found.assets());
        }
        Path dir = path.getParent();
        return new SceneScope(path, path, new ProjectAssets(dir, List.of(dir), List.of(path)));
    }

    // The first registered scene that exists is the one a project opens with
    private static Path mainScene(ProjectAssets assets, Path projectFile) throws IOException {
        for (Path scene : assets.scenes()) {
            if (Files.isRegularFile(scene)) {
                return scene;
            }
        }
        throw new IOException("The project registers no existing scene: " + projectFile);
    }

    public ProjectAssets loadForScene(Path scene) throws IOException {
        return scopeFor(scene).assets();
    }

    // The config lives in the asset root when it moves; a deeper config that would shadow the new one is removed
    public ProjectAssets saveScope(Path scene, Path root, List<String> folders, List<String> scenes)
        throws IOException {
        Path file = scene.toAbsolutePath().normalize();
        Path newRoot = root.toAbsolutePath().normalize();
        if (!file.startsWith(newRoot)) {
            throw new IOException("The asset root must contain the open scene: " + newRoot);
        }
        Maybe<Located> located = locate(file);
        boolean keepConfig = located.map(found -> found.assets().root().equals(newRoot)).unwrapOr(false);
        Path directory = keepConfig ? located.unwrap().directory() : newRoot;
        String assetRoot = keepConfig ? located.unwrap().config().assetRoot() : ".";

        List<String> registered = new ArrayList<>(scenes);
        ProjectAssets assets = resolve(directory, new ProjectConfig(VERSION, assetRoot, folders, registered));
        if (!assets.isScene(file)) {
            registered.add(newRoot.relativize(file).toString().replace('\\', '/'));
        }
        ProjectConfig config = new ProjectConfig(VERSION, assetRoot, folders, registered);
        assets = resolve(directory, config);

        Path configFile = directory.resolve(FILE_NAME);
        ProjectAssets.requireInside(directory, configFile);
        AtomicFiles.write(configFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config));
        located.ifSome(found -> deleteShadowing(found.directory(), directory));
        return assets;
    }

    private static void deleteShadowing(Path oldDirectory, Path newDirectory) {
        if (oldDirectory.equals(newDirectory) || !oldDirectory.startsWith(newDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(oldDirectory.resolve(FILE_NAME));
        } catch (IOException e) {
            LogService.get().warn("Could not remove the replaced " + FILE_NAME + " in " + oldDirectory);
        }
    }

    private Maybe<Located> locate(Path file) throws IOException {
        for (Path dir = file.getParent(); dir != null; dir = dir.getParent()) {
            Path configFile = dir.resolve(FILE_NAME);
            if (!Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            ProjectConfig config = read(configFile);
            ProjectAssets assets = resolve(dir, config);
            if (assets.isScene(file)) {
                return Maybe.some(new Located(dir, config, assets));
            }
        }
        return Maybe.none();
    }

    public ProjectConfig read(Path file) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        ProjectAssets.requireInside(directory, file);
        JsonNode node = mapper.readTree(file.toFile());
        JsonNode version = node == null ? null : node.path("version");
        boolean supported = node != null
            && node.isObject()
            && version.isIntegralNumber()
            && version.canConvertToInt()
            && version.intValue() == VERSION;
        if (!supported) {
            throw new IOException("Unsupported or missing project configuration version (expected 1): " + file);
        }
        if (!node.path("assetRoot").isTextual()) {
            throw new IOException("assetRoot must be a string");
        }
        ProjectConfig config = new ProjectConfig(
            VERSION,
            node.get("assetRoot").textValue(),
            strings(node, "assetFolders"),
            strings(node, "scenes")
        );
        resolve(directory, config);
        return config;
    }

    public ProjectAssets resolve(Path directory, ProjectConfig config) throws IOException {
        if (config.version() != VERSION) {
            throw new IOException("Unsupported project configuration version: " + config.version());
        }
        Path root = ProjectAssets.resolveInside(directory.toAbsolutePath().normalize(), config.assetRoot());
        requireDirectoryIfPresent(root, "assetRoot");
        List<Path> folders = new ArrayList<>();
        for (String folder : config.assetFolders()) {
            Path path = ProjectAssets.resolveInside(root, folder);
            requireDirectoryIfPresent(path, "Asset folder");
            folders.add(path);
        }
        List<Path> scenes = new ArrayList<>();
        for (String scene : config.scenes()) {
            scenes.add(ProjectAssets.resolveInside(root, scene));
        }
        return new ProjectAssets(root, folders, scenes);
    }

    // An existing config is never replaced: it has to register the new scene itself
    public void prepareNewScene(Path projectDir, Path scene) throws IOException {
        Path dir = projectDir.toAbsolutePath().normalize();
        Path file = scene.toAbsolutePath().normalize();
        ProjectAssets.requireInside(dir, file);
        Path configFile = dir.resolve(FILE_NAME);
        Path assets = ProjectAssets.resolveInside(dir, ASSETS_DIR);
        requireDirectoryIfPresent(assets, ASSETS_DIR);
        if (Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            if (!resolve(dir, read(configFile)).isScene(file)) {
                throw new IOException("Register " + file.getFileName() + " in the existing " + FILE_NAME
                    + " before creating it");
            }
            Files.createDirectories(assets);
            return;
        }
        // A scenes folder is browsable as a whole, so the scenes and prefabs added to it later show up
        List<String> folders = new ArrayList<>(List.of(ASSETS_DIR, ModelImporter.MODELS_DIR));
        Path sceneDir = file.getParent();
        if (!sceneDir.equals(dir)) {
            folders.add(relative(dir, sceneDir));
        }
        ProjectConfig config = new ProjectConfig(VERSION, ".", folders, List.of(relative(dir, file)));
        Files.createDirectories(assets);
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
        Files.write(configFile, json, StandardOpenOption.CREATE_NEW);
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void requireDirectoryIfPresent(Path path, String what) throws IOException {
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new IOException(what + " is not a directory: " + path);
        }
    }

    private static List<String> strings(JsonNode node, String field) throws IOException {
        if (!node.path(field).isArray()) {
            throw new IOException(field + " must be an array of paths");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : node.get(field)) {
            if (!value.isTextual()) {
                throw new IOException(field + " must contain only strings");
            }
            result.add(value.textValue());
        }
        return result;
    }
}
