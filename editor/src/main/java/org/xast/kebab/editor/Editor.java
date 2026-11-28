package org.xast.kebab.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.archetype.ParamType;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.archetype.error.InvalidParamType;
import org.xast.kebab.archetype.error.UnknownParamException;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.archetype.instance.NodeArchetype;
import org.xast.kebab.behavior.instance.SnapDistanceBehavior;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.math.Transform;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.project.ProjectAssets;
import org.xast.kebab.project.ProjectConfig;
import org.xast.kebab.project.ProjectManager;
import org.xast.kebab.project.RecentProjects;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.ModelResolver;
import org.xast.kebab.scene.resolve.PrefabResolver;
import org.xast.kebab.utils.Maybe;

import lombok.Getter;

public class Editor {

    private final ProjectManager projectManager;
    private final Selection selection = new Selection();
    private final History history;
    private boolean snapping = true;
    // Kept across scenes of one project, so objects can be copied from one location into another
    private Maybe<NodeTemplate> clipboard = Maybe.none();
    private Maybe<Path> clipboardProject = Maybe.none();
    private final List<Runnable> sceneListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Scene observedScene;
    private final Runnable structureListener = this::sceneChanged;
    // The scene as it is on disk: taken when a scene is opened and after each save
    private Maybe<SceneSnapshot> savedState = Maybe.none();

    public void addSceneListener(Runnable listener) { sceneListeners.add(listener); }
    public void removeSceneListener(Runnable listener) { sceneListeners.remove(listener); }

    /** Model edits and gizmo drags publish here; listeners marshal UI work to the EDT. */
    public void sceneChanged() {
        selection.primary().ifSome(node -> {
            if (!containsNode(node)) selection.clear();
        });
        sceneListeners.forEach(Runnable::run);
    }

    public void updateTransform(SceneNode node, org.xast.kebab.math.Transform transform) {
        Vec3 p = transform.getTranslation();
        var q = transform.getRotation();
        if (!allFinite(p.getX(), p.getY(), p.getZ(), transform.getScale(), q.getX(), q.getY(), q.getZ(), q.getW())) {
            throw new IllegalArgumentException("Transform values must be finite");
        }
        history.record("transform", () -> node.setTransform(transform.copy()));
        sceneChanged();
    }

    /** Replaces a greybox node's shape, per-axis size and colour as one undoable step. */
    public void updateGreybox(SceneNode node, Greybox greybox) {
        Vec3 size = greybox.size();
        if (!allFinite(size.getX(), size.getY(), size.getZ())) {
            throw new IllegalArgumentException("Greybox size must be finite");
        }
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            throw new IllegalArgumentException("Greybox size must be positive");
        }
        if (Greybox.of(node).isNone()) {
            throw new IllegalArgumentException("'" + node.getName() + "' is not a greybox");
        }
        history.record("edit greybox", () -> greybox.writeTo(node));
        sceneChanged();
    }

    public void setArchetypeParam(ArchetypeInstance instance, String name, Object value) {
        boolean finite = switch (value) {
            case null -> true;
            case Float f -> allFinite(f);
            case Double d -> allFinite(d.floatValue());
            case Vec3 v -> allFinite(v.getX(), v.getY(), v.getZ());
            default -> true;
        };
        if (!finite) {
            throw new IllegalArgumentException("Parameter values must be finite");
        }
        history.record("edit " + instance.getDefinition().getDisplayName(), () -> setParam(instance, name, value));
        sceneChanged();
    }

    private static boolean allFinite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /** While a playtest runs the scene is being played, and edit commands refuse to change it. */
    @Getter
    private boolean playtesting;

    public void setPlaytesting(boolean playtesting) {
        if (this.playtesting == playtesting) {
            return;
        }
        this.playtesting = playtesting;
        sceneListeners.forEach(Runnable::run);
    }

    public boolean containsNode(SceneNode node) {
        return scene().map(scene -> scene.find(node.getId()).unwrapOr(null) == node).unwrapOr(false);
    }

    public Editor(ProjectManager projectManager) {
        this.projectManager = projectManager;
        this.history = new History(projectManager::scene, selection);

        // Selected nodes belong to the scene that was just replaced or closed.
        projectManager.addListener(() -> {
            Scene next = scene().unwrapOr(null);
            if (next != observedScene) {
                if (observedScene != null) observedScene.removeListener(structureListener);
                observedScene = next;
                if (next != null) next.addListener(structureListener);
                selection.clear();
                markSaved();
            }
            sceneChanged();
        });
        observedScene = scene().unwrapOr(null);
        if (observedScene != null) observedScene.addListener(structureListener);
        markSaved();
        // The history goes with the scene, and a changed archetype registry can make old steps unrestorable.
        projectManager.addListener(history::clear);
        // Another project has other archetypes, so what was copied in this one does not carry over
        projectManager.addListener(() -> {
            Maybe<Path> project = projectManager.projectFile();
            if (!project.equals(clipboardProject)) {
                clipboard = Maybe.none();
                clipboardProject = project;
            }
        });
        clipboardProject = projectManager.projectFile();
        // Undo and redo restore nodes in place, so the inspector must hear about them too.
        history.addListener(this::sceneChanged);
    }

    /** Creates and opens a project with no scenes, which go to scenes/ by default. */
    public void createProject(Path dir) throws SceneException, IOException {
        createProject(dir, ProjectConfig.DEFAULT_SCENES_FOLDER);
    }

    /**
     * Creates and opens a project with no scenes; {@link #createScene} adds them.
     *
     * @param scenesFolder where new scenes go by default, relative to dir; empty for dir itself
     */
    public void createProject(Path dir, String scenesFolder) throws SceneException, IOException {
        projectManager.create(dir, scenesFolder);
    }

    /**
     * Creates an empty scene in the open project and opens it; unsaved edits are dropped.
     *
     * @param folder relative to the asset root; empty puts the scene in the root itself
     * @param name   the file name, with or without the .gltf extension
     */
    public void createScene(String folder, String name) throws SceneException, IOException {
        projectManager.createScene(folder, name);
    }

    public void closeProject() {
        projectManager.close();
    }

    public void openProject(Path file) throws SceneException, IOException {
        projectManager.open(file);
    }

    /** Replaces the edited scene with another one of the same project; unsaved edits are dropped. */
    public void openScene(Path scene) throws SceneException, IOException {
        projectManager.openScene(scene);
    }

    public void saveScene() throws SceneFormatException, IOException {
        projectManager.saveScene();
        markSaved();
    }

    public void saveProject() throws SceneFormatException, IOException {
        projectManager.save();
        markSaved();
    }

    /**
     * Whether the open scene differs from what was last opened or saved. Undoing
     * back to the saved state counts as no change; the selection is not an edit.
     */
    public boolean hasUnsavedChanges() {
        return scene().isSomeAnd(scene ->
            savedState.isNone() || !savedState.unwrap().sameScene(SceneSnapshot.capture(scene, selection))
        );
    }

    private void markSaved() {
        savedState = scene().map(scene -> SceneSnapshot.capture(scene, selection));
    }

    /** The kebab-project.json the project was opened from, or its scene file for a project without one. */
    public Maybe<Path> currentProjectFile() {
        return projectManager.projectFile();
    }

    /** The scene being edited. */
    public Maybe<Path> currentSceneFile() {
        return projectManager.currentScene();
    }

    public Maybe<Scene> scene() {
        return projectManager.scene();
    }

    public Selection selection() {
        return selection;
    }

    public History history() {
        return history;
    }

    public void undo() {
        if (playtesting) {
            return;
        }
        history.undo().ifSome(label -> LogService.get().info("Undid " + label));
    }

    public void redo() {
        if (playtesting) {
            return;
        }
        history.redo().ifSome(label -> LogService.get().info("Redid " + label));
    }

    public boolean isProjectOpen() {
        return projectManager.isOpen();
    }

    /** A project can be open with no scene, e.g. right after it was created. */
    public boolean isSceneOpen() {
        return scene().isSome();
    }

    public List<ArchetypeDefinition> archetypes() {
        return projectManager.archetypes();
    }

    public Maybe<ModelResolver> modelResolver() {
        return projectManager.modelResolver();
    }

    public Maybe<PrefabResolver> prefabResolver() {
        return projectManager.prefabResolver();
    }

    public boolean isBuiltinArchetype(String archetypeId) {
        return projectManager.isBuiltin(archetypeId);
    }

    public void addArchetype(ArchetypeDefinition definition) throws IOException {
        projectManager.addArchetype(definition);
    }

    public void removeArchetype(ArchetypeDefinition definition) throws IOException {
        projectManager.removeArchetype(definition);
    }

    public List<SceneNode> nodesUsing(String archetypeId) {
        return sceneNodes().stream()
            .filter(node -> node.getArchetypes().stream().anyMatch(a -> a.archetypeId().equals(archetypeId)))
            .toList();
    }

    public int instanceCount(String archetypeId) {
        return (int) sceneNodes().stream()
            .flatMap(node -> node.getArchetypes().stream())
            .filter(archetype -> archetype.archetypeId().equals(archetypeId))
            .count();
    }

    public void removeArchetypeWithInstances(ArchetypeDefinition definition) throws IOException {
        List<SceneNode> users = nodesUsing(definition.getId());

        projectManager.removeArchetype(definition);

        for (SceneNode node : users) {
            for (NodeArchetype archetype : List.copyOf(node.getArchetypes())) {
                if (archetype.archetypeId().equals(definition.getId())) {
                    node.removeArchetype(archetype);
                }
            }
        }

        sceneChanged();
        LogService.get().info("Removed archetype " + definition.getId() + " and its instances from " + users.size() + " objects");
    }

    public void saveArchetypes() throws IOException {
        projectManager.saveArchetypes();
        sceneChanged();
    }

    public RecentProjects recentProjects() {
        return projectManager.recentProjects();
    }

    public Maybe<ProjectAssets> projectAssets() {
        return projectManager.assets();
    }

    /** @param scenesFolder where new scenes go by default, relative to root; empty for root itself */
    public void updateAssetScope(Path root, List<String> folders, List<String> scenes, String scenesFolder)
        throws SceneException, IOException {
        projectManager.updateAssetScope(root, folders, scenes, scenesFolder);
    }

    public void removeProjectListener(Runnable listener) {
        projectManager.removeListener(listener);
    }

    public void addProjectListener(Runnable listener) {
        projectManager.addListener(listener);
    }

    public SceneNode spawnGreybox(GreyboxShape shape, Vec3 base) {
        float halfHeight = Greybox.unit(shape).size().getY() / 2f;
        return placeGreybox(shape, base.add(new Vec3(0, halfHeight, 0)));
    }

    public SceneNode placeGreybox(GreyboxShape shape, Vec3 center) {
        return history.record("add " + shape.displayName(), () -> {
            SceneNode node = new SceneNode(shape.displayName());
            node.getTransform().setTranslation(center.copy());
            Greybox.unit(shape).writeTo(node);

            addToScene(node);
            LogService.get().info("Spawned greybox " + shape.jsonName() + " at " + center);
            return node;
        });
    }

    public List<SceneNode> sceneNodes() {
        return projectManager.scene()
            .map(scene -> scene.traverse().toList())
            .unwrapOr(List.of());
    }

    public List<ArchetypeDefinition> availableArchetypes(SceneNode node) {
        Set<String> present = node.getArchetypes().stream()
            .map(NodeArchetype::archetypeId)
            .collect(Collectors.toSet());

        return archetypes().stream()
            .filter(definition -> !definition.isSingle() || !present.contains(definition.getId()))
            .toList();
    }

    public void attachArchetype(SceneNode node, ArchetypeInstance instance) {
        ArchetypeDefinition definition = instance.getDefinition();

        if (definition.isSingle() && node.getArchetypes().stream().anyMatch(a -> a.archetypeId().equals(definition.getId()))) {
            throw new IllegalArgumentException(
                "'" + definition.getDisplayName() + "' can only be added once to an object"
            );
        }

        history.record("add " + definition.getDisplayName(), () -> node.addArchetype(instance));
        sceneChanged();
        LogService.get().info("Added " + definition.getId() + " to " + node.getName());
    }

    public void setParams(ArchetypeInstance instance, Map<String, Object> values) {
        history.record("edit " + instance.getDefinition().getDisplayName(), () ->
            values.forEach((name, value) -> setParam(instance, name, value)));
    }

    public void renameNode(SceneNode node, String name) {
        String trimmed = name.trim();

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("An object needs a name");
        }

        String previous = Objects.toString(node.getName(), "");
        history.record("rename", () -> node.setName(trimmed));
        sceneChanged();
        LogService.get().info("Renamed '" + previous + "' to '" + trimmed + "'");
    }

    /**
     * Moves nodes under a new parent, or to the top level, as one undoable step.
     * Each keeps its place in the world, so its local transform is recomputed.
     * A node whose ancestor is moved too goes along with that ancestor.
     *
     * @param parent the new parent; none for the top level
     * @param index  where the nodes go among the new siblings, as in {@link Scene#move}
     * @throws IllegalArgumentException when the parent is one of the nodes or inside one of them
     */
    public void reparent(List<SceneNode> nodes, Maybe<SceneNode> parent, int index) {
        if (playtesting) {
            return;
        }
        Scene scene = projectManager.scene().expect("No scene is open");
        Set<SceneNode> chosen = Set.copyOf(nodes);
        // Scene order, so nodes moved together keep their order
        List<SceneNode> moved = scene.traverse()
            .filter(node -> chosen.contains(node) && !hasChosenAncestor(node, chosen))
            .toList();
        if (moved.isEmpty()) {
            return;
        }
        parent.ifSome(target -> {
            for (Maybe<SceneNode> at = Maybe.some(target); at.isSome(); at = at.unwrap().getParent()) {
                if (chosen.contains(at.unwrap())) {
                    throw new IllegalArgumentException(
                        "Cannot move '" + at.unwrap().getName() + "' into itself or one of its children");
                }
            }
        });

        Transform parentWorld = parent.map(Editor::worldTransform).unwrapOrElse(Transform::identity);
        if (!(Math.abs(parentWorld.getScale()) > 1e-6f)) {
            throw new IllegalArgumentException("Cannot move objects under '"
                + parent.unwrap().getName() + "': its scale is zero");
        }

        history.record(moved.size() == 1 ? "reparent " + moved.getFirst().getName() : "reparent", () -> {
            int at = index;
            for (SceneNode node : moved) {
                Transform world = worldTransform(node);
                try {
                    scene.move(node, parent, at);
                } catch (SceneException e) {
                    throw new IllegalStateException("Cannot move '" + node.getName() + "'", e);
                }
                node.setTransform(parentWorld.inverse().mul(world));
                at = parent.mapOr(scene.getRootNodes(), SceneNode::getChildren).indexOf(node) + 1;
            }
        });
        sceneChanged();
        LogService.get().info("Moved " + moved.size() + " object(s) "
            + parent.map(p -> "under '" + p.getName() + "'").unwrapOr("to the top level"));
    }

    // Node transforms have uniform scale, so composing them stays exact, unlike decomposing a matrix
    private static Transform worldTransform(SceneNode node) {
        return NodeTemplate.worldTransform(node);
    }

    /** Puts copies of the selected objects, with their children, on the clipboard. @return how many objects */
    public int copySelection() {
        List<SceneNode> roots = selectedRoots();
        if (roots.isEmpty()) {
            return 0;
        }
        clipboard = Maybe.some(NodeTemplate.of(roots));
        LogService.get().info("Copied " + clipboard.unwrap().size() + " object(s)");
        return clipboard.unwrap().size();
    }

    public boolean canPaste() {
        return clipboard.isSome() && scene().isSome();
    }

    /**
     * Adds the copied objects again, moved sideways so they are centered on {@code target}
     * at the height they had. They go back under their original parent while it is still
     * in the scene, and become the selection.
     */
    public List<SceneNode> paste(Vec3 target) {
        if (playtesting || !canPaste()) {
            return List.of();
        }
        NodeTemplate template = clipboard.unwrap();
        Scene scene = scene().unwrap();

        return history.record("paste", () -> {
            List<NodeTemplate.Made> made = template.instantiate(projectManager.catalog());
            List<NodeTemplate.Made> tops = made.stream().filter(item -> item.entry().parent() < 0).toList();
            Vec3 center = Vec3.zero();
            tops.forEach(item -> center.addLocal(item.entry().world().getTranslation()));
            center.divLocal(tops.size());
            Vec3 shift = new Vec3(target.getX() - center.getX(), 0, target.getZ() - center.getZ());

            for (NodeTemplate.Made item : made) {
                if (item.entry().parent() >= 0) {
                    addOrFail(scene, item.node(), Maybe.some(made.get(item.entry().parent()).node()));
                    continue;
                }
                Maybe<SceneNode> parent = livingParent(scene, item.entry());
                Transform world = item.entry().world().copy();
                world.getTranslation().addLocal(shift);
                item.node().setTransform(parent.map(p -> worldTransform(p).inverse().mul(world)).unwrapOr(world));
                addOrFail(scene, item.node(), parent);
            }
            List<SceneNode> pasted = tops.stream().map(NodeTemplate.Made::node).toList();
            selection.set(pasted);
            LogService.get().info("Pasted " + made.size() + " object(s) at " + target);
            return pasted;
        });
    }

    /** Copies the selected objects in place, each right after its original, and selects the copies. */
    public List<SceneNode> duplicateSelection() {
        List<SceneNode> roots = selectedRoots();
        if (playtesting || roots.isEmpty()) {
            return List.of();
        }
        Scene scene = scene().unwrap();
        NodeTemplate template = NodeTemplate.of(roots);

        return history.record("duplicate", () -> {
            List<NodeTemplate.Made> made = template.instantiate(projectManager.catalog());
            List<SceneNode> copies = new ArrayList<>();
            int root = 0;
            for (NodeTemplate.Made item : made) {
                if (item.entry().parent() >= 0) {
                    addOrFail(scene, item.node(), Maybe.some(made.get(item.entry().parent()).node()));
                    continue;
                }
                SceneNode original = roots.get(root++);
                Maybe<SceneNode> parent = original.getParent();
                addOrFail(scene, item.node(), parent);
                int after = parent.mapOr(scene.getRootNodes(), SceneNode::getChildren).indexOf(original) + 1;
                try {
                    scene.move(item.node(), parent, after);
                } catch (SceneException e) {
                    throw new IllegalStateException("Cannot place the copy of '" + original.getName() + "'", e);
                }
                copies.add(item.node());
            }
            selection.set(copies);
            LogService.get().info("Duplicated " + made.size() + " object(s)");
            return copies;
        });
    }

    // Selected objects without a selected ancestor, in scene order
    private List<SceneNode> selectedRoots() {
        Set<SceneNode> chosen = Set.copyOf(selection.nodes());
        return scene()
            .map(scene -> scene.traverse()
                .filter(node -> chosen.contains(node) && !hasChosenAncestor(node, chosen))
                .toList())
            .unwrapOr(List.of());
    }

    // A parent that was deleted, or that cannot hold children because its scale is zero, leaves the top level
    private static Maybe<SceneNode> livingParent(Scene scene, NodeTemplate.Entry entry) {
        return entry.oldParent()
            .andThen(scene::find)
            .filter(parent -> Math.abs(worldTransform(parent).getScale()) > 1e-6f);
    }

    private static void addOrFail(Scene scene, SceneNode node, Maybe<SceneNode> parent) {
        try {
            if (parent.isSome()) {
                scene.add(node, parent.unwrap());
            } else {
                scene.add(node);
            }
        } catch (SceneException e) {
            // Fresh copies have new ids and no parent yet, so this cannot happen.
            throw new IllegalStateException("Cannot add a copy of '" + node.getName() + "'", e);
        }
    }

    public Deletion planDeletion(List<SceneNode> selected) {
        Set<SceneNode> chosen = Set.copyOf(selected);
        List<SceneNode> roots = selected.stream()
            .filter(node -> !hasChosenAncestor(node, chosen))
            .toList();

        List<SceneNode> removed = new ArrayList<>();
        roots.forEach(root -> collect(root, removed));
        Set<String> removedIds = removed.stream()
            .map(node -> node.getId().value().toString())
            .collect(Collectors.toSet());
        Set<SceneNode> removedNodes = Set.copyOf(removed);

        List<Deletion.NodeReference> references = new ArrayList<>();

        for (SceneNode node : sceneNodes()) {
            if (removedNodes.contains(node)) {
                continue;
            }

            for (NodeArchetype archetype : node.getArchetypes()) {
                if (archetype instanceof ArchetypeInstance instance) {
                    collectReferences(node, instance, removedIds, references);
                }
            }
        }

        return new Deletion(roots, removed.size(), references);
    }

    public void delete(Deletion deletion) {
        Scene scene = projectManager.scene().expect("No scene is open");

        history.record("delete", () -> {
            for (Deletion.NodeReference reference : deletion.references()) {
                setParam(reference.instance(), reference.param(), Maybe.none());
            }

            for (SceneNode root : deletion.roots()) {
                try {
                    scene.remove(root);
                } catch (SceneException e) {
                    throw new IllegalStateException("Cannot remove '" + root.getName() + "' from the scene", e);
                }
            }

            selection.clear();
        });
        LogService.get().info(
            "Deleted " + deletion.nodeCount() + " object(s), cleared " + deletion.references().size() + " reference(s)"
        );
    }

    private static boolean hasChosenAncestor(SceneNode node, Set<SceneNode> chosen) {
        Maybe<SceneNode> parent = node.getParent();

        while (parent.isSome()) {
            if (chosen.contains(parent.unwrap())) {
                return true;
            }
            parent = parent.unwrap().getParent();
        }

        return false;
    }

    private static void collect(SceneNode node, List<SceneNode> into) {
        into.add(node);
        node.getChildren().forEach(child -> collect(child, into));
    }

    private static void collectReferences(
        SceneNode owner,
        ArchetypeInstance instance,
        Set<String> removedIds,
        List<Deletion.NodeReference> into
    ) {
        for (var entry : instance.getDefinition().getParams().entrySet()) {
            if (entry.getValue().type() != ParamType.NODE_REF) {
                continue;
            }

            try {
                if (instance.get(entry.getKey()) instanceof Maybe<?> target
                    && target.isSome()
                    && removedIds.contains(String.valueOf(target.unwrap()))
                ) {
                    into.add(new Deletion.NodeReference(owner, instance, entry.getKey()));
                }
            } catch (UnknownParamException e) {
                throw new IllegalStateException("Param vanished from its own definition", e);
            }
        }
    }

    public void detachArchetype(SceneNode node, NodeArchetype archetype) {
        history.record("remove " + archetype.archetypeId(), () -> node.removeArchetype(archetype));
        sceneChanged();
        LogService.get().info("Removed " + archetype.archetypeId() + " from " + node.getName());
    }

    public SceneNode spawnEmpty(Vec3 base) {
        return history.record("add empty", () -> {
            SceneNode node = new SceneNode("Empty");
            node.getTransform().setTranslation(base.copy());

            addToScene(node);
            LogService.get().info("Spawned empty at " + base);
            return node;
        });
    }

    public SceneNode spawnArchetype(String archetypeId, Vec3 base) {
        ArchetypeInstance instance = newInstance(archetypeId);

        return history.record("add " + instance.getDefinition().getDisplayName(), () -> {
            SceneNode node = new SceneNode(instance.getDefinition().getDisplayName());
            node.getTransform().setTranslation(base.copy());
            node.addArchetype(instance);

            addToScene(node);
            LogService.get().info("Spawned " + archetypeId + " at " + base);
            return node;
        });
    }

    /**
     * Adds a node for a glTF file of the project, e.g. one dragged in from the asset explorer:
     * a registered scene becomes a prefab_ref, any other file a model_ref. Files are only
     * referenced, never copied, so one outside the asset root is refused.
     *
     * @throws IllegalArgumentException when the file is outside the project, or the scene would end up containing itself
     */
    public SceneNode placeAsset(Path file, Vec3 base) {
        Path target = file.toAbsolutePath().normalize();
        ProjectAssets assets = projectManager.assets().expect("No project is open");
        if (!target.startsWith(assets.root())) {
            throw new IllegalArgumentException(target.getFileName() + " is outside the project. "
                + "Put it under the asset root (" + assets.root() + ") first, then drag it in from the Asset Explorer.");
        }
        if (!assets.isScene(target)) {
            return addReference(BuiltinArchetypes.MODEL_REF, target, base, "add model");
        }

        Path sceneFile = projectManager.currentScene().expect("No scene is open").toAbsolutePath().normalize();
        if (target.equals(sceneFile)) {
            throw new IllegalArgumentException("A scene cannot contain itself: " + target.getFileName());
        }
        if (projectManager.prefabResolver().isSomeAnd(resolver -> resolver.wouldCycle(sceneFile, target))) {
            throw new IllegalArgumentException(target.getFileName() + " already contains "
                + sceneFile.getFileName() + ", so it cannot be placed in it");
        }
        return addReference(BuiltinArchetypes.PREFAB_REF, target, base, "add prefab");
    }

    // A node named after the file, whose reference is relative to the open scene
    private SceneNode addReference(String archetypeId, Path file, Vec3 base, String label) {
        String reference = currentSceneDir().relativize(file).toString().replace('\\', '/');
        ArchetypeInstance instance = newInstance(archetypeId);
        setParam(instance, "path", reference);

        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        SceneNode node = new SceneNode(dot > 0 ? fileName.substring(0, dot) : fileName);
        node.getTransform().setTranslation(base.copy());
        node.addArchetype(instance);

        history.record(label, () -> addToScene(node));
        LogService.get().info("Placed " + archetypeId + " " + reference + " at " + base);
        return node;
    }

    private Path currentSceneDir() {
        return projectManager.currentScene().expect("No scene is open").toAbsolutePath().getParent();
    }

    public boolean isSnapping() {
        return snapping;
    }

    public void setSnapping(boolean snapping) {
        this.snapping = snapping;
    }

    public Maybe<Float> snapStep() {
        return snapping 
            ? Maybe.some(snapDistance()) 
            : Maybe.none();
    }

    public float snapDistance() {
        return snapDistanceInstance()
            .andThen(SnapDistanceBehavior::distance)
            .unwrapOr(BuiltinArchetypes.DEFAULT_SNAP_DISTANCE);
    }

    public void setSnapDistance(float distance) {
        if (!(distance > 0) || !Float.isFinite(distance)) {
            throw new IllegalArgumentException("Snap distance must be a positive number, got " + distance);
        }

        history.record("snap distance", () -> {
            ArchetypeInstance instance = snapDistanceInstance().unwrapOrElse(() -> {
                ArchetypeInstance created = newInstance(BuiltinArchetypes.SNAP_DISTANCE);
                SceneNode node = new SceneNode("Snap Distance");
                node.addArchetype(created);
                addToScene(node);
                return created;
            });

            setArchetypeParam(instance, "distance", distance);
        });
        LogService.get().info("Snap distance set to " + distance);
    }

    private Maybe<ArchetypeInstance> snapDistanceInstance() {
        return projectManager.scene()
            .andThen(scene -> Maybe.fromOptional(
                scene.traverse()
                    .flatMap(node -> node.getArchetypes().stream())
                    .filter(a -> a instanceof ArchetypeInstance i && i.archetypeId().equals(BuiltinArchetypes.SNAP_DISTANCE))
                    .map(ArchetypeInstance.class::cast)
                    .findFirst()
            ));
    }

    private ArchetypeInstance newInstance(String archetypeId) {
        ArchetypeCatalog catalog = projectManager.catalog().expect("No project is open");
        return new ArchetypeInstance(catalog.find(archetypeId)
            .expect("Builtin archetype '" + archetypeId + "' is not in the catalog"));
    }

    private static void setParam(ArchetypeInstance instance, String name, Object value) {
        try {
            instance.set(name, value);
        } catch (UnknownParamException | InvalidParamType e) {
            // Builtin definitions are fixed, so a mismatch here is a programming error.
            throw new IllegalStateException("Cannot set " + instance.archetypeId() + "." + name, e);
        }
    }

    private void addToScene(SceneNode node) {
        Scene scene = projectManager.scene().expect("No scene is open");

        try {
            scene.add(node);
        } catch (SceneException e) {
            // A freshly created node has a unique id and no parent, so this cannot happen.
            throw new IllegalStateException("Cannot add a new node to the scene", e);
        }

        selection.select(node);
    }
}
