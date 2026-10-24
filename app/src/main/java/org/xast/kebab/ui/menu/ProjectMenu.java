package org.xast.kebab.ui.menu;


import javax.swing.JFrame;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.ui.archetype.ArchetypeManagerDialog;
import org.xast.kebab.ui.menu.generic.Menu;
import org.xast.kebab.ui.menu.generic.MenuItem;
import org.xast.kebab.ui.menu.generic.SingleItem;
import org.xast.kebab.ui.project.AssetScopeDialog;
import org.xast.kebab.utils.Maybe;

public class ProjectMenu extends Menu {

    public ProjectMenu(JFrame frame, Editor editor) {
        super("Project");

        SingleItem manageArchetypes = new SingleItem(
            "Manage archetypes", 
            Maybe.some("control shift A"), 
            () -> new ArchetypeManagerDialog(frame, editor).setVisible(true)
        );

        SingleItem assetFolders = new SingleItem(
            "Asset folders...",
            Maybe.none(),
            () -> new AssetScopeDialog(frame, editor).setVisible(true)
        );

        Runnable updateEnabled = () -> {
            manageArchetypes.setEnabled(editor.isProjectOpen());
            assetFolders.setEnabled(editor.isProjectOpen());
        };
        updateEnabled.run();
        editor.addProjectListener(updateEnabled);

        setItems(new MenuItem[] { manageArchetypes, assetFolders });
    }
}
