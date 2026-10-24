package org.xast.kebab.ui.menu;

import javax.swing.JFrame;

import org.xast.kebab.KebabApplication;
import org.xast.kebab.ui.AboutDialog;
import org.xast.kebab.ui.menu.generic.Menu;
import org.xast.kebab.ui.menu.generic.MenuItem;
import org.xast.kebab.ui.menu.generic.SeparatorItem;
import org.xast.kebab.ui.menu.generic.SingleItem;
import org.xast.kebab.utils.Maybe;
import org.xast.kebab.utils.WebLinks;

public class HelpMenu extends Menu {

    public HelpMenu(JFrame frame) {
        super("Help");

        setItems(new MenuItem[] {
            new SingleItem(
                "Documentation",
                Maybe.some("F1"),
                () -> WebLinks.open(frame, KebabApplication.DOCUMENTATION_URL)
            ),
            new SeparatorItem(),
            new SingleItem(
                "About",
                Maybe.some("F12"),
                () -> new AboutDialog(frame).setVisible(true)
            )
        });
    }
}
