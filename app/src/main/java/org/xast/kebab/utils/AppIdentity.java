package org.xast.kebab.utils;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * How the desktop recognizes the editor: its window class and its icon.
 *
 * On Linux (X11 and XWayland) a dock or taskbar finds the .desktop file of a window
 * through the window's WM_CLASS, which Java sets to the main class name by default.
 * The installer's kebab-editor.desktop says {@code StartupWMClass=kebab-editor},
 * so the class is set to match before the first window opens.
 */
public final class AppIdentity {

    /** The application id: the .desktop file name, the launcher name and the window class. */
    public static final String ID = "kebab-editor";

    private static final String LOGO = "img/logo.svg";
    private static final int[] ICON_SIZES = { 16, 24, 32, 48, 64, 128, 256 };

    private static List<Image> icons;

    private AppIdentity() {}

    /**
     * Sets the X11 window class of every window opened afterwards. Call it before the
     * first window opens. Needs {@code --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED};
     * without it, or on other toolkits, nothing changes and the app still runs.
     */
    public static void setWindowClass() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        if (!toolkit.getClass().getName().equals("sun.awt.X11.XToolkit")) {
            return;
        }

        try {
            Field appClassName = toolkit.getClass().getDeclaredField("awtAppClassName");
            appClassName.setAccessible(true);
            appClassName.set(null, ID);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Only the dock icon is affected, so the app should still start.
            System.err.println("Could not set the window class to '" + ID + "': " + e);
        }
    }

    /** The logo at the sizes window managers ask for, from title bars to the Alt+Tab switcher. */
    public static synchronized List<Image> icons() {
        if (icons == null) {
            FlatSVGIcon logo = new FlatSVGIcon(LOGO);
            icons = java.util.Arrays.stream(ICON_SIZES)
                .<Image>mapToObj(size -> render(logo, size))
                .toList();
        }
        return icons;
    }

    public static void applyIcon(Window window) {
        window.setIconImages(icons());
    }

    private static BufferedImage render(FlatSVGIcon logo, int size) {
        FlatSVGIcon sized = logo.derive(size, size);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        sized.paintIcon(null, g, 0, 0);
        g.dispose();
        return image;
    }
}
