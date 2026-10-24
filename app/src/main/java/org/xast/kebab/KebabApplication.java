package org.xast.kebab;

import javax.swing.SwingUtilities;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.project.EditorSessions;
import org.xast.kebab.project.FileRecentProjects;
import org.xast.kebab.ui.MainWindow;
import org.xast.kebab.utils.KebabStyle;

public class KebabApplication {

    public static final String APP_NAME = "KebabEditor";
    public static final String AUTHORS = "Xast Games";
    public static final String DOCUMENTATION_URL = "https://kebab.xast.org/";
    public static final int FIRST_YEAR = 2025;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KebabStyle.setCurrent(KebabStyle.defaultStyle());

            Editor editor = EditorSessions.create(new FileRecentProjects());
            MainWindow window = new MainWindow(editor);
            LogService.get().info("Kebab Editor started");

            window.show();
        });
    }
}
