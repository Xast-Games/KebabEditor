package org.xast.kebab.ui.outliner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
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
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.ui.SceneNavigator;
import org.xast.kebab.utils.Fonts;
import org.xast.kebab.utils.Maybe;

/**
 * The scene's objects as a tree. Selecting here selects in the viewport and the
 * other way round. Double-click or Enter flies the camera to an object, F frames
 * the selection, and the context menu also opens prefabs. Dragging objects onto
 * another one makes them its children; dropping between rows reorders them.
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
    private SceneNode expandAfterRebuild;
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

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(Fonts.ui());
        tree.setCellRenderer(new Renderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new ReparentHandler());
        installListeners();
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

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
        // The tree's own clipboard keys would swallow the Edit menu's copy and paste of objects
        for (String key : List.of("control C", "control V", "control X", "control D")) {
            bind(key, "none");
        }
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

    /** Shows the scene file as the root of the tree, for hosts where nothing else names the scene. */
    public void showSceneRoot() {
        tree.setRootVisible(true);
        refresh();
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
            menu.addSeparator();
            JMenuItem duplicate = new JMenuItem("Duplicate  (Ctrl+D)");
            duplicate.addActionListener(e -> editor.duplicateSelection());
            menu.add(duplicate);
            JMenuItem copy = new JMenuItem("Copy  (Ctrl+C)");
            copy.addActionListener(e -> editor.copySelection());
            menu.add(copy);
            JMenuItem topLevel = new JMenuItem("Move to top level");
            topLevel.setEnabled(node.getParent().isSome());
            topLevel.addActionListener(e -> reparent(selectedNodes(), Maybe.none(), Integer.MAX_VALUE));
            menu.add(topLevel);
            menu.show(tree, event.getX(), event.getY());
        });
    }

    // The parent is expanded afterwards, so the moved objects stay in sight
    void reparent(List<SceneNode> nodes, Maybe<SceneNode> parent, int index) {
        try {
            editor.reparent(nodes, parent, index);
            parent.ifSome(target -> expandAfterRebuild = target);
            refresh();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Move objects", JOptionPane.WARNING_MESSAGE);
        }
    }

    private List<SceneNode> selectedNodes() {
        TreePath[] paths = tree.getSelectionPaths();
        List<SceneNode> nodes = new ArrayList<>();
        if (paths != null) {
            for (TreePath path : paths) {
                SceneNode node = nodeOf(path);
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        // The row clicked last becomes the primary selection, which the gizmo and inspector follow
        TreePath lead = tree.getLeadSelectionPath();
        if (lead != null && tree.isPathSelected(lead) && nodeOf(lead) != null) {
            SceneNode primary = nodeOf(lead);
            nodes.remove(primary);
            nodes.add(primary);
        }
        return nodes;
    }

    // Null for the root, which is the scene rather than an object in it
    private static SceneNode nodeOf(TreePath path) {
        return ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof SceneNode node ? node : null;
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
        String sceneName = editor.currentSceneFile().map(file -> file.getFileName().toString()).unwrapOr("");
        if (!sceneName.equals(root.getUserObject())) {
            root.setUserObject(sceneName);
            model.nodeChanged(root);
        }

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
        if (expandAfterRebuild != null) {
            expanded.add(expandAfterRebuild);
            expandAfterRebuild = null;
        }
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

    /** Where a drop puts the dragged objects: under which parent, and at which index among its children. */
    private record Target(Maybe<SceneNode> parent, int index) {}

    private Maybe<Target> dropTarget(TransferHandler.TransferSupport support) {
        if (!(support.getDropLocation() instanceof JTree.DropLocation location)) {
            return Maybe.none();
        }
        TreePath path = location.getPath();
        if (path == null || path.getLastPathComponent() == root) {
            // Between top-level rows, or below the last row
            int index = path == null ? Integer.MAX_VALUE : location.getChildIndex();
            return Maybe.some(new Target(Maybe.none(), index < 0 ? Integer.MAX_VALUE : index));
        }
        SceneNode parent = nodeOf(path);
        int index = location.getChildIndex() < 0 ? Integer.MAX_VALUE : location.getChildIndex();
        return Maybe.some(new Target(Maybe.some(parent), index));
    }

    // A node cannot go into itself or into one of its own children
    private static boolean contains(List<SceneNode> dragged, SceneNode target) {
        for (Maybe<SceneNode> at = Maybe.some(target); at.isSome(); at = at.unwrap().getParent()) {
            SceneNode node = at.unwrap();
            if (dragged.stream().anyMatch(d -> d == node)) {
                return true;
            }
        }
        return false;
    }

    private final class ReparentHandler extends TransferHandler {

        private static final DataFlavor NODES = new DataFlavor(List.class, "Scene objects");

        private List<SceneNode> dragged = List.of();

        @Override
        public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            dragged = selectedNodes();
            if (dragged.isEmpty() || editor.isPlaytesting()) {
                return null;
            }
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { NODES };
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return NODES.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return dragged;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(NODES) || dragged.isEmpty()) {
                return false;
            }
            return dropTarget(support)
                .isSomeAnd(target -> target.parent().mapOr(true, parent -> !contains(dragged, parent)));
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            Target target = dropTarget(support).unwrap();
            List<SceneNode> nodes = dragged;
            // After the drag ends, so a warning dialog does not interrupt Swing's drop handling
            SwingUtilities.invokeLater(() -> reparent(nodes, target.parent(), target.index()));
            return true;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            dragged = List.of();
        }
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
                : user instanceof String scene ? scene : UNNAMED;
            super.getTreeCellRendererComponent(tree, name, selected, expanded, leaf, row, focus);
            Font font = Fonts.ui();
            setFont(user instanceof String ? font.deriveFont(Font.BOLD) : font);
            setIcon(null);
            return this;
        }
    }
}
