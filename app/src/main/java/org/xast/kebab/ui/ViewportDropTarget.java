package org.xast.kebab.ui;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.utils.Maybe;

/**
 * Takes glTF files dropped on the viewport, from the asset explorer or a file manager, and
 * places each where the cursor points: a registered scene as a prefab_ref, any other file as
 * a model_ref. Files are only referenced; one from outside the project is refused, not copied.
 */
final class ViewportDropTarget extends TransferHandler {

    private final EditorView view;
    private final Editor editor;

    ViewportDropTarget(EditorView view, Editor editor) {
        this.view = view;
        this.editor = editor;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop()
            || !support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            || !editor.isSceneOpen()
            || editor.isPlaytesting()) {
            return false;
        }
        // Copy, so a file manager never moves the file away
        if ((support.getSourceDropActions() & COPY) == 0) {
            return false;
        }
        support.setDropAction(COPY);
        // Some platforms only reveal the files on drop, so an unreadable list is accepted here
        return files(support).map(ViewportDropTarget::allGltf).unwrapOr(true);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        Maybe<List<Path>> files = files(support);
        if (files.isNone() || !allGltf(files.unwrap())) {
            return false;
        }

        Point at = support.getDropLocation().getDropPoint();
        Vec3 base = view.dropPoint(at.x, at.y);
        // Dialogs shown during the drop itself can block the platform's drag and drop
        SwingUtilities.invokeLater(() -> place(files.unwrap(), base));
        return true;
    }

    private void place(List<Path> files, Vec3 base) {
        view.requestFocusInWindow();
        for (Path file : files) {
            try {
                editor.placeAsset(file, base);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(view, e.getMessage(), "Cannot place " + file.getFileName(),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Maybe<List<Path>> files(TransferSupport support) {
        try {
            List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            return Maybe.some(files.stream().map(file -> file.toPath().toAbsolutePath().normalize()).toList());
        } catch (UnsupportedFlavorException | IOException | InvalidDnDOperationException e) {
            return Maybe.none();
        }
    }

    private static boolean allGltf(List<Path> files) {
        return !files.isEmpty() && files.stream().allMatch(file -> {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".gltf") || name.endsWith(".glb");
        });
    }
}
