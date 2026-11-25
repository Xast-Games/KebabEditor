package org.xast.kebab.ui.inspector;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.KebabStyle;

/** Standard Swing disclosure section; no custom theme or painting. */
class InspectorSection extends JPanel {

    final JPanel body = new JPanel();
    final JToggleButton heading;

    InspectorSection(String title, Icon icon) {
        super(new BorderLayout(0, 6));

        heading = new JToggleButton(title, UIManager.getIcon("Tree.expandedIcon"), true);
        heading.setHorizontalAlignment(SwingConstants.LEADING);
        heading.setFont(KebabStyle.getCurrent().uiFont());
        heading.addActionListener(e -> toggle());

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.add(heading, BorderLayout.CENTER);
        if (icon != null) {
            header.add(new JLabel(icon), BorderLayout.EAST);
        }

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
    }

    private void toggle() {
        body.setVisible(heading.isSelected());
        updateArrow();
        revalidate();
    }

    private void updateArrow() {
        heading.setIcon(UIManager.getIcon(heading.isSelected() ? "Tree.expandedIcon" : "Tree.collapsedIcon"));
    }

    // Also runs when the theme changes, which has its own arrows.
    @Override
    public void updateUI() {
        super.updateUI();
        if (heading != null) { // not yet, in JPanel's constructor
            updateArrow();
        }
    }

    static boolean canEdit(Editor editor, SceneNode node) {
        return !editor.isPlaytesting()
            && editor.selection().primary().unwrapOr(null) == node
            && editor.containsNode(node);
    }

    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(KebabStyle.getCurrent().uiFont());
        return label;
    }

    static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(KebabStyle.getCurrent().uiFont());
        button.setAlignmentX(CENTER_ALIGNMENT);
        return button;
    }

    static void markError(JComponent component, String message) {
        component.putClientProperty("JComponent.outline", "error");
        component.setToolTipText(message);
    }

    static void clearError(JComponent component) {
        component.putClientProperty("JComponent.outline", null);
        component.setToolTipText(null);
    }
}
