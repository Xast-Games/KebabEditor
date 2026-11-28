package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.ParseException;

import javax.swing.JPanel;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Quat;
import org.xast.kebab.math.Transform;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.ui.NumberSpinner;

/**
 * Edits local TRS, keeping the authoritative quaternion and uniform scale. Each value
 * is a spinner: its arrows step it (position by the snap distance, rotation by 15
 * degrees) and typed text is taken on Enter or when the field loses focus.
 */
final class TransformEditor extends InspectorSection {

    // Uniform by design; a greybox stretches per axis through its own size
    private static final String[] GROUPS = {"Position", "Rotation (degrees)", "Scale (uniform)"};
    private static final String[] AXES = {"X", "Y", "Z"};
    private static final String[] FIELDS = {
        "position.x", "position.y", "position.z",
        "rotation.x", "rotation.y", "rotation.z",
        "scale"
    };
    private static final int ROTATION = 3;
    private static final int SCALE = 6;
    private static final double ROTATION_STEP = 15;
    private static final double SCALE_STEP = 0.1;
    private static final double MIN_SCALE = 0.01;

    private final Editor editor;
    private final SceneNode node;
    private final NumberSpinner[] fields = new NumberSpinner[FIELDS.length];
    // The text last shown in each field, so untouched text is never applied again
    private final String[] displayed = new String[FIELDS.length];
    private boolean refreshing;
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

    private NumberSpinner field(int index) {
        NumberSpinner spinner = index == SCALE
            ? new NumberSpinner(1, MIN_SCALE, Float.MAX_VALUE, SCALE_STEP)
            : new NumberSpinner(0, index < ROTATION ? editor.snapDistance() : ROTATION_STEP);
        spinner.setName(FIELDS[index] + ".spinner");
        spinner.field().setName(FIELDS[index]);

        // Arrow clicks and accepted text both change the value
        spinner.addChangeListener(e -> {
            if (!refreshing) {
                apply(index, spinner.floatValue());
            }
        });
        spinner.field().addActionListener(e -> commitText(index));
        spinner.field().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitText(index);
            }
        });

        fields[index] = spinner;
        return spinner;
    }

    void refresh() {
        Transform transform = node.getTransform();

        if (!transform.getRotation().equals(displayedRotation)) {
            displayedRotation = transform.getRotation().copy();
            angles = degrees(transform.getRotation());
        }

        refreshing = true;
        try {
            for (int i = 0; i < fields.length; i++) {
                if (i < ROTATION) {
                    fields[i].setStep(editor.snapDistance());
                }
                if (fields[i].isTyping()) {
                    continue;
                }
                fields[i].setNumber(valueAt(transform, i));
                displayed[i] = fields[i].field().getText();
                clearError(fields[i].field());
            }
        } finally {
            refreshing = false;
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

    // Enter or focus loss: take typed text, which then arrives at apply as a value change
    private void commitText(int index) {
        NumberSpinner field = fields[index];
        // Never round-trip an untouched quaternion
        if (field.field().getText().equals(displayed[index])) {
            return;
        }

        try {
            field.commitText();
            displayed[index] = field.field().getText();
            clearError(field.field());
        } catch (ParseException e) {
            String message = index == SCALE
                ? "Enter a number of at least " + MIN_SCALE + "; the previous value is preserved"
                : "Enter a finite number; the previous value is preserved";
            markError(field.field(), message);
        }
    }

    private void apply(int index, float value) {
        if (!canEdit(editor, node) || !Float.isFinite(value)) {
            return;
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
        clearError(fields[index].field());
        refresh();
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
