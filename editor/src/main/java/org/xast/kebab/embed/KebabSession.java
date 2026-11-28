package org.xast.kebab.embed;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.project.EditorSessions;
import org.xast.kebab.project.ProjectConfigStore;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.scene.error.SceneFormatException;
import org.xast.kebab.ui.DeleteSelected;
import org.xast.kebab.ui.EditorView;
import org.xast.kebab.ui.SceneNavigator;
import org.xast.kebab.ui.inspector.InspectorPane;
import org.xast.kebab.ui.navigation.FreeFlightNavigation;
import org.xast.kebab.ui.object.ObjectDialog;
import org.xast.kebab.ui.outliner.OutlinerPane;
import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogamp.opengl.GLException;

/**
 * One scene open for editing inside a host: the viewport, and the Inspector and Outliner the host
 * places where it likes. Use it on the Swing thread.
 */
public final class KebabSession implements AutoCloseable {

    /** What a session asks of its host. */
    public interface Host {

        /** The scene now differs from the saved one, or matches it again. */
        void dirtyChanged(boolean dirty);

        /** Another scene should be opened, for example the one a prefab points at. */
        void openScene(Path scene);
    }

    private static final int INSPECTOR_MIN_WIDTH = 240;
    // Gizmo drags change the scene every frame, so the comparison with the saved scene waits for a pause
    private static final int DIRTY_CHECK_MILLIS = 150;

    private final Editor editor;
    private final Host host;
    private final Maybe<EditorView> view;
    private final InspectorPane inspector;
    private final OutlinerPane outliner;
    private final JComponent component;
    private final Timer dirtyCheck = new Timer(DIRTY_CHECK_MILLIS, e -> updateDirty());
    private final Runnable changed = dirtyCheck::restart;
    private boolean dirty;

    private KebabSession(Editor editor, Host host) {
        this.editor = editor;
        this.host = host;

        view = createView();
        inspector = new InspectorPane(editor);
        inspector.setMinimumSize(new Dimension(INSPECTOR_MIN_WIDTH, 0));
        component = viewport();

        outliner = new OutlinerPane(
            editor,
            nodes -> view.ifSome(viewport -> viewport.frame(nodes)),
            new SceneNavigator(component, editor, host::openScene)
        );

        view.ifSome(viewport -> viewport.addNodeActivatedListener(node ->
            new ObjectDialog(SwingUtilities.getWindowAncestor(component), editor, node).setVisible(true)
        ));

        dirtyCheck.setRepeats(false);
        editor.addSceneListener(changed);
        editor.addProjectListener(changed);
    }

    /** Whether the file is a scene registered in a kebab-project.json above it. */
    public static boolean isScene(Path file) {
        try {
            return ProjectConfigStore.isProjectFile(new ProjectConfigStore(new ObjectMapper()).scopeFor(file).projectFile());
        } catch (IOException e) {
            LogService.get().warn("Cannot read the project of " + file + ": " + e.getMessage());
            return false;
        }
    }

    /** A scene read from disk with its project, not shown yet. */
    public static final class Loaded {

        private final Editor editor;

        private Loaded(Editor editor) {
            this.editor = editor;
        }
    }

    /**
     * Reads a scene with the project that registers it, or as a project of its own.
     * Safe off the Swing thread; nothing is shown until {@link #open(Loaded, Host)}.
     */
    public static Loaded load(Path scene) throws SceneException, IOException {
        Editor editor = EditorSessions.create();
        editor.openProject(scene);
        return new Loaded(editor);
    }

    /** Shows a loaded scene. Call it on the Swing thread, after {@link ModelPreview#prepare()} has returned. */
    public static KebabSession open(Loaded loaded, Host host) {
        if (!KebabStyle.isSet()) {
            KebabStyle.setCurrent(KebabStyle.defaultStyle());
        }

        return new KebabSession(loaded.editor, host);
    }

    /** The 3D viewport. */
    public JComponent component() {
        return component;
    }

    public JComponent inspector() {
        return inspector;
    }

    public JComponent outliner() {
        return outliner;
    }

    public boolean isDirty() {
        return editor.hasUnsavedChanges();
    }

    public void save() throws SceneFormatException, IOException {
        editor.saveScene();
        updateDirty();
    }

    public void undo() {
        editor.undo();
    }

    public void redo() {
        editor.redo();
    }

    public void copy() {
        editor.copySelection();
    }

    public void cut() {
        if (editor.copySelection() > 0) {
            DeleteSelected.run(component, editor);
        }
    }

    public void paste() {
        editor.paste(view.map(EditorView::spawnPoint).unwrapOr(Vec3.zero()));
    }

    public void delete() {
        DeleteSelected.run(component, editor);
    }

    public void selectAll() {
        editor.selection().set(editor.sceneNodes());
    }

    @Override
    public void close() {
        dirtyCheck.stop();
        editor.removeSceneListener(changed);
        editor.removeProjectListener(changed);
        inspector.close();
        outliner.close();
        view.ifSome(EditorView::close);
    }

    private Maybe<EditorView> createView() {
        try {
            EditorView viewport = new EditorView(editor);
            viewport.setNavigationMode(new FreeFlightNavigation());
            return Maybe.some(viewport);
        } catch (GLException e) {
            LogService.get().error("OpenGL 3.x is not available, the 3D viewport is disabled", e);
            return Maybe.none();
        }
    }

    private JComponent viewport() {
        if (view.isSome()) {
            return view.unwrap();
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("3D viewport unavailable: OpenGL 3.x is not supported", SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }

    private void updateDirty() {
        boolean now = editor.hasUnsavedChanges();
        if (now != dirty) {
            dirty = now;
            host.dirtyChanged(now);
        }
    }
}
