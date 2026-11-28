package org.xast.kebab.ui;

import java.awt.Component;

import javax.swing.JOptionPane;

import org.xast.kebab.editor.Deletion;
import org.xast.kebab.editor.Editor;

/** Deletes the selected objects, asking first when children or references go with them. */
public final class DeleteSelected {

    private DeleteSelected() {}

    public static void run(Component parent, Editor editor) {
        if (editor.selection().nodes().isEmpty()) {
            return;
        }

        Deletion deletion = editor.planDeletion(editor.selection().nodes());

        if (!deletion.needsConfirmation() || confirm(parent, deletion)) {
            editor.delete(deletion);
        }
    }

    private static boolean confirm(Component parent, Deletion deletion) {
        StringBuilder message = new StringBuilder();

        if (deletion.childCount() > 0) {
            message.append("This also deletes ").append(deletion.childCount()).append(" child object(s).\n");
        }
        if (!deletion.references().isEmpty()) {
            message.append(deletion.references().size())
                .append(" reference(s) in other objects point to what is being deleted and will be cleared.");
        }

        String[] options = { "Delete", "Cancel" };
        int choice = JOptionPane.showOptionDialog(
            parent,
            message.toString().strip(),
            "Delete object",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[1]
        );

        return choice == 0;
    }
}
