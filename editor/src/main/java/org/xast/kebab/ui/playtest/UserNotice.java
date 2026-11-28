package org.xast.kebab.ui.playtest;

/** Tells the user something they need to act on, e.g. why a playtest cannot start. */
@FunctionalInterface
public interface UserNotice {

    void show(String title, String message);
}
