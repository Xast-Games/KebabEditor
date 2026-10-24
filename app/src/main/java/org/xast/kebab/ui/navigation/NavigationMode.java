package org.xast.kebab.ui.navigation;

import org.xast.kebab.ui.EditorView;

public interface NavigationMode {
    void attach(EditorView view);
    void detach();
    void update(double deltaSeconds);
}
