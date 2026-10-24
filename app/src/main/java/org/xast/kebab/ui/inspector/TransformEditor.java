package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JPanel;
import javax.swing.JTextField;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Quat;
import org.xast.kebab.math.Transform;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.KebabStyle;

/** Edits local TRS, keeping the authoritative quaternion and uniform scale. */
final class TransformEditor extends InspectorSection {

    private static final String[] GROUPS = {"Position", "Rotation (degrees)", "Scale"};
    private static final String[] AXES = {"X", "Y", "Z"};
    private static final String[] FIELDS = {
        "position.x", "position.y", "position.z",
        "rotation.x", "rotation.y", "rotation.z",
        "scale"
    };
    private static final int ROTATION = 3;
    private static final int SCALE = 6;

    private final Editor editor;
    private final SceneNode node;
    private final JTextField[] fields = new JTextField[FIELDS.length];
    private final String[] displayed = new String[FIELDS.length];
    private Quat displayedRotation;
    private Vec3 angles;

    TransformEditor(Editor editor, SceneNode node) {
        super("Transform", null);
        this.editor = editor;
        this.node = node;

        body.setLayout(new GridLayout(0, 1, 0, 4));
        for (int group = 0; group < GROUPS.length; group++) {
            body.add(group(group));
        }

        refresh();
    }

    private JPanel group(int group) {
        int axisCount = group == 2 ? 1 : AXES.length;

        JPanel axes = new JPanel(new GridLayout(1, axisCount, 4, 0));
        for (int axis = 0; axis < axisCount; axis++) {
            JPanel pair = new JPanel(new BorderLayout(3, 0));
            if (axisCount > 1) {
                pair.add(label(AXES[axis]), BorderLayout.WEST);
            }
            pair.add(field(group * 3 + axis), BorderLayout.CENTER);
            axes.add(pair);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(label(GROUPS[group]), BorderLayout.NORTH);
        panel.add(axes, BorderLayout.CENTER);
        return panel;
    }

    private JTextField field(int index) {
        JTextField field = new JTextField();
        field.setName(FIELDS[index]);
        field.setFont(KebabStyle.getCurrent().techFont());

        field.addActionListener(e -> commit(index));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit(index);
            }
        });

        fields[index] = field;
        return field;
    }

    void refresh() {
        Transform transform = node.getTransform();

        if (!transform.getRotation().equals(displayedRotation)) {
            displayedRotation = transform.getRotation().copy();
            angles = degrees(transform.getRotation());
        }

        for (int i = 0; i < fields.length; i++) {
            if (fields[i].hasFocus()) {
                continue;
            }
            float value = valueAt(transform, i);
            displayed[i] = Float.toString(value == 0 ? 0 : value);
            fields[i].setText(displayed[i]);
        }
    }

    private float valueAt(Transform transform, int index) {
        if (index < ROTATION) {
            return axis(transform.getTranslation(), index);
        }
        if (index < SCALE) {
            return axis(angles, index - ROTATION);
        }
        return transform.getScale();
    }

    private void commit(int index) {
        if (!canEdit(editor, node)) {
            return;
        }

        JTextField field = fields[index];
        // Never round-trip an untouched quaternion
        if (field.getText().equals(displayed[index])) {
            return;
        }

        try {
            float value = Float.parseFloat(field.getText().trim());
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Enter a finite number");
            }

            Transform next = node.getTransform().copy();
            if (index < ROTATION) {
                setAxis(next.getTranslation(), index, value);
            } else if (index < SCALE) {
                rotate(next, index - ROTATION, value);
            } else {
                next.setScale(value);
            }

            editor.updateTransform(node, next);
            displayed[index] = field.getText();
            clearError(field);
            refresh();
        } catch (IllegalArgumentException e) {
            markError(field, "Enter a finite number; the previous value is preserved");
        }
    }

    private void rotate(Transform next, int axis, float value) {
        Vec3 current = next.getRotation().equals(displayedRotation)
            ? angles.copy()
            : degrees(next.getRotation());
        setAxis(current, axis, value);

        // Reduce before conversion so even very large finite degree input stays finite
        next.setRotation(Quat.fromEuler(
            (float) Math.toRadians(current.getX() % 360),
            (float) Math.toRadians(current.getY() % 360),
            (float) Math.toRadians(current.getZ() % 360)
        ));

        angles = current;
        displayedRotation = next.getRotation().copy();
    }

    private static Vec3 degrees(Quat rotation) {
        return rotation.toEuler().mulLocal((float) (180 / Math.PI));
    }

    private static float axis(Vec3 v, int axis) {
        return switch (axis) {
            case 0 -> v.getX();
            case 1 -> v.getY();
            default -> v.getZ();
        };
    }

    private static void setAxis(Vec3 v, int axis, float value) {
        switch (axis) {
            case 0 -> v.setX(value);
            case 1 -> v.setY(value);
            default -> v.setZ(value);
        }
    }
}
