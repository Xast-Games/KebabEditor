package org.xast.kebab;

import javax.swing.SwingUtilities;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.project.EditorSessions;
import org.xast.kebab.project.FileRecentProjects;
import org.xast.kebab.ui.MainWindow;
import org.xast.kebab.ui.theme.ThemeSwitcher;
import org.xast.kebab.utils.AppIdentity;
import org.xast.kebab.utils.ConfigFolder;
import org.xast.kebab.utils.KebabStyle;

public class KebabApplication {

    public static void main(String[] args) {
        // Before any window exists, so the dock matches the windows to kebab-editor.desktop.
        AppIdentity.setWindowClass();

        // Exceptions nobody caught, including on the Swing thread, end up in the Logs panel instead of vanishing.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            LogService.get().error("Unhandled error in thread '" + thread.getName() + "'", error);
            error.printStackTrace();
        });

        SwingUtilities.invokeLater(() -> {
            KebabStyle.setCurrent(KebabStyle.defaultStyle());

            Editor editor = EditorSessions.create(new FileRecentProjects());
            ThemeSwitcher themes = new ThemeSwitcher(ConfigFolder.file(ThemeSwitcher.FILE));
            themes.installSaved();

            MainWindow window = new MainWindow(editor, themes);
            LogService.get().info("Kebab Editor started");

            window.show();
        });
    }
}
