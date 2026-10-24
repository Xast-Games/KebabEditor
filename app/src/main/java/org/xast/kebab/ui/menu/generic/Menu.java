package org.xast.kebab.ui.menu.generic;

import javax.swing.JComponent;
import javax.swing.JMenu;

import org.xast.kebab.utils.KebabStyle;

public class Menu implements MenuItem {
    private JMenu component;

    public Menu(String label) {
        KebabStyle style = KebabStyle.getCurrent();

        component = new JMenu(label);
        component.setFont(style.uiFont());
    }

    public Menu(String label, MenuItem[] items) {
        this(label);
        setItems(items);
    }

    public void setItems(MenuItem[] items) {
        component.removeAll();

        for (MenuItem item : items) {
            component.add(item.component());
        }
    }

    @Override
    public JComponent component() {
        return component;
    }
}
