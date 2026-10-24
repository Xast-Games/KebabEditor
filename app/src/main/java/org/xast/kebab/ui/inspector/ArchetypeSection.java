package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;
import java.awt.Image;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.xast.kebab.archetype.ArchetypeIcon;
import org.xast.kebab.archetype.ParamSpec;
import org.xast.kebab.archetype.ParamType;
import org.xast.kebab.archetype.error.UnknownParamException;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.archetype.instance.NodeArchetype;
import org.xast.kebab.archetype.instance.UnresolvedArchetype;
import org.xast.kebab.editor.Editor;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.ui.archetype.ParamValueEditor;
import org.xast.kebab.ui.validation.ValidationGroup;
import org.xast.kebab.utils.KebabStyle;

import com.formdev.flatlaf.extras.FlatSVGIcon;

final class ArchetypeSection extends InspectorSection {

    private static final int ICON_SIZE = 18;

    private final Editor editor;
    private final SceneNode node;
    private final NodeArchetype archetype;
    private final Map<String, ParamValueEditor> controls = new LinkedHashMap<>();
    private final Map<String, Object> shown = new HashMap<>();
    private final ValidationGroup validation = new ValidationGroup();
    private final boolean referencesNodes;
    private String nodeLabels = "";

    ArchetypeSection(Editor editor, SceneNode node, NodeArchetype archetype) {
        super(title(archetype), icon(archetype));
        this.editor = editor;
        this.node = node;
        this.archetype = archetype;
        this.referencesNodes = referencesNodes(archetype);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        switch (archetype) {
            case ArchetypeInstance instance -> instance.getDefinition()
                .getParams()
                .forEach((name, spec) -> body.add(paramRow(instance, name, spec)));
            case UnresolvedArchetype unresolved -> body.add(rawJson(unresolved));
        }
        body.add(removeButton());

        refresh();
    }

    void refresh() {
        if (!(archetype instanceof ArchetypeInstance instance)) {
            return;
        }

        boolean referencesChanged = updateNodeLabels();

        controls.forEach((name, control) -> {
            if (control.isEditing()) {
                return;
            }

            Object value = read(instance, name);
            ParamType type = instance.getDefinition().getParams().get(name).type();
            boolean changed = !shown.containsKey(name) || !Objects.equals(shown.get(name), value);

            if (changed || referencesChanged && type == ParamType.NODE_REF) {
                control.showFor(type, value);
                shown.put(name, value instanceof Vec3 v ? v.copy() : value);
            }
        });
    }

    void dispose() {
        controls.values().forEach(ParamValueEditor::dispose);
        validation.hidePopups();
    }

    private JPanel paramRow(ArchetypeInstance instance, String name, ParamSpec spec) {
        ParamValueEditor control = new ParamValueEditor(KebabStyle.getCurrent(), validation, editor::sceneNodes);
        control.setName("param." + name);
        control.setPathBase(() -> editor.currentProjectFile().map(file -> file.toAbsolutePath().getParent()));
        control.addCommitListener(() -> commit(instance, name, control));
        controls.put(name, control);

        JPanel row = new JPanel(new BorderLayout(0, 3));
        row.add(label(name + " (" + spec.type().displayName() + ")"), BorderLayout.NORTH);
        row.add(control, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return row;
    }

    private void commit(ArchetypeInstance instance, String name, ParamValueEditor control) {
        if (!canEdit(editor, node) || !node.getArchetypes().contains(instance)) {
            return;
        }

        try {
            Object value = control.value();

            if (!Objects.equals(value, shown.get(name))) {
                editor.setArchetypeParam(instance, name, value);
            } else {
                // A deferred external update can now replace untouched input
                editor.sceneChanged();
            }

            clearError(control);
        } catch (IllegalArgumentException e) {
            markError(control, e.getMessage());
        }
    }

    private JButton removeButton() {
        JButton remove = button("Remove archetype");

        remove.addActionListener(e -> {
            if (!canEdit(editor, node)) {
                return;
            }

            int answer = JOptionPane.showConfirmDialog(
                this,
                "Remove this archetype from the object?",
                "Remove archetype",
                JOptionPane.YES_NO_OPTION
            );

            if (answer == JOptionPane.YES_OPTION) {
                editor.detachArchetype(node, archetype);
            }
        });

        return remove;
    }

    private boolean updateNodeLabels() {
        if (!referencesNodes) {
            return false;
        }

        String labels = editor.sceneNodes()
            .stream()
            .map(n -> n.getId() + ":" + n.getName())
            .toList()
            .toString();
        boolean changed = !labels.equals(nodeLabels);

        nodeLabels = labels;
        return changed;
    }

    private static boolean referencesNodes(NodeArchetype archetype) {
        return archetype instanceof ArchetypeInstance instance
            && instance.getDefinition()
                .getParams()
                .values()
                .stream()
                .anyMatch(spec -> spec.type() == ParamType.NODE_REF);
    }

    private static Object read(ArchetypeInstance instance, String name) {
        try {
            return instance.get(name);
        } catch (UnknownParamException e) {
            throw new IllegalStateException("Parameter vanished from its definition", e);
        }
    }

    private static JTextArea rawJson(UnresolvedArchetype unresolved) {
        JTextArea raw = new JTextArea(unresolved.rawJson().toPrettyString());
        raw.setEditable(false);
        raw.setLineWrap(true);
        raw.setWrapStyleWord(true);
        raw.setFont(KebabStyle.getCurrent().techFont());
        return raw;
    }

    private static String title(NodeArchetype archetype) {
        if (archetype instanceof ArchetypeInstance instance) {
            return instance.getDefinition().getDisplayName();
        }
        return archetype.archetypeId() + " (unresolved)";
    }

    private static Icon icon(NodeArchetype archetype) {
        if (!(archetype instanceof ArchetypeInstance instance)) {
            return null;
        }
        return instance.getDefinition()
            .getIcon()
            .map(ArchetypeSection::icon)
            .unwrapOr(null);
    }

    private static Icon icon(ArchetypeIcon icon) {
        return switch (icon) {
            case ArchetypeIcon.Bundled bundled -> new FlatSVGIcon(bundled.resource(), ICON_SIZE, ICON_SIZE);
            case ArchetypeIcon.Loaded loaded -> scaled(loaded);
            case ArchetypeIcon.Handle handle -> handleIcon(handle);
        };
    }

    private static Icon handleIcon(ArchetypeIcon.Handle handle) {
        if (handle.path().toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return new FlatSVGIcon(new File(handle.path())).derive(ICON_SIZE, ICON_SIZE);
        }
        return handle.loaded()
            .map(ArchetypeSection::scaled)
            .unwrapOr(null);
    }

    private static Icon scaled(ArchetypeIcon.Loaded loaded) {
        Image image = loaded.icon().getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }
}
