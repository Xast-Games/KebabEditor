package org.xast.kebab.embed;

import com.jogamp.opengl.GLProfile;

/**
 * Set-up for hosts that embed Kebab's previews and scenes. Kebab's panels take their fonts from the
 * Look and Feel: {@code defaultFont} for text and {@code monospaced.font} for numbers.
 */
public final class Embedding {

    private Embedding() {}

    /**
     * Sets up OpenGL ahead of the first preview or scene, which otherwise waits about a second for it.
     * Call it from a background thread, and show nothing with OpenGL until it has returned.
     */
    public static void prepare() {
        GLProfile.initSingleton();
    }
}
