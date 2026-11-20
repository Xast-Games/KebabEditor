package org.xast.kebab.ui.menu;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.xast.kebab.editor.Editor;

/**
 * Guard for actions that edit the scene. They stay enabled without a scene so
 * their shortcuts explain what is missing instead of silently doing nothing.
 * They are refused during a playtest, which plays the scene as it was when it started.
 */
final class SceneRequired {

    private SceneRequired() {}

    static boolean check(JFrame frame, Editor editor) {
        if (editor.isPlaytesting()) {
            JOptionPane.showMessageDialog(
                frame,
                "The level is being playtested. Stop the playtest (Esc) to edit it.",
                "Playtest running",
                JOptionPane.INFORMATION_MESSAGE
            );
            return false;
        }

        if (editor.isSceneOpen()) {
            return true;
        }

        if (editor.isProjectOpen()) {
            JOptionPane.showMessageDialog(
                frame,
                "Create or open a scene first (File > New scene, or a scene in the asset explorer).",
                "No scene open",
                JOptionPane.INFORMATION_MESSAGE
            );
            return false;
        }

        JOptionPane.showMessageDialog(
            frame,
            "Open or create a project first (File > Open project / Create new project).",
            "No project open",
            JOptionPane.INFORMATION_MESSAGE
        );
        return false;
    }

    static Runnable guard(JFrame frame, Editor editor, Runnable action) {
        return () -> {
            if (check(frame, editor)) {
                action.run();
            }
        };
    }
}
