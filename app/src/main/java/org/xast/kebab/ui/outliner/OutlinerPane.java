package org.xast.kebab.ui.outliner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.ui.SceneNavigator;
import org.xast.kebab.utils.KebabStyle;

/**
 * The scene's objects as a tree. Selecting here selects in the viewport and the
 * other way round. Double-click or Enter flies the camera to an object, F frames
 * the selection, and the context menu also opens prefabs.
 */
public final class OutlinerPane extends JPanel implements AutoCloseable {

    private static final String UNNAMED = "(unnamed)";

    // What the tree shows; edits that change none of it, like a gizmo drag, keep the tree as it is
    private record Row(SceneNode node, String name, int depth) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Row row && row.node == node && row.name.equals(name) && row.depth == depth;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(node), name, depth);
        }
    }

    private final Editor editor;
    private final Consumer<List<SceneNode>> framer;
    private final SceneNavigator navigator;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Runnable listener = this::scheduleRefresh;
    private List<Row> shown = List.of();
    private boolean syncing;
    private boolean attached;
    private boolean closed;

    /**
     * @param framer points the viewport camera at nodes
     */
    public OutlinerPane(Editor editor, Consumer<List<SceneNode>> framer, SceneNavigator navigator) {
        super(new BorderLayout());
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Create OutlinerPane on the EDT");
        }
        this.editor = editor;
        this.framer = framer;
        this.navigator = navigator;
        setMinimumSize(new Dimension(240, 80));

        JLabel title = new JLabel("Outliner");
        title.setFont(KebabStyle.getCurrent().uiFont());
        title.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(title, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(KebabStyle.getCurrent().uiFont());
        tree.setCellRenderer(new Renderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        installListeners();
        add(new JScrollPane(tree), BorderLayout.CENTER);

        attach();
        refresh();
    }

    private void installListeners() {
        tree.addTreeSelectionListener(event -> {
            if (!syncing) {
                editor.selection().set(selectedNodes());
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
                    nodeAt(event).ifPresent(node -> framer.accept(List.of(node)));
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                popup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                popup(event);
            }
        });
        bind("ENTER", "frameSelected");
        bind("F", "frameSelected");
        tree.getActionMap().put("frameSelected", action(() -> framer.accept(selectedNodes())));
    }

    private void bind(String key, String name) {
        tree.getInputMap().put(KeyStroke.getKeyStroke(key), name);
    }

    private static AbstractAction action(Runnable run) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                run.run();
            }
        };
    }

    private java.util.Optional<SceneNode> nodeAt(MouseEvent event) {
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        return java.util.Optional.ofNullable(path).map(OutlinerPane::nodeOf);
    }

    private void popup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        nodeAt(event).ifPresent(node -> {
            if (!editor.selection().contains(node)) {
                editor.selection().select(node);
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem frame = new JMenuItem("Frame  (F)");
            frame.addActionListener(e -> framer.accept(List.of(node)));
            menu.add(frame);
            JMenuItem prefab = new JMenuItem("Open prefab");
            prefab.setEnabled(SceneNavigator.isPrefab(node));
            prefab.addActionListener(e -> navigator.openPrefab(node));
            menu.add(prefab);
            menu.show(tree, event.getX(), event.getY());
        });
    }

    private List<SceneNode> selectedNodes() {
        TreePath[] paths = tree.getSelectionPaths();
        List<SceneNode> nodes = new ArrayList<>();
        if (paths != null) {
            for (TreePath path : paths) {
                nodes.add(nodeOf(path));
            }
        }
        // The row clicked last becomes the primary selection, which the gizmo and inspector follow
        TreePath lead = tree.getLeadSelectionPath();
        if (lead != null && tree.isPathSelected(lead)) {
            SceneNode primary = nodeOf(lead);
            nodes.remove(primary);
            nodes.add(primary);
        }
        return nodes;
    }

    private static SceneNode nodeOf(TreePath path) {
        return (SceneNode) ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        attach();
        refresh();
    }

    @Override
    public void removeNotify() {
        detach();
        super.removeNotify();
    }

    @Override
    public void close() {
        closed = true;
        detach();
    }

    private void attach() {
        if (attached || closed) {
            return;
        }
        editor.selection().addListener(listener);
        editor.addSceneListener(listener);
        editor.addProjectListener(listener);
        attached = true;
    }

    private void detach() {
        editor.selection().removeListener(listener);
        editor.removeSceneListener(listener);
        editor.removeProjectListener(listener);
        attached = false;
    }

    // Scene listeners can fire off the EDT and many times per drag, so refreshes are coalesced
    private void scheduleRefresh() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            scheduled.set(false);
            if (attached && !closed) {
                refresh();
            }
        });
    }

    void refresh() {
        List<SceneNode> roots = editor.scene().map(scene -> List.copyOf(scene.getRootNodes())).unwrapOr(List.of());
        List<Row> rows = new ArrayList<>();
        roots.forEach(node -> collect(node, 0, rows));
        syncing = true;
        try {
            if (!rows.equals(shown)) {
                shown = rows;
                rebuild(roots);
            }
            syncSelection();
        } finally {
            syncing = false;
        }
    }

    private static void collect(SceneNode node, int depth, List<Row> into) {
        into.add(new Row(node, Objects.toString(node.getName(), ""), depth));
        node.getChildren().forEach(child -> collect(child, depth + 1, into));
    }

    private void rebuild(List<SceneNode> roots) {
        List<SceneNode> expanded = expandedNodes();
        root.removeAllChildren();
        roots.forEach(node -> root.add(treeNode(node)));
        model.reload();
        tree.expandPath(new TreePath(root.getPath()));
        for (TreePath path : pathsOf(expanded)) {
            tree.expandPath(path);
        }
    }

    private static DefaultMutableTreeNode treeNode(SceneNode node) {
        DefaultMutableTreeNode item = new DefaultMutableTreeNode(node);
        node.getChildren().forEach(child -> item.add(treeNode(child)));
        return item;
    }

    private List<SceneNode> expandedNodes() {
        List<SceneNode> nodes = new ArrayList<>();
        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(root.getPath()));
        if (paths != null) {
            while (paths.hasMoreElements()) {
                TreePath path = paths.nextElement();
                if (path.getLastPathComponent() != root) {
                    nodes.add(nodeOf(path));
                }
            }
        }
        return nodes;
    }

    private void syncSelection() {
        List<TreePath> paths = pathsOf(editor.selection().nodes());
        tree.setSelectionPaths(paths.toArray(TreePath[]::new));
        if (!paths.isEmpty()) {
            tree.scrollPathToVisible(paths.getLast());
        }
    }

    private List<TreePath> pathsOf(List<SceneNode> nodes) {
        List<TreePath> paths = new ArrayList<>();
        Enumeration<javax.swing.tree.TreeNode> all = root.depthFirstEnumeration();
        while (all.hasMoreElements()) {
            if (all.nextElement() instanceof DefaultMutableTreeNode item
                && item != root
                && nodes.stream().anyMatch(node -> node == item.getUserObject())) {
                paths.add(new TreePath(item.getPath()));
            }
        }
        return paths;
    }

    JTree tree() {
        return tree;
    }

    /** The scene objects in display order, for tests. */
    List<SceneNode> displayedNodes() {
        return shown.stream().map(Row::node).toList();
    }

    private static final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean focus
        ) {
            Object user = ((DefaultMutableTreeNode) value).getUserObject();
            String name = user instanceof SceneNode node && node.getName() != null && !node.getName().isBlank()
                ? node.getName()
                : UNNAMED;
            super.getTreeCellRendererComponent(tree, name, selected, expanded, leaf, row, focus);
            setFont(KebabStyle.getCurrent().uiFont());
            setIcon(null);
            return this;
        }
    }
}
