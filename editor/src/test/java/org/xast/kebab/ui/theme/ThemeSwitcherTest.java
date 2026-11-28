package org.xast.kebab.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xast.kebab.ui.NumberSpinner;
import org.xast.kebab.utils.Fonts;

class ThemeSwitcherTest {

    @TempDir
    Path dir;

    private LookAndFeel previous;

    @BeforeEach
    void rememberLook() {
        previous = UIManager.getLookAndFeel();
    }

    @AfterEach
    void restoreLook() throws Exception {
        UIManager.setLookAndFeel(previous);
    }

    private ThemeSwitcher started() {
        ThemeSwitcher themes = new ThemeSwitcher(dir.resolve("settings").resolve(ThemeSwitcher.FILE));
        themes.installSaved();
        return themes;
    }

    private void select(ThemeSwitcher themes, Theme theme) throws Exception {
        SwingUtilities.invokeAndWait(() -> themes.select(theme));
    }

    @Test
    void startsWithTheDefaultAndWritesNothing() {
        assertEquals(Theme.GITHUB_DARK, started().current());
        assertFalse(Files.exists(dir.resolve("settings")));
    }

    @Test
    void remembersTheChoice() throws Exception {
        select(started(), Theme.MINI_DE);

        assertEquals(Theme.MINI_DE, started().current());
        assertEquals("Intellij (Mini DE)", UIManager.getLookAndFeel().getName());
    }

    @Test
    void unknownThemeFallsBackToTheDefault() throws Exception {
        Files.createDirectories(dir.resolve("settings"));
        Files.writeString(dir.resolve("settings").resolve(ThemeSwitcher.FILE), "NO_SUCH_THEME");

        assertEquals(Theme.DEFAULT, started().current());
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    void everyThemeStylesTheMenuButtons(Theme theme) {
        theme.install();

        assertNotNull(UIManager.get("[style]Button.menuButton"));
    }

    @Test
    void editorSettingsOfOneThemeDoNotLeakIntoAnother() throws Exception {
        ThemeSwitcher themes = started();
        assertEquals(3, UIManager.getInt("Component.focusWidth"));

        select(themes, Theme.MINI_DE);
        assertEquals(0, UIManager.getInt("Component.focusWidth"));

        select(themes, Theme.GITHUB_DARK);
        assertEquals(3, UIManager.getInt("Component.focusWidth"));
    }

    @Test
    void numberSpinnersKeepTheirAlignment() throws Exception {
        Fonts.installKebabFonts();
        started();
        NumberSpinner spinner = new NumberSpinner(1, 0.1);
        int before = spinner.field().getHorizontalAlignment();

        Theme.MINI_DE.install();
        SwingUtilities.invokeAndWait(() -> SwingUtilities.updateComponentTreeUI(spinner));

        assertEquals(before, spinner.field().getHorizontalAlignment());
    }
}
