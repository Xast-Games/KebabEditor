package org.xast.kebab.ui;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.JOptionPane;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.scene.resolve.PrefabResult;
import org.xast.kebab.utils.ErrorDialogs;
import org.xast.kebab.utils.Maybe;
import org.xast.kebab.utils.Params;

/** Moves the main editor to another scene of the project, asking first about unsaved edits. */
public final class SceneNavigator {

    private final Component parent;
    private final Editor editor;

    public SceneNavigator(Component parent, Editor editor) {
        this.parent = parent;
        this.editor = editor;
    }

    public void open(Path scene) {
        Path target = scene.toAbsolutePath().normalize();
        if (editor.currentSceneFile().isSomeAnd(target::equals)) {
            return;
        }
        if (!UnsavedChanges.confirm(parent, editor, "open another scene", "open")) {
            return;
        }

        try {
            editor.openScene(target);
        } catch (SceneException e) {
            ErrorDialogs.show(parent, "Scene error, cannot open " + target.getFileName(), e);
        } catch (IOException e) {
            ErrorDialogs.show(parent, "I/O error, cannot open " + target.getFileName(), e);
        }
    }

    public static boolean isPrefab(SceneNode node) {
        return prefabRef(node).isSome();
    }

    /** Opens the scene a node's prefab_ref points at, e.g. a location placed on a world map. */
    public void openPrefab(SceneNode node) {
        Maybe<ArchetypeInstance> prefab = prefabRef(node);
        if (prefab.isNone()) {
            JOptionPane.showMessageDialog(
                parent,
                "Select an object with a Prefab Reference first.",
                "Open prefab",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        String path = Params.text(prefab.unwrap(), "path").unwrapOr("");
        Maybe<PrefabResult> result = editor.prefabResolver().andThen(resolver ->
            editor.currentSceneFile().map(scene -> resolver.resolve(scene, path)));
        if (result.isNone()) {
            return;
        }

        switch (result.unwrap()) {
            case PrefabResult.LoadedPrefab loaded -> open(loaded.path());
            case PrefabResult.BrokenPrefab broken -> JOptionPane.showMessageDialog(
                parent,
                "Cannot open the prefab of '" + node.getName() + "': " + broken.detail(),
                "Open prefab",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static Maybe<ArchetypeInstance> prefabRef(SceneNode node) {
        return Maybe.fromOptional(node.getArchetypes().stream()
            .filter(archetype -> archetype instanceof ArchetypeInstance instance
                && instance.archetypeId().equals(BuiltinArchetypes.PREFAB_REF))
            .map(ArchetypeInstance.class::cast)
            .findFirst());
    }
}
