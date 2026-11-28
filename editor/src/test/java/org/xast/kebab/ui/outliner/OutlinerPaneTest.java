package org.xast.kebab.ui.outliner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.project.EditorSessions;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.ui.SceneNavigator;
import org.xast.kebab.utils.Fonts;

class OutlinerPaneTest {

    @TempDir Path dir;
    Editor editor;
    OutlinerPane pane;
    final List<List<SceneNode>> framed = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        editor = EditorSessions.create();
        editor.createProject(dir);
        editor.createScene("", "scene");
        SwingUtilities.invokeAndWait(() -> {
            Fonts.installKebabFonts();
            pane = new OutlinerPane(editor, framed::add, new SceneNavigator(null, editor));
        });
    }

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(pane::close);
    }

    void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});
    }

    @Test
    void showsTheSceneAndFollowsEdits() throws Exception {
        SceneNode a = editor.spawnEmpty(Vec3.zero());
        SceneNode b = editor.spawnEmpty(Vec3.one());
        flush();
        assertEquals(List.of(a, b), pane.displayedNodes());

        editor.renameNode(a, "Castle");
        flush();
        assertEquals("Castle", label(0));
    }

    String label(int row) throws Exception {
        String[] text = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            Object value = pane.tree().getPathForRow(row).getLastPathComponent();
            var component = pane.tree().getCellRenderer()
                .getTreeCellRendererComponent(pane.tree(), value, false, false, true, row, false);
            text[0] = ((javax.swing.JLabel) component).getText();
        });
        return text[0];
    }

    @Test
    void selectionGoesBothWays() throws Exception {
        SceneNode a = editor.spawnEmpty(Vec3.zero());
        SceneNode b = editor.spawnEmpty(Vec3.one());
        flush();

        // spawning selects the new node, and the tree follows
        SwingUtilities.invokeAndWait(() -> {
            TreePath selected = pane.tree().getSelectionPath();
            assertSame(b, ((javax.swing.tree.DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject());
            pane.tree().setSelectionRow(0);
        });
        assertEquals(List.of(a), editor.selection().nodes());
    }

    @Test
    void theSceneRootIsNamedAfterTheFileAndIsNotAnObject() throws Exception {
        SceneNode a = editor.spawnEmpty(Vec3.zero());
        SwingUtilities.invokeAndWait(pane::showSceneRoot);
        flush();

        SwingUtilities.invokeAndWait(() -> {
            Object root = ((javax.swing.tree.DefaultMutableTreeNode) pane.tree().getPathForRow(0).getLastPathComponent()).getUserObject();
            assertEquals(editor.currentSceneFile().unwrap().getFileName().toString(), root);
            pane.tree().setSelectionRows(new int[] { 0, 1 });
        });
        assertEquals(List.of(a), editor.selection().nodes());
    }

    @Test
    void enterFramesTheSelection() throws Exception {
        SceneNode a = editor.spawnEmpty(Vec3.zero());
        flush();

        SwingUtilities.invokeAndWait(() -> pane.tree().getActionMap().get("frameSelected")
            .actionPerformed(new ActionEvent(pane.tree(), ActionEvent.ACTION_PERFORMED, "")));

        assertEquals(List.of(List.of(a)), framed);
    }

    @Test
    void movedObjectsShowUnderTheirExpandedParent() throws Exception {
        SceneNode parent = editor.spawnEmpty(Vec3.zero());
        SceneNode child = editor.spawnEmpty(Vec3.one());
        flush();

        SwingUtilities.invokeAndWait(() ->
            pane.reparent(List.of(child), org.xast.kebab.utils.Maybe.some(parent), Integer.MAX_VALUE));
        flush();

        assertSame(parent, child.getParent().unwrap());
        SwingUtilities.invokeAndWait(() -> assertEquals(2, pane.tree().getRowCount()));
        assertEquals(List.of(parent, child), pane.displayedNodes());
    }
}
