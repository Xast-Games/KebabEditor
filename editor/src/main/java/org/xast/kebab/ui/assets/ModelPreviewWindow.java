package org.xast.kebab.ui.assets;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import org.xast.kebab.embed.ModelPreview;
import org.xast.kebab.scene.resolve.ModelResult.ResolvedModel;
import org.xast.kebab.utils.AppIdentity;

public final class ModelPreviewWindow extends JFrame {

    public ModelPreviewWindow(ResolvedModel model) {
        super(model.path().getFileName() + " — Model Preview");
        AppIdentity.applyIcon(this);
        ModelPreview preview = new ModelPreview(model);
        add(preview.component(), BorderLayout.CENTER);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationByPlatform(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                preview.close();
            }
        });
    }
}
