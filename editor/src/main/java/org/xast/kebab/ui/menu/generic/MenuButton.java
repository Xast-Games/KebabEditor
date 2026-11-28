package org.xast.kebab.ui.menu.generic;

import java.awt.Color;
import java.awt.Insets;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;

import org.xast.kebab.utils.LucideIcon;

public class MenuButton extends JButton {
    public static final Supplier<Color> PLAY_BUTTON_COLOR = themed("MenuButton.playColor", new Color(0x87e449));
    public static final Supplier<Color> STOP_BUTTON_COLOR = themed("MenuButton.stopColor", new Color(0xf0645a));

    /** Themes style the button as {@code [style]Button.menuButton}. */
    private static final String STYLE_CLASS = "menuButton";

    private static final int ICON_SIZE = 18;

    public MenuButton(LucideIcon icon, String tooltip, Supplier<Color> color, Insets insets, Runnable action) {
        super(icon.icon(ICON_SIZE, color));

        setMargin(insets);
        putClientProperty(FlatClientProperties.STYLE_CLASS, STYLE_CLASS);
        setToolTipText(tooltip);
        addActionListener(e -> action.run());
    }

    /** Swap the icon and tooltip, e.g. between run and stop. */
    public void setAppearance(LucideIcon icon, Supplier<Color> color, String tooltip) {
        setIcon(icon.icon(ICON_SIZE, color));
        setToolTipText(tooltip);
    }

    public MenuButton(LucideIcon icon, String tooltip, Supplier<Color> color, Runnable runnable) {
        this(icon, tooltip, color, new Insets(8, 8, 8, 8), runnable);
    }

    public MenuButton(LucideIcon icon, String tooltip, Insets insets, Runnable runnable) {
        this(icon, tooltip, () -> Color.LIGHT_GRAY, insets, runnable);
    }

    public MenuButton(LucideIcon icon, String tooltip, Runnable runnable) {
        this(icon, tooltip, () -> Color.LIGHT_GRAY, new Insets(8, 8, 8, 8), runnable);
    }

    /** The theme's colour under {@code key}, looked up on every use. */
    private static Supplier<Color> themed(String key, Color fallback) {
        return () -> {
            Color color = UIManager.getColor(key);
            return color != null ? color : fallback;
        };
    }
}
