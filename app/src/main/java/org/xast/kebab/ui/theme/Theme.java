package org.xast.kebab.ui.theme;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import org.xast.kebab.utils.Maybe;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme;

/** The looks the user can choose from in View > Theme. */
public enum Theme {

    GITHUB_DARK("GitHub Dark", Theme::gitHubDark),
    MINI_DE(KebabDarkLaf.NAME, KebabDarkLaf::new);

    public static final Theme DEFAULT = GITHUB_DARK;

    private final String title;
    private final Supplier<FlatLaf> laf;

    Theme(String title, Supplier<FlatLaf> laf) {
        this.title = title;
        this.laf = laf;
    }

    public String title() {
        return title;
    }

    /** Makes this the look of components created from now on; false if it could not be installed. */
    public boolean install() {
        return FlatLaf.setup(laf.get());
    }

    public static Maybe<Theme> byName(String name) {
        return Maybe.ofNullable(Arrays.stream(values())
            .filter(theme -> theme.name().equals(name))
            .findFirst()
            .orElse(null));
    }

    // A ready-made theme, so what the editor adds to it is listed here rather than in a properties file.
    private static FlatLaf gitHubDark() {
        FlatLaf laf = new FlatMTGitHubDarkIJTheme();
        laf.setExtraDefaults(Map.of(
            "Component.focusWidth", "3",
            "Component.focusColor", "#ac6c40ac",
            "[style]Button.menuButton", "background: #21252a"
        ));
        return laf;
    }
}
