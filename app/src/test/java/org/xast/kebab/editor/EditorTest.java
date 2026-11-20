package org.xast.kebab.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.project.FileProjectLoader;
import org.xast.kebab.project.FileProjectWriter;
import org.xast.kebab.project.ModelImporter;
import org.xast.kebab.project.ProjectManager;
import org.xast.kebab.project.RecentProjects;
import org.xast.kebab.scene.io.GltfSceneReader;
import org.xast.kebab.scene.io.GltfSceneWriter;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.CompositeModelExtractor;
import org.xast.kebab.scene.resolve.GltfMeshExtractor;
import org.xast.kebab.scene.resolve.ModelResult.ResolvedModel;

import com.fasterxml.jackson.databind.ObjectMapper;

class EditorTest {

    @TempDir
    Path projectDir;

    @TempDir
    Path elsewhere;

    private Editor editor;

    // Keeps the user's real recent-projects file out of tests.
    private static final class InMemoryRecentProjects implements RecentProjects {
        private final List<Path> paths = new ArrayList<>();

        public List<Path> paths() { return List.copyOf(paths); }
        public void add(Path file) { paths.add(file); }
        public void remove(Path file) { paths.remove(file); }
        public void clear() { paths.clear(); }
    }

    @BeforeEach
    void openNewProject() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProjectManager projects = new ProjectManager(
            new InMemoryRecentProjects(),
            new FileProjectWriter(new GltfSceneWriter(mapper), mapper),
            new FileProjectLoader(new GltfSceneReader(mapper), mapper,
                new CompositeModelExtractor(List.of(new GltfMeshExtractor())))
        );
        editor = new Editor(projects, new ModelImporter(mapper));
        editor.createProject(projectDir);
        editor.createScene("", "level");
    }

    @Test
    void snapDistanceDefaultsWithoutASnapDistanceObject() {
        assertEquals(BuiltinArchetypes.DEFAULT_SNAP_DISTANCE, editor.snapDistance());
    }

    @Test
    void settingTheSnapDistanceAddsASnapDistanceObjectThatIsSaved() throws Exception {
        editor.setSnapDistance(0.25f);
        editor.setSnapDistance(2f); // updates the same object rather than adding another

        long markers = editor.scene().unwrap().traverse()
            .filter(n -> n.getArchetypes().stream().anyMatch(a -> a.archetypeId().equals(BuiltinArchetypes.SNAP_DISTANCE)))
            .count();
        assertEquals(1, markers);
        assertEquals(2f, editor.snapDistance());

        editor.saveProject();
        editor.openProject(editor.currentProjectFile().unwrap());
        assertEquals(2f, editor.snapDistance());
    }

    @Test
    void rejectsNonPositiveSnapDistances() {
        assertThrows(IllegalArgumentException.class, () -> editor.setSnapDistance(0f));
        assertThrows(IllegalArgumentException.class, () -> editor.setSnapDistance(-1f));
        assertThrows(IllegalArgumentException.class, () -> editor.setSnapDistance(Float.NaN));
    }

    @Test
    void playtestingFlagIsOffUntilSet() {
        assertFalse(editor.isPlaytesting());

        editor.setPlaytesting(true);
        assertTrue(editor.isPlaytesting());

        editor.setPlaytesting(false);
        assertFalse(editor.isPlaytesting());
    }

    @Test
    void snapStepFollowsTheSnappingSwitch() {
        assertEquals(0.5f, editor.snapStep().unwrap());

        editor.setSnapping(false);
        assertTrue(editor.snapStep().isNone());
    }

    @Test
    void importedModelIsCopiedInAndShownThroughModelRef() throws Exception {
        Path model = writeTriangleGltf(elsewhere.resolve("tri.gltf"));

        SceneNode node = editor.importModel(model, new Vec3(1, 0, 2));

        assertTrue(Files.isRegularFile(projectDir.resolve("models/tri/tri.gltf")));
        assertTrue(Files.isRegularFile(projectDir.resolve("models/tri/tri.bin")));

        ArchetypeInstance modelRef = assertInstanceOf(ArchetypeInstance.class, node.getArchetypes().getFirst());
        assertEquals(BuiltinArchetypes.MODEL_REF, modelRef.archetypeId());
        assertEquals("models/tri/tri.gltf", modelRef.get("path"));

        ResolvedModel resolved = assertInstanceOf(ResolvedModel.class,
            editor.modelResolver().unwrap().resolve(editor.currentSceneFile().unwrap(), "models/tri/tri.gltf"));
        assertEquals(1, resolved.parts().size());
        assertSame(node, editor.selection().primary().unwrap());
    }

    @Test
    void unsavedChangesFollowEditsSavesAndUndo() throws Exception {
        assertFalse(editor.hasUnsavedChanges(), "a freshly opened scene");

        SceneNode box = editor.spawnGreybox(GreyboxShape.BOX, Vec3.zero());
        assertTrue(editor.hasUnsavedChanges());

        editor.saveProject();
        assertFalse(editor.hasUnsavedChanges());

        editor.selection().clear();
        assertFalse(editor.hasUnsavedChanges(), "selecting is not an edit");

        editor.renameNode(box, "crate");
        assertTrue(editor.hasUnsavedChanges());
        editor.undo();
        assertFalse(editor.hasUnsavedChanges(), "undone back to the saved scene");
        editor.undo();
        assertTrue(editor.hasUnsavedChanges(), "undone past the save");

        editor.openProject(editor.currentProjectFile().unwrap());
        assertFalse(editor.hasUnsavedChanges(), "reopened from disk");

        editor.closeProject();
        assertFalse(editor.hasUnsavedChanges(), "no scene open");
    }

    @Test
    void undoRemovesASpawnedObjectAndRedoBringsItBack() {
        SceneNode box = editor.spawnGreybox(GreyboxShape.BOX, Vec3.zero());

        editor.undo();
        assertTrue(editor.scene().unwrap().find(box.getId()).isNone());
        assertTrue(editor.selection().nodes().isEmpty());
        assertTrue(editor.history().canRedo());

        editor.redo();
        assertSame(box, editor.scene().unwrap().find(box.getId()).unwrap());
        assertSame(box, editor.selection().primary().unwrap());
    }

    @Test
    void undoingADeleteRestoresTheSameNodesWithTheirChildren() throws Exception {
        SceneNode parent = editor.spawnEmpty(Vec3.zero());
        SceneNode child = new SceneNode("child");
        editor.scene().unwrap().add(child, parent);

        editor.delete(editor.planDeletion(List.of(parent)));
        assertTrue(editor.scene().unwrap().find(child.getId()).isNone());

        editor.undo();
        assertSame(parent, editor.scene().unwrap().find(parent.getId()).unwrap());
        assertSame(child, editor.scene().unwrap().find(child.getId()).unwrap());
        assertSame(parent, child.getParent().unwrap());
        assertEquals(List.of(child), parent.getChildren());
    }

    @Test
    void aDragIsOneStepAndAnUnchangedOneIsNotRecorded() {
        SceneNode node = editor.spawnEmpty(Vec3.zero());

        editor.history().begin();
        assertFalse(editor.history().canUndo());
        node.getTransform().setTranslation(new Vec3(1, 0, 0));
        node.getTransform().setTranslation(new Vec3(2, 0, 0));
        editor.history().commit("move");

        editor.history().begin();
        editor.history().commit("move");

        editor.undo();
        assertEquals(Vec3.zero(), node.getTransform().getTranslation());

        editor.undo();
        assertTrue(editor.scene().unwrap().find(node.getId()).isNone());
        assertFalse(editor.history().canUndo());
    }

    @Test
    void undoRestoresParamValuesAndANewEditClearsRedo() throws Exception {
        editor.setSnapDistance(2f);
        ArchetypeInstance snap = editor.sceneNodes().stream()
            .flatMap(n -> n.getArchetypes().stream())
            .map(ArchetypeInstance.class::cast)
            .findFirst().orElseThrow();
        editor.setParams(snap, Map.of("distance", 4f));

        editor.undo();
        assertEquals(2f, snap.get("distance"));

        editor.renameNode(editor.sceneNodes().getFirst(), "Grid");
        assertFalse(editor.history().canRedo());
    }

    @Test
    void reopeningTheProjectClearsTheHistory() throws Exception {
        editor.spawnEmpty(Vec3.zero());
        editor.openProject(editor.currentProjectFile().unwrap());

        assertFalse(editor.history().canUndo());
    }

    // One triangle with positions in an external .bin next to the .gltf.
    static Path writeTriangleGltf(Path gltf) throws Exception {
        ByteBuffer bin = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : new float[] { 0, 0, 0, 1, 0, 0, 0, 0, 1 }) {
            bin.putFloat(f);
        }
        Files.write(gltf.resolveSibling("tri.bin"), bin.array());
        Files.writeString(gltf, """
            {
              "asset": { "version": "2.0" },
              "scenes": [ { "nodes": [0] } ],
              "nodes": [ { "mesh": 0 } ],
              "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0 } } ] } ],
              "buffers": [ { "uri": "tri.bin", "byteLength": 36 } ],
              "bufferViews": [ { "buffer": 0, "byteLength": 36 } ],
              "accessors": [ { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                               "min": [0, 0, 0], "max": [1, 0, 1] } ]
            }
            """);
        return gltf;
    }

    @Test
    void openingAProjectRemembersItsProjectFileAndOpensItsFirstScene() {
        Path project = projectDir.resolve("kebab-project.json");

        assertEquals(project, editor.currentProjectFile().unwrap());
        assertEquals(projectDir.resolve("level.gltf"), editor.currentSceneFile().unwrap());
        assertEquals(List.of(project), editor.recentProjects().paths());
    }

    @Test
    void openingAnotherSceneKeepsTheProjectAndDropsTheEdits() throws Exception {
        Files.copy(projectDir.resolve("level.gltf"), projectDir.resolve("assets/town.gltf"));
        editor.spawnEmpty(Vec3.zero());

        editor.openScene(projectDir.resolve("assets/town.gltf"));

        assertEquals(projectDir.resolve("assets/town.gltf"), editor.currentSceneFile().unwrap());
        assertEquals(projectDir.resolve("kebab-project.json"), editor.currentProjectFile().unwrap());
        assertTrue(editor.sceneNodes().isEmpty());
        assertFalse(editor.hasUnsavedChanges());
        assertTrue(editor.history().canUndo() == false);
        assertEquals(1, editor.recentProjects().paths().size());
    }

    @Test
    void aSceneOutsideTheProjectCannotBeOpenedInIt() throws Exception {
        Path outside = elsewhere.resolve("other.gltf");
        Files.copy(projectDir.resolve("level.gltf"), outside);

        assertThrows(java.io.IOException.class, () -> editor.openScene(outside));
        assertEquals(projectDir.resolve("level.gltf"), editor.currentSceneFile().unwrap());
    }

    @Test
    void modelsOfAScenesFolderGoToTheSharedModelsFolder() throws Exception {
        Path dir = elsewhere.resolve("game");
        editor.createProject(dir);
        editor.createScene("scenes", "world");
        Path model = elsewhere.resolve("tri.gltf");
        Files.writeString(model, "{\"asset\":{\"version\":\"2.0\"}}");

        editor.importModel(model, Vec3.zero());

        assertTrue(Files.isRegularFile(dir.resolve("models/tri/tri.gltf")), "models: " + dir.resolve("models"));
        ArchetypeInstance ref = (ArchetypeInstance) editor.sceneNodes().getFirst().getArchetypes().getFirst();
        assertEquals("../models/tri/tri.gltf", ref.get("path"));
    }

    @Test
    void movingTheAssetRootTakesTheArchetypesAlong() throws Exception {
        Path game = elsewhere.resolve("game");
        editor.createProject(game.resolve("level"));
        editor.createScene("", "world");
        editor.addArchetype(new org.xast.kebab.archetype.definition.ArchetypeDefinition("door"));

        editor.updateAssetScope(game, List.of("level/assets"), List.of("level/world.gltf"), "level/scenes");

        assertEquals(game.resolve("kebab-project.json"), editor.currentProjectFile().unwrap());
        assertTrue(Files.readString(game.resolve("archetypes.json")).contains("door"));
        assertTrue(editor.archetypes().stream().anyMatch(definition -> definition.getId().equals("door")));
    }

    @Test
    void aNewProjectHasNoSceneUntilOneIsCreated() throws Exception {
        Path dir = elsewhere.resolve("fresh");
        editor.createProject(dir);

        assertTrue(editor.isProjectOpen());
        assertFalse(editor.isSceneOpen());
        assertFalse(editor.hasUnsavedChanges());
        editor.saveProject();

        editor.createScene("scenes", "harbor");

        assertTrue(editor.isSceneOpen());
        assertEquals(dir.resolve("scenes/harbor.gltf"), editor.currentSceneFile().unwrap());
        assertEquals(dir.resolve("kebab-project.json"), editor.currentProjectFile().unwrap());
        assertTrue(editor.projectAssets().unwrap().isScene(dir.resolve("scenes/harbor.gltf")));
    }

    @Test
    void aNewSceneReplacesTheOpenOne() throws Exception {
        editor.spawnEmpty(Vec3.zero());

        editor.createScene("", "second");

        assertEquals(projectDir.resolve("second.gltf"), editor.currentSceneFile().unwrap());
        assertTrue(editor.sceneNodes().isEmpty());
        assertTrue(editor.projectAssets().unwrap().isScene(projectDir.resolve("level.gltf")));
    }

    @Test
    void theAssetRootOfAProjectWithoutScenesCanMove() throws Exception {
        Path game = elsewhere.resolve("game");
        editor.createProject(game.resolve("level"));
        editor.addArchetype(new org.xast.kebab.archetype.definition.ArchetypeDefinition("door"));

        editor.updateAssetScope(game, List.of("level/assets"), List.of(), "level/scenes");

        assertFalse(editor.isSceneOpen());
        assertEquals(game.resolve("level/scenes"), editor.projectAssets().unwrap().scenesFolder());
        assertEquals(game.resolve("kebab-project.json"), editor.currentProjectFile().unwrap());
        assertTrue(Files.readString(game.resolve("archetypes.json")).contains("door"));
    }

    @Test
    void theScenesFolderIsAProjectSettingThatCanChange() throws Exception {
        Path dir = elsewhere.resolve("custom");
        editor.createProject(dir, "levels");

        assertEquals(dir.resolve("levels"), editor.projectAssets().unwrap().scenesFolder());
        assertTrue(Files.isDirectory(dir.resolve("levels")));
        assertTrue(editor.projectAssets().unwrap().folders().contains(dir.resolve("levels")));

        editor.updateAssetScope(dir, List.of("assets", "models", "levels"), List.of(), ".");

        assertEquals(dir, editor.projectAssets().unwrap().scenesFolder());
        assertTrue(Files.readString(dir.resolve("kebab-project.json")).contains("\"scenesFolder\" : \".\""));
    }

    @Test
    void greyboxSizeMustBeFiniteAndPositiveAndOnlyAGreyboxHasOne() {
        SceneNode box = editor.spawnGreybox(org.xast.kebab.scene.model.GreyboxShape.BOX, Vec3.zero());
        var start = org.xast.kebab.scene.model.Greybox.of(box).unwrap();

        for (Vec3 bad : List.of(new Vec3(0, 1, 1), new Vec3(1, -1, 1), new Vec3(1, 1, Float.NaN))) {
            assertThrows(IllegalArgumentException.class, () -> editor.updateGreybox(box,
                new org.xast.kebab.scene.model.Greybox(start.shape(), bad, start.color())));
        }
        assertEquals(start.size(), org.xast.kebab.scene.model.Greybox.of(box).unwrap().size());

        SceneNode empty = editor.spawnEmpty(Vec3.zero());
        assertThrows(IllegalArgumentException.class, () -> editor.updateGreybox(empty, start));
        assertTrue(org.xast.kebab.scene.model.Greybox.of(empty).isNone());
    }

    private static void assertSamePlace(org.xast.kebab.math.Mat4 expected, org.xast.kebab.math.Mat4 actual) {
        float[] e = expected.toArray();
        float[] a = actual.toArray();
        for (int k = 0; k < 16; k++) {
            assertEquals(e[k], a[k], 1e-4f, "matrix element " + k);
        }
    }

    @Test
    void reparentingKeepsTheObjectWhereItIsInTheWorld() {
        SceneNode parent = editor.spawnEmpty(new Vec3(10, 0, 0));
        parent.getTransform().setRotation(org.xast.kebab.math.Quat.fromAxisAngle(Vec3.unitY(), 1.2f));
        parent.getTransform().setScale(2f);
        SceneNode child = editor.spawnEmpty(new Vec3(3, 4, 5));
        var before = child.worldMatrix();

        editor.reparent(List.of(child), org.xast.kebab.utils.Maybe.some(parent), Integer.MAX_VALUE);

        assertSame(parent, child.getParent().unwrap());
        assertSamePlace(before, child.worldMatrix());

        editor.reparent(List.of(child), org.xast.kebab.utils.Maybe.none(), Integer.MAX_VALUE);
        assertTrue(child.getParent().isNone());
        assertSamePlace(before, child.worldMatrix());
    }

    @Test
    void reparentingIsOneUndoableStep() {
        SceneNode parent = editor.spawnEmpty(Vec3.zero());
        SceneNode child = editor.spawnEmpty(Vec3.one());

        editor.reparent(List.of(child), org.xast.kebab.utils.Maybe.some(parent), 0);
        editor.history().undo();

        SceneNode restored = editor.sceneNodes().stream().filter(n -> n.getId().equals(child.getId())).findFirst().orElseThrow();
        assertTrue(restored.getParent().isNone());
        editor.history().redo();
        SceneNode redone = editor.sceneNodes().stream().filter(n -> n.getId().equals(child.getId())).findFirst().orElseThrow();
        assertEquals(parent.getId(), redone.getParent().unwrap().getId());
    }

    @Test
    void anObjectCannotGoIntoItsOwnChildren() {
        SceneNode parent = editor.spawnEmpty(Vec3.zero());
        SceneNode child = editor.spawnEmpty(Vec3.one());
        editor.reparent(List.of(child), org.xast.kebab.utils.Maybe.some(parent), 0);

        assertThrows(IllegalArgumentException.class,
            () -> editor.reparent(List.of(parent), org.xast.kebab.utils.Maybe.some(child), 0));
        assertThrows(IllegalArgumentException.class,
            () -> editor.reparent(List.of(parent), org.xast.kebab.utils.Maybe.some(parent), 0));
        assertTrue(parent.getParent().isNone());
    }

    @Test
    void droppingBetweenSiblingsReordersThem() {
        SceneNode a = editor.spawnEmpty(Vec3.zero());
        SceneNode b = editor.spawnEmpty(Vec3.zero());
        SceneNode c = editor.spawnEmpty(Vec3.zero());
        SceneNode d = editor.spawnEmpty(Vec3.zero());
        var none = org.xast.kebab.utils.Maybe.<SceneNode>none();

        // Dropped before d, counted before a and b leave their places
        editor.reparent(List.of(b, a), none, 3);
        assertEquals(List.of(c, a, b, d), editor.scene().unwrap().getRootNodes());

        editor.reparent(List.of(d), none, 0);
        assertEquals(List.of(d, c, a, b), editor.scene().unwrap().getRootNodes());
    }

    @Test
    void childrenMoveWithTheirParent() {
        SceneNode group = editor.spawnEmpty(Vec3.zero());
        SceneNode child = editor.spawnEmpty(Vec3.one());
        SceneNode target = editor.spawnEmpty(new Vec3(5, 0, 0));
        editor.reparent(List.of(child), org.xast.kebab.utils.Maybe.some(group), 0);

        editor.reparent(List.of(group, child), org.xast.kebab.utils.Maybe.some(target), 0);

        assertSame(target, group.getParent().unwrap());
        assertSame(group, child.getParent().unwrap());
    }

    private ArchetypeInstance link(SceneNode from, SceneNode to) throws Exception {
        var definition = editor.archetypes().stream().filter(d -> d.getId().equals("link")).findFirst()
            .orElse(null);
        if (definition == null) {
            definition = new org.xast.kebab.archetype.definition.ArchetypeDefinition("link")
                .withParam("target", org.xast.kebab.archetype.ParamType.NODE_REF);
            editor.addArchetype(definition);
        }
        ArchetypeInstance instance = new ArchetypeInstance(definition);
        editor.attachArchetype(from, instance);
        editor.setArchetypeParam(instance, "target", org.xast.kebab.utils.Maybe.some(to.getId().value().toString()));
        return instance;
    }

    @Test
    void duplicatingPutsACopyRightAfterTheOriginalAndSelectsIt() {
        SceneNode first = editor.spawnGreybox(GreyboxShape.BOX, new Vec3(1, 0, 2));
        SceneNode last = editor.spawnEmpty(Vec3.zero());
        editor.selection().select(first);

        List<SceneNode> copies = editor.duplicateSelection();

        SceneNode copy = copies.getFirst();
        assertEquals(List.of(first, copy, last), editor.scene().unwrap().getRootNodes());
        assertEquals(List.of(copy), editor.selection().nodes());
        assertFalse(copy.getId().equals(first.getId()));
        assertEquals(first.getName(), copy.getName());
        assertTrue(first.getTransform().getTranslation().approxEquals(copy.getTransform().getTranslation(), 1e-6f));
        assertEquals(org.xast.kebab.scene.model.Greybox.of(first).unwrap(), org.xast.kebab.scene.model.Greybox.of(copy).unwrap());

        // The copy is its own object
        copy.getTransform().getTranslation().set(9, 9, 9);
        assertTrue(first.getTransform().getTranslation().approxEquals(new Vec3(1, 0.5f, 2), 1e-6f));
    }

    @Test
    void duplicatingAGroupCopiesItsChildrenAndPointsInnerReferencesAtTheCopies() throws Exception {
        SceneNode group = editor.spawnEmpty(Vec3.zero());
        SceneNode door = editor.spawnEmpty(Vec3.one());
        SceneNode outside = editor.spawnEmpty(new Vec3(5, 0, 0));
        editor.reparent(List.of(door), org.xast.kebab.utils.Maybe.some(group), 0);
        ArchetypeInstance inner = link(group, door);
        link(door, outside);
        editor.selection().select(group);

        SceneNode copy = editor.duplicateSelection().getFirst();

        SceneNode copiedDoor = copy.getChildren().getFirst();
        assertFalse(copiedDoor.getId().equals(door.getId()));
        ArchetypeInstance copiedInner = (ArchetypeInstance) copy.getArchetypes().getFirst();
        assertEquals(org.xast.kebab.utils.Maybe.some(copiedDoor.getId().value().toString()), copiedInner.get("target"));
        assertEquals(org.xast.kebab.utils.Maybe.some(door.getId().value().toString()), inner.get("target"));
        ArchetypeInstance copiedOuter = (ArchetypeInstance) copiedDoor.getArchetypes().getFirst();
        assertEquals(org.xast.kebab.utils.Maybe.some(outside.getId().value().toString()), copiedOuter.get("target"));
    }

    @Test
    void pastingMovesTheCopiesSidewaysToTheTargetKeepingTheirHeight() {
        SceneNode a = editor.spawnEmpty(new Vec3(0, 2, 0));
        SceneNode b = editor.spawnEmpty(new Vec3(4, 3, 0));
        editor.selection().set(List.of(a, b));
        assertEquals(2, editor.copySelection());

        List<SceneNode> pasted = editor.paste(new Vec3(100, 0, 50));

        assertEquals(2, pasted.size());
        assertEquals(pasted, editor.selection().nodes());
        assertTrue(new Vec3(98, 2, 50).approxEquals(pasted.get(0).getTransform().getTranslation(), 1e-4f));
        assertTrue(new Vec3(102, 3, 50).approxEquals(pasted.get(1).getTransform().getTranslation(), 1e-4f));

        // Each paste makes new objects
        List<SceneNode> again = editor.paste(Vec3.zero());
        assertFalse(again.getFirst().getId().equals(pasted.getFirst().getId()));
        assertEquals(6, editor.sceneNodes().size());
    }

    @Test
    void pasteIsOneUndoableStepAndCopiesAreTakenWhenCopying() {
        SceneNode a = editor.spawnEmpty(Vec3.zero());
        a.setName("Crate");
        editor.selection().select(a);
        editor.copySelection();
        a.setName("Renamed later");

        SceneNode pasted = editor.paste(Vec3.zero()).getFirst();
        assertEquals("Crate", pasted.getName());

        editor.history().undo();
        assertEquals(1, editor.sceneNodes().size());
    }

    @Test
    void theClipboardSurvivesSwitchingScenesButNotProjects() throws Exception {
        Files.copy(projectDir.resolve("level.gltf"), projectDir.resolve("assets/town.gltf"));
        editor.selection().select(editor.spawnEmpty(Vec3.zero()));
        editor.copySelection();
        editor.saveProject();

        editor.openScene(projectDir.resolve("assets/town.gltf"));
        assertTrue(editor.canPaste());
        assertEquals(1, editor.paste(Vec3.zero()).size());

        editor.createProject(elsewhere.resolve("other"));

        editor.createScene("", "level");
        assertFalse(editor.canPaste());
    }
}
