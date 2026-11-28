package org.xast.kebab.utils;

import java.awt.Font;

import javax.swing.UIManager;

/**
 * Kebab's panels take their fonts from the Look and Feel, so a host that embeds them gets its own
 * fonts there. The standalone app puts Kebab's fonts into the Look and Feel with {@link #installKebabFonts()}.
 */
public final class Fonts {

    private static final String DEFAULT_KEY = "defaultFont";
    private static final String MONOSPACED_KEY = "monospaced.font";
    private static final String UI_FONT = "fonts/IBM_Plex_Sans/IBMPlexSans-VariableFont_wdth,wght.ttf";
    private static final String MONOSPACED_FONT = "fonts/IBM_Plex_Mono/IBMPlexMono-Regular.ttf";

    private Fonts() {}

    /** Call before the Look and Feel is set up. */
    public static void installKebabFonts() {
        UiAssetLoader loader = UiAssetLoader.getInstance();
        UIManager.put(DEFAULT_KEY, loader.loadFont(UI_FONT, 16f, false));
        UIManager.put(MONOSPACED_KEY, loader.loadFont(MONOSPACED_FONT, 14f, true));
    }

    public static Font ui() {
        Font font = UIManager.getFont(DEFAULT_KEY);
        return font != null ? font : UIManager.getFont("Label.font");
    }

    /** For numbers and raw values. */
    public static Font mono() {
        Font font = UIManager.getFont(MONOSPACED_KEY);
        return font != null ? font : new Font(Font.MONOSPACED, Font.PLAIN, ui().getSize());
    }
}
