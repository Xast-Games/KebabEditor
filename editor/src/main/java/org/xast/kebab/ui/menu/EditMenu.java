package org.xast.kebab.ui.menu;

import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.text.JTextComponent;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.render.GizmoMode;
import org.xast.kebab.render.GizmoPivot;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.ui.DeleteSelected;
import org.xast.kebab.ui.EditorView;
import org.xast.kebab.ui.SceneNavigator;
import org.xast.kebab.ui.menu.generic.Menu;
import org.xast.kebab.ui.menu.generic.MenuItem;
import org.xast.kebab.ui.menu.generic.RadioItem;
import org.xast.kebab.ui.menu.generic.SeparatorItem;
import org.xast.kebab.ui.menu.generic.SingleItem;
import org.xast.kebab.ui.object.ObjectDialog;
import org.xast.kebab.utils.Maybe;

public class EditMenu extends Menu {

    private static final float LIGHT_HEIGHT = 2f;

    /**
     * @param spawnPoint where new objects are placed, usually what the viewport camera looks at
     * @param view       the viewport whose gizmo the tool items switch; none when it is unavailable
     * @param navigator  opens the scene a selected prefab reference points at
     */
    public EditMenu(
        JFrame frame, 
        Editor editor, 
        Supplier<Vec3> spawnPoint, 
        Maybe<EditorView> view,
        SceneNavigator navigator
    ) {
        super("Edit");

        SingleItem undo = new SingleItem("Undo", Maybe.some("control Z"), editor::undo);
        SingleItem redo = new SingleItem("Redo", Maybe.some("control Y"), editor::redo);
        syncHistoryItems(editor, view, undo, redo);

        ButtonGroup tools = new ButtonGroup();
        ButtonGroup pivots = new ButtonGroup();

        setItems(new MenuItem[] {
            undo,
            redo,
            new SeparatorItem(),
            objectItem(frame, editor, "Copy", "control C", editor::copySelection),
            objectItem(frame, editor, "Paste", "control V", () -> editor.paste(spawnPoint.get())),
            objectItem(frame, editor, "Duplicate", "control D", editor::duplicateSelection),
            new SeparatorItem(),
            spawnItem(
                frame,
                editor,
                "Add greybox box",
                Maybe.some("control B"),
                () -> editor.spawnGreybox(GreyboxShape.BOX, spawnPoint.get())
            ),
            spawnItem(
                frame,
                editor,
                "Add greybox sphere",
                Maybe.some("control shift B"),
                () -> editor.spawnGreybox(GreyboxShape.SPHERE, spawnPoint.get())
            ),
            spawnItem(
                frame,
                editor,
                "Add empty",
                Maybe.none(),
                () -> editor.spawnEmpty(spawnPoint.get())
            ),
            spawnItem(
                frame,
                editor,
                "Add player start",
                Maybe.none(),
                () -> editor.spawnArchetype(BuiltinArchetypes.PLAYER_START, spawnPoint.get())
            ),
            new SeparatorItem(),
            lightItem(frame, editor, spawnPoint, "Add point light", BuiltinArchetypes.POINT_LIGHT),
            lightItem(frame, editor, spawnPoint, "Add directional light", BuiltinArchetypes.DIR_LIGHT),
            lightItem(frame, editor, spawnPoint, "Add spot light", BuiltinArchetypes.SPOT_LIGHT),
            new SeparatorItem(),
            editObjectItem(frame, editor),
            openPrefabItem(frame, editor, navigator),
            frameSelectedItem(view),
            spawnItem(
                frame,
                editor,
                "Delete selected object",
                Maybe.some("DELETE"),
                () -> DeleteSelected.run(frame, editor)
            ),
            new SeparatorItem(),
            toolItem(view, "Move tool  (1)", "1", GizmoMode.TRANSLATE, tools),
            toolItem(view, "Rotate tool  (2)", "2", GizmoMode.ROTATE, tools),
            toolItem(view, "Scale tool  (3)", "3", GizmoMode.SCALE, tools),
            new SeparatorItem(),
            pivotItem(view, "Rotate/scale each object individually", GizmoPivot.INDIVIDUAL, pivots),
            pivotItem(view, "Rotate/scale around selection center", GizmoPivot.SELECTION_CENTER, pivots),
        });
    }

    // Undo and redo only act on the scene while its viewport has focus, so the
    // shortcuts are left to whatever else is focused, like a text field.
    private static void syncHistoryItems(Editor editor, Maybe<EditorView> view, SingleItem undo, SingleItem redo) {
        BooleanSupplier sceneActive = () -> view.isSomeAnd(EditorView::isFocusOwner);
        Runnable sync = () -> {
            undo.setEnabled(sceneActive.getAsBoolean() && editor.history().canUndo());
            redo.setEnabled(sceneActive.getAsBoolean() && editor.history().canRedo());
        };

        sync.run();
        editor.history().addListener(sync);
        view.ifSome(v -> v.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                sync.run();
            }

            @Override
            public void focusLost(FocusEvent e) {
                sync.run();
            }
        }));
    }

    private static SingleItem spawnItem(
        JFrame frame,
        Editor editor,
        String label,
        Maybe<String> keyStroke,
        Runnable action
    ) {
        return new SingleItem(label, keyStroke, SceneRequired.guard(frame, editor, action));
    }

    // While a text field has focus the shortcut belongs to it, so Ctrl+C there still copies text
    private static SingleItem objectItem(JFrame frame, Editor editor, String label, String keyStroke, Runnable action) {
        return spawnItem(frame, editor, label, Maybe.some(keyStroke), () -> {
            if (!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent)) {
                action.run();
            }
        });
    }

    private static SingleItem editObjectItem(JFrame frame, Editor editor) {
        Runnable editObject = () -> editor.selection().primary().ifSomeOrElse(
            node -> new ObjectDialog(frame, editor, node).setVisible(true),
            () -> JOptionPane.showMessageDialog(
                frame,
                "Select an object first.",
                "Edit object",
                JOptionPane.INFORMATION_MESSAGE
            )
        );

        return spawnItem(frame, editor, "Edit selected object...", Maybe.some("control E"), editObject);
    }

    private static SingleItem openPrefabItem(JFrame frame, Editor editor, SceneNavigator navigator) {
        Runnable openPrefab = () -> editor.selection().primary().ifSomeOrElse(
            navigator::openPrefab,
            () -> JOptionPane.showMessageDialog(
                frame,
                "Select an object with a Prefab Reference first.",
                "Open prefab",
                JOptionPane.INFORMATION_MESSAGE
            )
        );

        return spawnItem(frame, editor, "Open prefab", Maybe.some("control shift O"), openPrefab);
    }

    // F is handled by the viewport and the outliner, so typing an F in a text field never moves the camera
    private static SingleItem frameSelectedItem(Maybe<EditorView> view) {
        SingleItem item = new SingleItem("Frame selected  (F)", Maybe.none(), () -> view.ifSome(EditorView::frameSelection));
        item.setEnabled(view.isSome());
        return item;
    }

    private static SingleItem lightItem(
        JFrame frame,
        Editor editor,
        Supplier<Vec3> spawnPoint,
        String label,
        String archetypeId
    ) {
        Vec3 lifted = new Vec3(0, LIGHT_HEIGHT, 0);

        return spawnItem(
            frame,
            editor,
            label,
            Maybe.none(),
            () -> editor.spawnArchetype(archetypeId, spawnPoint.get().add(lifted))
        );
    }

    // The mode also changes from the viewport's 1/2/3 keys, so each item mirrors it.
    private static RadioItem toolItem(
        Maybe<EditorView> view,
        String label,
        String keyStroke,
        GizmoMode mode,
        ButtonGroup group
    ) {
        RadioItem item = new RadioItem(
            label,
            Maybe.some(keyStroke),
            group,
            () -> view.ifSome(v -> v.setGizmoMode(mode))
        );

        view.ifSomeOrElse(v -> {
            Runnable sync = () -> item.setSelected(v.getGizmoMode() == mode);
            sync.run();
            v.addGizmoModeListener(sync);
        }, () -> item.component().setEnabled(false));

        return item;
    }

    // With several objects selected, moving always moves them together; this picks
    // whether rotating and scaling treat them one by one or as one big object.
    private static RadioItem pivotItem(Maybe<EditorView> view, String label, GizmoPivot pivot, ButtonGroup group) {
        RadioItem item = new RadioItem(label, group, () -> view.ifSome(v -> v.setGizmoPivot(pivot)));

        view.ifSomeOrElse(v -> {
            Runnable sync = () -> item.setSelected(v.getGizmoPivot() == pivot);
            sync.run();
            v.addGizmoModeListener(sync);
        }, () -> item.component().setEnabled(false));

        return item;
    }
}
