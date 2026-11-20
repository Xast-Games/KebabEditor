package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.Maybe;

/**
 * Edits a greybox's shape, per-axis size and colour. The size is what the scale gizmo
 * stretches; the node's own scale stays uniform and multiplies it.
 */
final class GreyboxSection extends InspectorSection {

    private static final String[] AXES = {"X", "Y", "Z"};
    private static final String[] SIZE_FIELDS = {"greybox.size.x", "greybox.size.y", "greybox.size.z"};

    private final Editor editor;
    private final SceneNode node;
    private final JComboBox<GreyboxShape> shape = new JComboBox<>(GreyboxShape.values());
    private final JTextField[] size = new JTextField[3];
    private final String[] displayed = new String[3];
    private final JButton color = new JButton();
    private boolean refreshing;

    GreyboxSection(Editor editor, SceneNode node) {
        super("Greybox", null);
        this.editor = editor;
        this.node = node;

        body.setLayout(new GridLayout(0, 1, 0, 4));
        body.add(row("Shape", shapeEditor()));
        body.add(row("Size", sizeEditor()));
        body.add(row("Color", colorEditor()));

        refresh();
    }

    private JPanel shapeEditor() {
        shape.setName("greybox.shape");
        shape.setFont(KebabStyle.getCurrent().uiFont());
        shape.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus
            ) {
                Object text = value instanceof GreyboxShape s ? s.displayName() : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        shape.addActionListener(e -> {
            if (!refreshing && shape.getSelectedItem() instanceof GreyboxShape chosen) {
                current().ifSome(greybox -> {
                    if (greybox.shape() != chosen) {
                        update(new Greybox(chosen, greybox.size(), greybox.color()));
                    }
                });
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(shape, BorderLayout.CENTER);
        return panel;
    }

    private JPanel sizeEditor() {
        JPanel axes = new JPanel(new GridLayout(1, 3, 4, 0));
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            JTextField field = new JTextField();
            field.setName(SIZE_FIELDS[axis]);
            field.setFont(KebabStyle.getCurrent().techFont());
            field.addActionListener(e -> commitSize(index));
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitSize(index);
                }
            });
            size[axis] = field;

            JPanel pair = new JPanel(new BorderLayout(3, 0));
            pair.add(label(AXES[axis]), BorderLayout.WEST);
            pair.add(field, BorderLayout.CENTER);
            axes.add(pair);
        }
        return axes;
    }

    // Cancel in the chooser keeps the current colour; its alpha is kept, as the chooser has none
    private JPanel colorEditor() {
        color.setName("greybox.color");
        color.setFont(KebabStyle.getCurrent().techFont());
        color.addActionListener(e -> current().ifSome(greybox -> {
            if (!canEdit(editor, node)) {
                return;
            }
            Color chosen = JColorChooser.showDialog(
                SwingUtilities.getWindowAncestor(color), "Greybox colour", greybox.color()
            );
            if (chosen != null) {
                Color withAlpha = new Color(
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue(), greybox.color().getAlpha()
                );
                update(new Greybox(greybox.shape(), greybox.size(), withAlpha));
            }
        }));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(color, BorderLayout.CENTER);
        return panel;
    }

    void refresh() {
        current().ifSome(greybox -> {
            refreshing = true;
            try {
                if (shape.getSelectedItem() != greybox.shape()) {
                    shape.setSelectedItem(greybox.shape());
                }
            } finally {
                refreshing = false;
            }

            float[] values = greybox.size().toArray();
            for (int axis = 0; axis < 3; axis++) {
                if (size[axis].hasFocus()) {
                    continue;
                }
                displayed[axis] = Float.toString(values[axis]);
                size[axis].setText(displayed[axis]);
                clearError(size[axis]);
            }

            Color c = greybox.color();
            color.setBackground(c);
            color.setForeground(isLight(c) ? Color.BLACK : Color.WHITE);
            color.setText(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
        });
    }

    private void commitSize(int axis) {
        if (!canEdit(editor, node) || current().isNone()) {
            return;
        }

        JTextField field = size[axis];
        if (field.getText().equals(displayed[axis])) {
            return;
        }

        try {
            float value = Float.parseFloat(field.getText().trim());
            if (!Float.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException();
            }

            Greybox greybox = current().unwrap();
            float[] values = greybox.size().toArray();
            values[axis] = value;
            update(new Greybox(greybox.shape(), new Vec3(values[0], values[1], values[2]), greybox.color()));
            displayed[axis] = field.getText();
            clearError(field);
            refresh();
        } catch (IllegalArgumentException e) {
            markError(field, "Enter a positive number; the previous value is preserved");
        }
    }

    private void update(Greybox greybox) {
        if (canEdit(editor, node)) {
            editor.updateGreybox(node, greybox);
        }
    }

    private Maybe<Greybox> current() {
        return Greybox.of(node);
    }

    private static JPanel row(String title, JPanel editor) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(label(title), BorderLayout.NORTH);
        panel.add(editor, BorderLayout.CENTER);
        return panel;
    }

    private static boolean isLight(Color color) {
        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue() > 140;
    }
}
