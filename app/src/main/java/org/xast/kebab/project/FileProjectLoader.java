package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.archetype.definition.ArchetypeRegistryJson;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.scene.io.SceneReader;
import org.xast.kebab.scene.resolve.CachingModelResolver;
import org.xast.kebab.scene.resolve.CachingPrefabResolver;
import org.xast.kebab.scene.resolve.ModelExtractor;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

// A project opens from its kebab-project.json, or from a bare scene file for a project without one.
@RequiredArgsConstructor
public class FileProjectLoader implements ProjectLoader {

    private final SceneReader sceneReader;
    private final ObjectMapper mapper;
    private final ModelExtractor modelExtractor;

    @Override
    public SceneScope scopeFor(Path file) throws IOException {
        return new ProjectConfigStore(mapper).scopeFor(file);
    }

    @Override
    public ProjectRepository load(SceneScope scope) throws SceneException, IOException {
        ProjectAssets assets = scope.assets();
        ArchetypeRegistry registry = loadRegistry(registryPath(scope));
        ArchetypeCatalog catalog = new ArchetypeCatalog(registry);

        Maybe<ProjectRepository.OpenScene> scene = Maybe.none();
        if (scope.scene().isSome()) {
            Path file = scope.scene().unwrap();
            scene = Maybe.some(new ProjectRepository.OpenScene(file, sceneReader.read(file, catalog)));
        }

        // Each project gets its own resolver, rooted at the project folder, so models never leak between projects
        Path projectRoot = assets.root();
        ModelResolver modelResolver = new CachingModelResolver(modelExtractor, projectRoot);

        PrefabResolver prefabResolver = new CachingPrefabResolver(sceneReader, catalog, projectRoot);

        return new ProjectRepository(scene, catalog, modelResolver, prefabResolver, assets, scope.projectFile());
    }

    // Projects made before archetypes moved to the project folder kept them next to the scene.
    // That file is read until the next save writes the project's own.
    private static Path registryPath(SceneScope scope) {
        Path projectRegistry = ProjectTemplates.archetypesFileFor(scope.projectFile());
        if (Files.exists(projectRegistry) || scope.scene().isNone()) {
            return projectRegistry;
        }
        Path sceneRegistry = scope.scene().unwrap().resolveSibling(ProjectTemplates.ARCHETYPES_FILE);
        if (Files.isRegularFile(sceneRegistry)) {
            LogService.get().info("Using " + sceneRegistry + " for archetypes; saving moves them to " + projectRegistry);
            return sceneRegistry;
        }
        return projectRegistry;
    }

    // A missing registry is just an empty one; 
    // the file is created on the first save
    private ArchetypeRegistry loadRegistry(Path registryPath) throws IOException {
        if (!Files.exists(registryPath)) {
            return new ArchetypeRegistry();
        }

        return mapper.readValue(
            Files.readString(registryPath),
            ArchetypeRegistryJson.class
        ).toRegistry();
    }
}
