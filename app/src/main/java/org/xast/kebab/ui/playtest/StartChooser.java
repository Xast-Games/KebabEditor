package org.xast.kebab.ui.playtest;

import java.util.List;

import org.xast.kebab.playtest.PlayerStart;
import org.xast.kebab.utils.Maybe;

/** Asks which player start a playtest begins at, when the level has more than one. */
@FunctionalInterface
public interface StartChooser {

    /** Takes the suggested start without asking. */
    StartChooser SUGGESTED = (starts, suggested) -> Maybe.some(suggested);

    /**
     * @param starts    every player start of the level, in scene order
     * @param suggested the one to offer first: the selected start, else the last one played
     * @return the chosen start, or none when the user cancels
     */
    Maybe<PlayerStart> choose(List<PlayerStart> starts, PlayerStart suggested);
}
