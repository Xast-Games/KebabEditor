package org.xast.kebab.embed;

import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.xast.kebab.logging.LogService;
import org.xast.kebab.project.ProjectConfigStore;
import org.xast.kebab.scene.error.ModelLoadException;
import org.xast.kebab.scene.resolve.CachingModelResolver;
import org.xast.kebab.scene.resolve.GltfMeshExtractor;
import org.xast.kebab.scene.resolve.ModelResult;
import org.xast.kebab.scene.resolve.ModelResult.BrokenReference;
import org.xast.kebab.scene.resolve.ModelResult.ResolvedModel;
import org.xast.kebab.ui.assets.ModelPreviewView;
import org.xast.kebab.ui.navigation.FreeFlightNavigation;
import org.xast.kebab.utils.Fonts;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A read-only 3D view of one model file, for the Kebab window and for hosts that embed Kebab.
 * Open and close it on the Swing thread; {@link #close()} releases its OpenGL resources.
 */
public final class ModelPreview implements AutoCloseable {

    private final ModelPreviewView view;
    private final JPanel component = new JPanel(new BorderLayout());

    public ModelPreview(ResolvedModel model) {
        view = new ModelPreviewView(model);
        view.setNavigationMode(new FreeFlightNavigation());

        JLabel help = new JLabel("Drag to look · WASD to fly · wheel to dolly · read-only preview");
        help.setFont(Fonts.ui());

        component.add(view, BorderLayout.CENTER);
        component.add(help, BorderLayout.SOUTH);
    }

    /** Reads a .glb or .gltf file and shows it; see {@link #load(Path)}. */
    public static ModelPreview open(Path file) throws ModelLoadException {
        return new ModelPreview(load(file));
    }

    /**
     * Reads a .glb or .gltf file. Textures and buffers may lie anywhere in the Kebab project
     * the file belongs to, or next to the file when it is in no project. Safe off the Swing thread.
     */
    public static ResolvedModel load(Path file) throws ModelLoadException {
        Path absolute = file.toAbsolutePath().normalize();
        ModelResult result = new CachingModelResolver(new GltfMeshExtractor(), assetRoot(absolute))
            .resolve(absolute, absolute.getFileName().toString());

        return switch (result) {
            case ResolvedModel model -> model;
            case BrokenReference broken -> throw new ModelLoadException(broken.detail());
        };
    }

    private static Path assetRoot(Path file) {
        try {
            return new ProjectConfigStore(new ObjectMapper()).assetRootAbove(file).unwrapOr(file.getParent());
        } catch (IOException e) {
            LogService.get().warn("Cannot read the project of " + file + ": " + e.getMessage());
            return file.getParent();
        }
    }

    public JComponent component() {
        return component;
    }

    @Override
    public void close() {
        view.close();
    }
}
