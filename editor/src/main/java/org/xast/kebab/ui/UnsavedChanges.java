package org.xast.kebab.ui;

import java.awt.Component;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.utils.ErrorDialogs;

/** Asks what to do with unsaved scene edits before an action would drop them. */
public final class UnsavedChanges {

    private UnsavedChanges() {}

    /**
     * @param action what is about to happen, e.g. "open another scene"
     * @param verb   the action's verb for the buttons, e.g. "open" for "Save and open"
     * @return true when it is fine to go on: nothing to save, saved, or the user chose to discard
     */
    public static boolean confirm(Component parent, Editor editor, String action, String verb) {
        if (!editor.hasUnsavedChanges()) {
            return true;
        }

        String scene = editor.currentSceneFile()
            .map(file -> "'" + file.getFileName() + "'")
            .unwrapOr("The current scene");
        Object[] options = { "Save and " + verb, "Discard and " + verb, "Cancel" };
        int answer = JOptionPane.showOptionDialog(
            parent,
            scene + " is not saved.\nIf you " + action + ", your changes will be lost. Proceed?",
            "Unsaved changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]
        );

        return switch (answer) {
            case JOptionPane.YES_OPTION -> save(parent, editor);
            case JOptionPane.NO_OPTION -> true;
            default -> false; // Cancel, Esc or the dialog's close button
        };
    }

    // A failed save cancels the action, so the edits are not lost.
    private static boolean save(Component parent, Editor editor) {
        try {
            editor.saveProject();
            return true;
        } catch (SceneFormatException e) {
            ErrorDialogs.show(parent, "Scene format error, cannot save project", e);
        } catch (IOException e) {
            ErrorDialogs.show(parent, "I/O error, cannot save project", e);
        }
        return false;
    }
}
