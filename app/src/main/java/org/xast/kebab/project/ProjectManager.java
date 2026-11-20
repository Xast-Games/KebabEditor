package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.utils.Maybe;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProjectManager {

    private final List<Runnable> listeners = new ArrayList<>();
    private final RecentProjects recentProjects;
    private final ProjectWriter writer;
    private final ProjectLoader loader;

    private Maybe<ProjectRepository> repository = Maybe.none();
    private byte[] savedSceneHash;

    /**
     * Creates a project with no scenes and opens it.
     *
     * @param scenesFolder where new scenes go by default, relative to dir; empty for dir itself
     */
    public void create(Path dir, String scenesFolder) throws SceneException, IOException {
        open(writer.createProject(dir, scenesFolder));
    }

    /**
     * Creates an empty scene in the open project and opens it; unsaved edits of the current scene are dropped.
     *
     * @param folder relative to the asset root; empty puts the scene in the root itself
     */
    public void createScene(String folder, String name) throws SceneException, IOException {
        ProjectRepository current = repository.expect("No project is open");
        SceneScope scope = loader.scopeFor(writer.createScene(current.projectFile(), folder, name));
        show(scope);
        // A project without a kebab-project.json gets one for its second scene
        if (!scope.projectFile().equals(current.projectFile())) {
            recentProjects.add(scope.projectFile());
        }
    }

    /**
     * Opens a kebab-project.json at its first scene, or with no scene when it has none, or a bare
     * scene file with the project that registers it.
     */
    public void open(Path file) throws SceneException, IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + file);
        }

        SceneScope scope = loader.scopeFor(file);
        show(scope);
        recentProjects.add(scope.projectFile());
    }

    /** Switches to another scene of the open project, which keeps its project file and asset scope. */
    public void openScene(Path scene) throws SceneException, IOException {
        ProjectRepository current = repository.expect("No project is open");
        Path file = scene.toAbsolutePath().normalize();
        ProjectAssets.requireInside(current.assets().root(), file);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + file);
        }

        show(new SceneScope(file, current.projectFile(), current.assets()));
    }

    // The hash is taken before reading, so an edit on disk during loading is caught on the next save
    private void show(SceneScope scope) throws SceneException, IOException {
        byte[] hash = scope.scene().isSome() ? sceneHash(scope.scene().unwrap()) : null;
        ProjectRepository loaded = loader.load(scope);
        repository = Maybe.some(loaded);
        savedSceneHash = hash;
        notifyListeners();
    }

    public void close() {
        repository = Maybe.none();
        notifyListeners();
    }

    public boolean isOpen() {
        return repository.isSome();
    }

    public Maybe<Path> currentScene() {
        return repository.andThen(ProjectRepository::filePath);
    }

    public Maybe<Path> projectFile() {
        return repository.map(ProjectRepository::projectFile);
    }

    public List<ArchetypeDefinition> archetypes() {
        return repository
            .map(r -> r.archetypeCatalog()
                .all()
                .stream()
                .sorted(Comparator.comparing(ArchetypeDefinition::getId))
                .toList()
            )
            .unwrapOr(List.of());
    }

    public Maybe<Scene> scene() {
        return repository.andThen(ProjectRepository::sceneData);
    }

    public Maybe<ArchetypeCatalog> catalog() {
        return repository.map(ProjectRepository::archetypeCatalog);
    }

    public Maybe<ModelResolver> modelResolver() {
        return repository.map(ProjectRepository::modelResolver);
    }

    public Maybe<PrefabResolver> prefabResolver() {
        return repository.map(ProjectRepository::prefabResolver);
    }

    public boolean isBuiltin(String archetypeId) {
        return repository
            .map(r -> r.archetypeCatalog().isBuiltin(archetypeId))
            .unwrapOr(false);
    }

    public void addArchetype(ArchetypeDefinition definition) throws IOException {
        if (repository.isNone()) {
            throw new IllegalStateException("No project is open");
        }

        ProjectRepository current = repository.unwrap();
        ArchetypeRegistry registry = current.archetypeCatalog().getRegistry();

        if (current.archetypeCatalog().isBuiltin(definition.getId())) {
            throw new IllegalArgumentException(
                "Archetype '" + definition.getId() + "' is built in and cannot be redefined"
            );
        }

        registry.add(definition);

        try {
            writer.saveArchetypes(current.projectFile(), registry);
        } catch (IOException e) {
            registry.remove(definition);
            throw e;
        }

        current.prefabResolver().invalidateAll();
        notifyListeners();
    }

    public void removeArchetype(ArchetypeDefinition definition) throws IOException {
        if (repository.isNone()) {
            throw new IllegalStateException("No project is open");
        }

        ProjectRepository current = repository.unwrap();
        ArchetypeRegistry registry = current.archetypeCatalog().getRegistry();

        if (current.archetypeCatalog().isBuiltin(definition.getId())) {
            throw new IllegalArgumentException(
                "Archetype '" + definition.getId() + "' is built in and cannot be removed"
            );
        }

        registry.remove(definition);

        try {
            writer.saveArchetypes(current.projectFile(), registry);
        } catch (IOException e) {
            registry.add(definition);
            throw e;
        }

        current.prefabResolver().invalidateAll();
        notifyListeners();
    }

    public void saveArchetypes() throws IOException {
        if (repository.isNone()) {
            throw new IllegalStateException("No project is open");
        }

        ProjectRepository current = repository.unwrap();
        writer.saveArchetypes(current.projectFile(), current.archetypeCatalog().getRegistry());
        current.prefabResolver().invalidateAll();
    }

    public void saveScene() throws SceneFormatException, IOException {
        ProjectRepository current = repository.expect("No project is open");
        ProjectRepository.OpenScene scene = current.scene().expect("No scene is open");
        if (!Arrays.equals(savedSceneHash, sceneHash(scene.file()))) {
            throw new IOException("Scene changed on disk since it was opened; reopen it before saving: "
                + scene.file());
        }
        writer.saveScene(scene.file(), scene.data());
        savedSceneHash = sceneHash(scene.file());
    }

    public void save() throws SceneFormatException, IOException {
        ProjectRepository current = repository.expect("No project is open");
        if (current.scene().isSome()) {
            saveScene();
        }
        writer.saveArchetypes(current.projectFile(), current.archetypeCatalog().getRegistry());
    }

    private static byte[] sceneHash(Path file) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // A moved asset root rebuilds the resolvers, which means reloading the scene from disk
    public void updateAssetScope(Path root, List<String> folders, List<String> scenes, String scenesFolder)
        throws SceneException, IOException {
        ProjectRepository current = repository.expect("No project is open");
        SceneScope saved = writer.saveAssetScope(
            current.projectFile(), current.filePath(), root, folders, scenes, scenesFolder
        );
        if (saved.assets().root().equals(current.assets().root())) {
            repository = Maybe.some(current.withAssets(saved.assets(), saved.projectFile()));
            notifyListeners();
        } else {
            // The archetypes go along with the project file, unless the new root already has its own
            boolean moved = !saved.projectFile().equals(current.projectFile());
            if (moved && !Files.exists(ProjectTemplates.archetypesFileFor(saved.projectFile()))) {
                writer.saveArchetypes(saved.projectFile(), current.archetypeCatalog().getRegistry());
            }
            show(saved);
            recentProjects.add(saved.projectFile());
        }
    }

    public RecentProjects recentProjects() {
        return recentProjects;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public Maybe<ProjectAssets> assets() {
        return repository.map(ProjectRepository::assets);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
