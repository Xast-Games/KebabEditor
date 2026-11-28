package org.xast.kebab.ui.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;

import org.junit.jupiter.api.Test;
import org.xast.kebab.assets.AssetService.Entry;
import org.xast.kebab.assets.AssetType;

class AssetDragSourceTest {

    private final DefaultListModel<Entry> entries = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(entries);
    private final AssetDragSource source = new AssetDragSource(list);

    @Test
    void modelsAndScenesTravelAsAFileList() throws Exception {
        for (AssetType type : List.of(AssetType.MODEL, AssetType.SCENE)) {
            Path file = Path.of("/project/" + type + ".gltf");
            select(new Entry(file, type));

            Object data = source.createTransferable(list).getTransferData(DataFlavor.javaFileListFlavor);

            assertEquals(List.of(new File(file.toString())), data);
        }
    }

    @Test
    void otherAssetsAndNoSelectionAreNotDragged() {
        assertNull(source.createTransferable(list));
        for (AssetType type : List.of(AssetType.FOLDER, AssetType.IMAGE, AssetType.JSON, AssetType.FILE)) {
            select(new Entry(Path.of("/project/thing"), type));
            assertNull(source.createTransferable(list), type.toString());
        }
    }

    private void select(Entry entry) {
        entries.clear();
        entries.addElement(entry);
        list.setSelectedIndex(0);
    }
}
