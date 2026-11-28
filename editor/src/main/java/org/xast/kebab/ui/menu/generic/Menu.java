package org.xast.kebab.ui.menu.generic;

import org.xast.kebab.utils.Fonts;

import javax.swing.JComponent;
import javax.swing.JMenu;


public class Menu implements MenuItem {
    private JMenu component;

    public Menu(String label) {

        component = new JMenu(label);
        component.setFont(Fonts.ui());
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
