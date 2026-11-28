package org.xast.kebab.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.xast.kebab.logging.LogService;
import org.xast.kebab.utils.ConfigFolder;

public class FileRecentProjects implements RecentProjects {

    public static final String RECENT_FILE = "recent.txt";
    private static final int MAX = 10;

    private final Path configPath = ConfigFolder.file(RECENT_FILE);
    private final List<Path> paths = new ArrayList<>();

    public FileRecentProjects() {
        load();
    }

    @Override
    public List<Path> paths() {
        return List.copyOf(paths);
    }

    @Override
    public void add(Path file) {
        Path path = file.toAbsolutePath().normalize();
        paths.remove(path);
        paths.add(0, path);

        if (paths.size() > MAX) {
            paths.subList(MAX, paths.size()).clear();
        }

        save();
    }

    @Override
    public void remove(Path file) {
        paths.remove(file.toAbsolutePath().normalize());
        save();
    }

    @Override
    public void clear() {
        paths.clear();
        save();
    }

    private void load() {
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(configPath)) {
                if (!line.isBlank() && Files.isRegularFile(Path.of(line.trim()))) {
                    paths.add(Path.of(line.trim()));
                }
            }
        } catch (IOException e) {
            LogService.get().error("Cannot read " + configPath, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, paths.stream().map(Path::toString).toList());
        } catch (IOException e) {
            LogService.get().error("Cannot save " + configPath, e);
        }
    }
}
