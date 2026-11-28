package org.xast.kebab.ui.menu.generic;


import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.xast.kebab.utils.Fonts;
import org.xast.kebab.utils.Maybe;

public class SingleItem implements MenuItem {
    private JMenuItem component;

    public SingleItem(String label, Maybe<String> keyStroke, Runnable onClick) {

        component = new JMenuItem(label);
        component.setFont(Fonts.ui());
        component.addActionListener(e -> onClick.run());

        keyStroke.ifSome(key -> component.setAccelerator(KeyStroke.getKeyStroke(key)));
    }

    public void setEnabled(boolean enabled) {
        component.setEnabled(enabled);
    }

    @Override
    public JComponent component() {
        return component;
    }
}
