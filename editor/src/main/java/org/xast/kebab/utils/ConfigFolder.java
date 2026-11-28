package org.xast.kebab.utils;

import java.nio.file.Path;

/** Where the editor keeps the user's own settings: the folder each OS expects them in. */
public final class ConfigFolder {

    private static final String NAME = "kebab";

    private ConfigFolder() {}

    public static Path file(String name) {
        return folder().resolve(name);
    }

    private static Path folder() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, NAME);
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", NAME);
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, NAME);
        }

        return Path.of(home, ".config", NAME);
    }
}
