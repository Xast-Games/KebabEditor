package org.xast.kebab.ui.menu.generic;

import org.xast.kebab.utils.Fonts;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;


public class MenuBar extends JMenuBar {
    public MenuBar(JFrame frame) {

        setFont(Fonts.ui());
        frame.setJMenuBar(this);
    }

    public void addMenu(Menu menu) {
        add(menu.component());
    }

    public void addMenu(String label, MenuItem[] items) {
        add(new JMenu(label) {{
            for (MenuItem item : items) {
                add(item.component());
            }
        }});
    }

    public void addBarSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(8, 24));
        add(separator);
    }
}
