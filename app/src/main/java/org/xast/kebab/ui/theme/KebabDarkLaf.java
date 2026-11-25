package org.xast.kebab.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The "Intellij (Mini DE)" theme: mini-de's "intellij" variant (IntelliJ New UI Dark colours,
 * borderless rounded controls, round window buttons) on top of FlatLaf's dark theme.
 *
 * Colours and sizes live in {@code KebabDarkLaf.properties}, which FlatLaf finds by this
 * class's name and package. Only what properties cannot express is code: {@link RoundWindowIcon}.
 */
public final class KebabDarkLaf extends FlatDarkLaf {

    public static final String NAME = "Intellij (Mini DE)";

    public static boolean setup() {
        return setup(new KebabDarkLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Kebab Editor dark theme, after mini-de's intellij variant";
    }
}
