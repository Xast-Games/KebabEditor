package org.xast.kebab.ui.assets;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.xast.kebab.scene.resolve.ModelResult.ResolvedModel;
import org.xast.kebab.ui.navigation.FreeFlightNavigation;
import org.xast.kebab.utils.KebabStyle;

public final class ModelPreviewWindow extends JFrame {

    public ModelPreviewWindow(ResolvedModel model) {
        super(model.path().getFileName() + " — Model Preview");
        ModelPreviewView view = new ModelPreviewView(model);
        view.setNavigationMode(new FreeFlightNavigation());
        add(view, BorderLayout.CENTER);

        JLabel help = new JLabel("Drag to look · WASD to fly · wheel to dolly · read-only preview");
        help.setFont(KebabStyle.getCurrent().uiFont());
        add(help, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationByPlatform(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                view.close();
            }
        });
    }
}
