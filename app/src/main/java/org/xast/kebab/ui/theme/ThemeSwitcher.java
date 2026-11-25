package org.xast.kebab.ui.theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.xast.kebab.logging.LogService;
import org.xast.kebab.utils.Maybe;

import com.formdev.flatlaf.FlatLaf;

/** The current theme, remembered between runs. */
public final class ThemeSwitcher {

    public static final String FILE = "theme.txt";

    private final Path file;
    private Theme current = Theme.DEFAULT;

    /** @param file where the choice is kept */
    public ThemeSwitcher(Path file) {
        this.file = file;
    }

    public Theme current() {
        return current;
    }

    /** Installs the remembered theme. Call it before creating any component. */
    public void installSaved() {
        current = load();

        if (!current.install()) {
            LogService.get().warn("Cannot install the theme '" + current.title() + "'");
        }
    }

    /** Restyles every open window and remembers the choice. */
    public void select(Theme theme) {
        if (theme == current) {
            return;
        }
        if (!theme.install()) {
            LogService.get().warn("Cannot install the theme '" + theme.title() + "'");
            return;
        }

        current = theme;
        FlatLaf.updateUI();
        save();
    }

    private Theme load() {
        if (!Files.isRegularFile(file)) {
            return Theme.DEFAULT;
        }
        try {
            String name = Files.readString(file).strip();
            Maybe<Theme> saved = Theme.byName(name);
            if (saved.isNone()) {
                LogService.get().warn("Unknown theme '" + name + "' in " + file + ", using '" + Theme.DEFAULT.title() + "'");
            }
            return saved.unwrapOr(Theme.DEFAULT);
        } catch (IOException e) {
            LogService.get().error("Cannot read " + file, e);
            return Theme.DEFAULT;
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, current.name());
        } catch (IOException e) {
            LogService.get().error("Cannot save " + file, e);
        }
    }
}
