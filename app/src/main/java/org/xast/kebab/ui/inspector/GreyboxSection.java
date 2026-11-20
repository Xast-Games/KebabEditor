package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.GreyboxColor;
import org.xast.kebab.scene.model.GreyboxShape;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.Maybe;

/**
 * Edits a greybox's shape, per-axis size, colour and texture pattern. The size is what the
 * scale gizmo stretches; the node's own scale stays uniform and multiplies it. Patterns are
 * shown in their grey variant, and the colour is picked separately from Kenney's colours.
 */
final class GreyboxSection extends InspectorSection {

    private static final String[] AXES = {"X", "Y", "Z"};
    private static final String[] SIZE_FIELDS = {"greybox.size.x", "greybox.size.y", "greybox.size.z"};
    private static final int THUMBNAIL = 48;
    private static final int SWATCH = 14;
    /** Grey pattern previews, shared by every greybox section and loaded on first use. */
    private static final Map<Integer, Icon> THUMBNAILS = new HashMap<>();

    private final Editor editor;
    private final SceneNode node;
    private final JTextField[] size = new JTextField[3];
    private final String[] displayed = new String[3];
    private final Map<JComboBox<?>, Runnable> combos = new HashMap<>();
    private boolean refreshing;

    GreyboxSection(Editor editor, SceneNode node) {
        super("Greybox", null);
        this.editor = editor;
        this.node = node;

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(row("Shape", combo("greybox.shape", GreyboxShape.values(), Greybox::shape, Greybox::withShape,
            shape -> new JLabel(shape.displayName()))));
        body.add(row("Size", sizeEditor()));
        body.add(row("Color", combo("greybox.color", GreyboxColor.values(), Greybox::color, Greybox::withColor,
            color -> new JLabel(color.displayName(), swatch(color.swatch()), JLabel.LEADING))));
        Integer[] patterns = IntStream.rangeClosed(1, Greybox.PATTERNS).boxed().toArray(Integer[]::new);
        body.add(row("Pattern", combo("greybox.pattern", patterns, Greybox::pattern, Greybox::withPattern,
            pattern -> new JLabel(String.format("Pattern %02d", pattern), thumbnail(pattern), JLabel.LEADING))));

        refresh();
    }

    /**
     * A drop-down for one property; choosing an item writes it, and {@link #refresh} shows the stored one.
     *
     * @param look how an item is shown, in the list and in the closed box
     */
    private <T> JPanel combo(
        String name,
        T[] items,
        Function<Greybox, T> get,
        BiFunction<Greybox, T, Greybox> set,
        Function<T, JLabel> look
    ) {
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setName(name);
        combo.setFont(KebabStyle.getCurrent().uiFont());
        combo.setMaximumRowCount(items.length);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, "", index, selected, focus);
                if (value != null) {
                    JLabel shown = look.apply((T) value);
                    label.setText(shown.getText());
                    label.setIcon(shown.getIcon());
                }
                return label;
            }
        });
        combo.addActionListener(e -> {
            @SuppressWarnings("unchecked")
            T chosen = (T) combo.getSelectedItem();
            if (!refreshing && chosen != null) {
                current().ifSome(greybox -> {
                    if (!chosen.equals(get.apply(greybox))) {
                        update(set.apply(greybox, chosen));
                    }
                });
            }
        });
        combos.put(combo, () -> current().ifSome(greybox -> combo.setSelectedItem(get.apply(greybox))));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(combo, BorderLayout.CENTER);
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

    void refresh() {
        current().ifSome(greybox -> {
            refreshing = true;
            try {
                combos.values().forEach(Runnable::run);
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
            update(greybox.withSize(new Vec3(values[0], values[1], values[2])));
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
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private static Icon swatch(Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x, y, SWATCH, SWATCH);
                g.setColor(color.darker());
                g.drawRect(x, y, SWATCH - 1, SWATCH - 1);
            }

            @Override
            public int getIconWidth() {
                return SWATCH;
            }

            @Override
            public int getIconHeight() {
                return SWATCH;
            }
        };
    }

    // Area averaging keeps the patterns' thin lines visible at thumbnail size
    private static Icon thumbnail(int pattern) {
        return THUMBNAILS.computeIfAbsent(pattern, p -> {
            String path = "/" + Greybox.textureResource(GreyboxColor.DARK, p);
            try (InputStream stream = GreyboxSection.class.getResourceAsStream(path)) {
                BufferedImage image = stream == null ? null : ImageIO.read(stream);
                if (image == null) {
                    throw new IOException("bundled image " + path + " not found");
                }
                return new ImageIcon(image.getScaledInstance(THUMBNAIL, THUMBNAIL, Image.SCALE_AREA_AVERAGING));
            } catch (IOException e) {
                LogService.get().warn("Cannot load pattern preview " + path + ": " + e.getMessage());
                return swatch(GreyboxColor.DARK.swatch());
            }
        });
    }
}
