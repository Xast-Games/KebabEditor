package org.xast.kebab.ui.assets;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

import org.xast.kebab.assets.AssetService.Entry;
import org.xast.kebab.assets.AssetType;

/**
 * Lets models and scenes be dragged out of the asset explorer, e.g. into the viewport.
 * They travel as a plain file list, the flavour file managers use too.
 */
final class AssetDragSource extends TransferHandler {

    private final JList<Entry> list;

    AssetDragSource(JList<Entry> list) {
        this.list = list;
    }

    @Override
    public int getSourceActions(JComponent component) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent component) {
        Entry entry = list.getSelectedValue();
        if (entry == null || (entry.type() != AssetType.MODEL && entry.type() != AssetType.SCENE)) {
            return null;
        }
        return new FileTransferable(entry.path().toFile());
    }

    private record FileTransferable(File file) implements Transferable {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.javaFileListFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return List.of(file);
        }
    }
}
