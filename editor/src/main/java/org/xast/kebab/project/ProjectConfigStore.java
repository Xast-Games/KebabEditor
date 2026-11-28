package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
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
     * What opening a file means: a kebab-project.json opens its first registered scene, or no
     * scene when it has none yet, and a scene file opens inside the project that registers it.
     */
    public SceneScope scopeFor(Path file) throws IOException {
        Path path = file.toAbsolutePath().normalize();
        if (isProjectFile(path)) {
            ProjectAssets assets = resolve(path.getParent(), read(path));
            return new SceneScope(mainScene(assets), path, assets);
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
    private static Maybe<Path> mainScene(ProjectAssets assets) {
        return Maybe.fromOptional(assets.scenes().stream().filter(Files::isRegularFile).findFirst());
    }

    /** The asset scope of a kebab-project.json, or of a bare scene for a project without one. */
    public ProjectAssets assetsOf(Path projectFile) throws IOException {
        return scopeFor(projectFile).assets();
    }

    public ProjectAssets loadForScene(Path scene) throws IOException {
        return scopeFor(scene).assets();
    }

    /**
     * Writes the asset scope of a project. The config lives in the asset root when it moves,
     * and a deeper config that would shadow the new one is removed.
     *
     * @param projectFile the kebab-project.json, or the scene of a project without one
     * @param scene       the open scene, which the new root must contain and which stays registered
     * @return the project as it is now, with the kebab-project.json that was written
     */
    public SceneScope saveScope(Path projectFile, Maybe<Path> scene, Path root, List<String> folders,
        List<String> scenes, String scenesFolder) throws IOException {
        Maybe<Path> file = scene.map(path -> path.toAbsolutePath().normalize());
        Path newRoot = root.toAbsolutePath().normalize();
        if (file.isSomeAnd(path -> !path.startsWith(newRoot))) {
            throw new IOException("The asset root must contain the open scene: " + newRoot);
        }
        Maybe<Located> located = located(projectFile);
        boolean keepConfig = located.map(found -> found.assets().root().equals(newRoot)).unwrapOr(false);
        Path directory = keepConfig ? located.unwrap().directory() : newRoot;
        String assetRoot = keepConfig ? located.unwrap().config().assetRoot() : ".";

        String newScenesFolder = folderName(newRoot, ProjectAssets.resolveFolder(newRoot, scenesFolder));
        List<String> registered = new ArrayList<>(scenes);
        ProjectAssets assets = resolve(directory,
            new ProjectConfig(VERSION, assetRoot, folders, registered, newScenesFolder));
        if (file.isSomeAnd(path -> !assets.isScene(path))) {
            registered.add(relative(newRoot, file.unwrap()));
        }
        ProjectConfig config = new ProjectConfig(VERSION, assetRoot, folders, registered, newScenesFolder);

        Path configFile = directory.resolve(FILE_NAME);
        ProjectAssets.requireInside(directory, configFile);
        AtomicFiles.write(configFile, json(config));
        located.ifSome(found -> deleteShadowing(found.directory(), directory));
        return new SceneScope(file, configFile, resolve(directory, config));
    }

    private Maybe<Located> located(Path projectFile) throws IOException {
        Path file = projectFile.toAbsolutePath().normalize();
        if (!isProjectFile(file)) {
            return Maybe.none();
        }
        ProjectConfig config = read(file);
        return Maybe.some(new Located(file.getParent(), config, resolve(file.getParent(), config)));
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
        // Projects made before scenesFolder existed get the folder new projects default to
        JsonNode scenesFolder = node.path("scenesFolder");
        if (!scenesFolder.isMissingNode() && !scenesFolder.isTextual()) {
            throw new IOException("scenesFolder must be a string");
        }
        ProjectConfig config = new ProjectConfig(
            VERSION,
            node.get("assetRoot").textValue(),
            strings(node, "assetFolders"),
            strings(node, "scenes"),
            scenesFolder.isTextual() ? scenesFolder.textValue() : ProjectConfig.DEFAULT_SCENES_FOLDER
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
        Path scenesFolder = ProjectAssets.resolveFolder(root, config.scenesFolder());
        requireDirectoryIfPresent(scenesFolder, "scenesFolder");
        return new ProjectAssets(root, folders, scenes, scenesFolder);
    }

    /**
     * Starts a project in projectDir with no scenes; an existing config is never replaced.
     *
     * @param scenesFolder where new scenes go by default, relative to projectDir; empty for projectDir itself.
     *                     It is browsable from the start, so it shows up before it holds a scene.
     */
    public Path createProject(Path projectDir, String scenesFolder) throws IOException {
        Path dir = projectDir.toAbsolutePath().normalize();
        Path configFile = dir.resolve(FILE_NAME);
        if (Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(configFile.toString(), null, "A project already exists here");
        }
        Path assets = ProjectAssets.resolveInside(dir, ASSETS_DIR);
        requireDirectoryIfPresent(assets, ASSETS_DIR);
        Path scenesDir = ProjectAssets.resolveFolder(dir, scenesFolder);
        requireDirectoryIfPresent(scenesDir, "Scenes folder");

        String scenes = folderName(dir, scenesDir);
        List<String> folders = new ArrayList<>(List.of(ASSETS_DIR));
        if (!scenesDir.equals(dir) && !folders.contains(scenes)) {
            folders.add(scenes);
        }
        ProjectConfig config = new ProjectConfig(VERSION, ".", folders, List.of(), scenes);
        Files.createDirectories(assets);
        Files.createDirectories(scenesDir);
        Files.write(configFile, json(config), StandardOpenOption.CREATE_NEW);
        return configFile;
    }

    /**
     * Registers a scene file that was just made for the project. Its folder becomes browsable,
     * so the scenes and prefabs added next to it show up too. A project without a
     * kebab-project.json gets one next to its scene, registering both scenes.
     *
     * @param projectFile the kebab-project.json, or the scene of a project without one
     * @return the kebab-project.json that registers the scene
     */
    public Path registerScene(Path projectFile, Path scene) throws IOException {
        Path file = scene.toAbsolutePath().normalize();
        Maybe<Located> located = located(projectFile);
        ProjectAssets assets = located.isSome() ? located.unwrap().assets() : assetsOf(projectFile);
        Path root = assets.root();
        ProjectAssets.requireInside(root, file);

        Path sceneDir = file.getParent();
        boolean browsable = sceneDir.equals(root) || assets.folders().stream().anyMatch(sceneDir::startsWith);

        if (located.isSome()) {
            ProjectConfig config = located.unwrap().config();
            List<String> folders = new ArrayList<>(config.assetFolders());
            if (!browsable) {
                folders.add(relative(root, sceneDir));
            }
            List<String> scenes = new ArrayList<>(config.scenes());
            if (!assets.isScene(file)) {
                scenes.add(relative(root, file));
            }
            Path configFile = located.unwrap().directory().resolve(FILE_NAME);
            AtomicFiles.write(configFile,
                json(new ProjectConfig(VERSION, config.assetRoot(), folders, scenes, config.scenesFolder())));
            return configFile;
        }

        // "." keeps the whole folder browsable, as it was for the bare scene
        List<String> scenes = new ArrayList<>();
        for (Path existing : assets.scenes()) {
            scenes.add(relative(root, existing));
        }
        scenes.add(relative(root, file));
        Path configFile = root.resolve(FILE_NAME);
        ProjectConfig config = new ProjectConfig(VERSION, ".", List.of("."), scenes, folderName(root, assets.scenesFolder()));
        Files.write(configFile, json(config), StandardOpenOption.CREATE_NEW);
        return configFile;
    }

    private byte[] json(ProjectConfig config) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
    }

    // The root itself is written as "."
    private static String folderName(Path root, Path folder) {
        String name = relative(root, folder);
        return name.isEmpty() ? "." : name;
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
