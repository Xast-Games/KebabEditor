package org.xast.kebab.ui.menu.generic;

import java.util.function.Consumer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.Maybe;

public class CheckItem implements MenuItem {
    private JCheckBoxMenuItem component;

    public CheckItem(String label, Maybe<String> keyStroke, Consumer<Boolean> onToggle) {
        KebabStyle style = KebabStyle.getCurrent();

        component = new JCheckBoxMenuItem(label);
        component.setFont(style.uiFont());
        component.addActionListener(e -> onToggle.accept(component.isSelected()));

        keyStroke.ifSome(key -> component.setAccelerator(KeyStroke.getKeyStroke(key)));
    }

    /** Updates the check mark without firing the toggle callback. */
    public void setSelected(boolean selected) {
        component.setSelected(selected);
    }

    @Override
    public JComponent component() {
        return component;
    }
}
