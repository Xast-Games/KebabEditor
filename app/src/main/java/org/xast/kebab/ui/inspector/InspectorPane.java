package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.archetype.instance.NodeArchetype;
import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.LucideIcon;

/** Event-driven, headless-testable inspector. Construct and attach on the EDT. */
public final class InspectorPane extends JPanel implements AutoCloseable {

    private record SectionState(
        NodeArchetype instance, 
        String title, 
        Object icon, 
        Map<String, ?> params
    ) {}

    private final Editor editor;
    private final JPanel content = new InspectorContent();
    private final List<ArchetypeSection> sections = new ArrayList<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Runnable listener = this::scheduleRefresh;
    private boolean attached;
    private boolean closed;

    private SceneNode node;
    private List<SectionState> structure = List.of();
    private JTextField name;
    private String displayedName;
    private TransformEditor transform;
    private JButton add;

    public InspectorPane(Editor editor) {
        super(new BorderLayout());

        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Create InspectorPane on the EDT");
        }

        this.editor = editor;
        setPreferredSize(new Dimension(350, 600));
        setMinimumSize(new Dimension(240, 100));

        JLabel title = InspectorSection.label("Inspector");
        title.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(title, BorderLayout.NORTH);

        content.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(
            content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        attach();
        refresh();
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
        sections.forEach(ArchetypeSection::dispose);
    }

    private void attach() {
        if (attached || closed) {
            return;
        }

        editor.selection().addListener(listener);
        editor.addSceneListener(listener);
        attached = true;
    }

    private void detach() {
        editor.selection().removeListener(listener);
        editor.removeSceneListener(listener);
        attached = false;
    }

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

    private void refresh() {
        SceneNode selected = editor.selection().primary().unwrapOr(null);
        if (selected != null && !editor.containsNode(selected)) {
            selected = null;
        }

        List<SectionState> nextStructure = selected == null ? List.of() : structureOf(selected);
        if (selected != node || !structure.equals(nextStructure) || content.getComponentCount() == 0) {
            rebuild(selected, nextStructure);
        }

        if (node == null) {
            return;
        }

        if (!name.hasFocus()) {
            showName();
        }
        transform.refresh();
        sections.forEach(ArchetypeSection::refresh);

        boolean editable = !editor.isPlaytesting();
        setEnabledDeep(content, editable);
        add.setEnabled(editable && !editor.availableArchetypes(node).isEmpty());
    }

    private void rebuild(SceneNode selected, List<SectionState> nextStructure) {
        sections.forEach(ArchetypeSection::dispose);
        sections.clear();
        node = selected;
        structure = nextStructure;
        content.removeAll();

        if (node == null) {
            name = null;
            transform = null;
            add = null;
            content.add(InspectorSection.label("Select an object to inspect"), BorderLayout.NORTH);
        } else {
            // BorderLayout sizes the stack to the viewport width, including narrow sidebars
            content.add(stack(node), BorderLayout.NORTH);
        }

        content.revalidate();
        content.repaint();
    }

    private JPanel stack(SceneNode target) {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        stack.add(header(target));

        transform = new TransformEditor(editor, target);
        stack.add(transform);

        for (NodeArchetype archetype : target.getArchetypes()) {
            ArchetypeSection section = new ArchetypeSection(editor, target, archetype);
            sections.add(section);
            stack.add(section);
        }

        add = addArchetypeButton(target);
        stack.add(add);

        return stack;
    }

    private JPanel header(SceneNode target) {
        JTextField field = new JTextField(Objects.toString(target.getName(), ""));
        field.setName("object.name");
        field.setFont(KebabStyle.getCurrent().uiFont());

        field.addActionListener(e -> commitName(target, field));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitName(target, field);
            }
        });
        name = field;

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.add(new JLabel(LucideIcon.COMPONENT.icon(18, UIManager.getColor("Label.foreground"))), BorderLayout.WEST);
        header.add(field, BorderLayout.CENTER);
        return header;
    }

    private void showName() {
        displayedName = Objects.toString(node.getName(), "");
        name.setText(displayedName);
    }

    private void commitName(SceneNode target, JTextField field) {
        if (!InspectorSection.canEdit(editor, target)) {
            return;
        }

        if (field.getText().equals(displayedName)) {
            scheduleRefresh();
            return;
        }

        String value = field.getText().trim();
        if (!value.isEmpty() && !value.equals(target.getName())) {
            editor.renameNode(target, value);
        }

        displayedName = Objects.toString(target.getName(), "");
        field.setText(displayedName);
    }

    private JButton addArchetypeButton(SceneNode target) {
        JButton button = InspectorSection.button("Add Archetype");

        button.addActionListener(e -> {
            if (!InspectorSection.canEdit(editor, target)) {
                return;
            }

            JPopupMenu menu = new JPopupMenu();
            for (ArchetypeDefinition definition : editor.availableArchetypes(target)) {
                menu.add(archetypeItem(target, definition));
            }
            menu.show(button, 0, button.getHeight());
        });

        return button;
    }

    private JMenuItem archetypeItem(SceneNode target, ArchetypeDefinition definition) {
        JMenuItem item = new JMenuItem(definition.getDisplayName());
        item.setToolTipText(definition.getId());
        item.setFont(KebabStyle.getCurrent().uiFont());

        item.addActionListener(e -> {
            if (!InspectorSection.canEdit(editor, target)) {
                return;
            }

            try {
                editor.attachArchetype(target, new ArchetypeInstance(definition));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Cannot add archetype",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });

        return item;
    }

    private static List<SectionState> structureOf(SceneNode selected) {
        return selected.getArchetypes()
            .stream()
            .map(InspectorPane::stateOf)
            .toList();
    }

    private static SectionState stateOf(NodeArchetype archetype) {
        if (archetype instanceof ArchetypeInstance instance) {
            var definition = instance.getDefinition();
            return new SectionState(
                archetype,
                definition.getDisplayName(),
                definition.getIcon(),
                Map.copyOf(definition.getParams())
            );
        }
        return new SectionState(archetype, archetype.archetypeId(), null, Map.of());
    }

    // The scene being played is read-only; section headings still fold
    private static void setEnabledDeep(Component component, boolean enabled) {
        if (!(component instanceof JToggleButton)) {
            component.setEnabled(enabled);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setEnabledDeep(child, enabled);
            }
        }
    }
}
