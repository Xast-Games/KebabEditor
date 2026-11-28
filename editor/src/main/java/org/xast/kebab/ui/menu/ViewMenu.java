package org.xast.kebab.ui.menu;


import java.util.Arrays;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.ui.CollapsiblePanel;
import org.xast.kebab.ui.EditorView;
import org.xast.kebab.ui.menu.generic.CheckItem;
import org.xast.kebab.ui.menu.generic.Menu;
import org.xast.kebab.ui.menu.generic.MenuItem;
import org.xast.kebab.ui.menu.generic.RadioItem;
import org.xast.kebab.ui.menu.generic.SeparatorItem;
import org.xast.kebab.ui.menu.generic.SingleItem;
import org.xast.kebab.ui.placement.PlacementTool;
import org.xast.kebab.ui.theme.Theme;
import org.xast.kebab.ui.theme.ThemeSwitcher;
import org.xast.kebab.utils.Maybe;

public class ViewMenu extends Menu {

    /**
     * @param view the viewport; none when it is unavailable
     */
    public ViewMenu(
        JFrame frame,
        Editor editor,
        ThemeSwitcher themes,
        Maybe<EditorView> view,
        Maybe<CollapsiblePanel> sidePanel,
        Maybe<CollapsiblePanel> bottomPanel
    ) {
        super("View");

        setItems(new MenuItem[] {
            blockoutMenu(frame, editor, view.map(EditorView::getPlacement)),

            new SeparatorItem(),

            showIconsItem(view),
            renderNoVolumeItem(view),

            new SeparatorItem(),

            panelItem("Show side panel", "N", sidePanel),
            panelItem("Show bottom panel", "T", bottomPanel),

            new SeparatorItem(),

            themeMenu(themes),
        });
    }

    private static Menu themeMenu(ThemeSwitcher themes) {
        ButtonGroup group = new ButtonGroup();
        MenuItem[] items = Arrays.stream(Theme.values())
            .map(theme -> {
                // Later: closing the menu while its own look is replaced would paint it with both
                RadioItem item = new RadioItem(theme.title(), group,
                    () -> SwingUtilities.invokeLater(() -> themes.select(theme)));
                item.setSelected(theme == themes.current());
                return item;
            })
            .toArray(MenuItem[]::new);

        return new Menu("Theme", items);
    }

    private static CheckItem panelItem(String label, String keyStroke, Maybe<CollapsiblePanel> panel) {
        CheckItem item = new CheckItem(label, Maybe.some(keyStroke), on -> panel.ifSome(CollapsiblePanel::toggle));

        item.setSelected(true);
        item.component().setEnabled(panel.isSome());
        panel.ifSome(p -> p.addListener(() -> item.setSelected(p.isShown())));

        return item;
    }

    private static CheckItem showIconsItem(Maybe<EditorView> view) {
        CheckItem showIcons = new CheckItem("Show icons", Maybe.none(), on -> view.ifSome(v -> v.setShowIcons(on)));

        showIcons.setSelected(view.map(EditorView::isShowIcons).unwrapOr(false));
        showIcons.component().setEnabled(view.isSome());

        return showIcons;
    }

    private static CheckItem renderNoVolumeItem(Maybe<EditorView> view) {
        CheckItem renderNoVolume = new CheckItem(
            "Render No Volume", 
            Maybe.none(),
            on -> view.ifSome(v -> v.setRenderNoVolume(on))
        );

        renderNoVolume.setSelected(view.map(EditorView::isRenderNoVolume).unwrapOr(false));
        renderNoVolume.component().setEnabled(view.isSome());

        return renderNoVolume;
    }

    private static Menu blockoutMenu(JFrame frame, Editor editor, Maybe<PlacementTool> placement) {
        Menu blockout = new Menu("Blockout view");
        if (placement.isNone()) {
            blockout.component().setEnabled(false);
            return blockout;
        }

        PlacementTool tool = placement.unwrap();

        CheckItem placementRay = new CheckItem("Placement ray", Maybe.some("control shift P"), on -> {
            if (on && !SceneRequired.check(frame, editor)) {
                on = false;
            }
            tool.setActive(on);
        });

        ButtonGroup shapes = new ButtonGroup();
        RadioItem box = new RadioItem("Place box", shapes, () -> tool.setShape(GreyboxShape.BOX));
        RadioItem sphere = new RadioItem("Place sphere", shapes, () -> tool.setShape(GreyboxShape.SPHERE));

        // The tool also changes on its own (Esc leaves placement), so mirror its state.
        Runnable sync = () -> {
            placementRay.setSelected(tool.isActive());
            box.setSelected(tool.getShape() == GreyboxShape.BOX);
            sphere.setSelected(tool.getShape() == GreyboxShape.SPHERE);
        };
        sync.run();
        tool.addListener(sync);
        // A closed project, or one with no scene open, leaves nothing to place into.
        editor.addProjectListener(() -> {
            if (!editor.isSceneOpen()) {
                tool.setActive(false);
            }
        });

        CheckItem snapToGrid = new CheckItem("Snap to grid", Maybe.some("control shift G"), editor::setSnapping);
        snapToGrid.setSelected(editor.isSnapping());

        SingleItem snapDistance = new SingleItem("Snap distance...", Maybe.none(),
            SceneRequired.guard(frame, editor, () -> askSnapDistance(frame, editor)));

        blockout.setItems(new MenuItem[] {
            placementRay,
            new SeparatorItem(),
            box,
            sphere,
            new SeparatorItem(),
            snapToGrid,
            snapDistance,
        });
        return blockout;
    }

    // The value lives on the scene's snap_distance object, so it is saved with the level.
    private static void askSnapDistance(JFrame frame, Editor editor) {
        Object input = JOptionPane.showInputDialog(
            frame,
            "Grid step in meters, stored on the level's Snap Distance object:",
            "Snap distance",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            String.valueOf(editor.snapDistance())
        );
        if (input == null) {
            return;
        }

        try {
            editor.setSnapDistance(Float.parseFloat(input.toString().trim().replace(',', '.')));
        } catch (IllegalArgumentException e) {
            // NumberFormatException included: keep the old value and say why.
            JOptionPane.showMessageDialog(
                frame,
                "'" + input + "' is not a positive number; the snap distance is unchanged.",
                "Invalid snap distance",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }
}
