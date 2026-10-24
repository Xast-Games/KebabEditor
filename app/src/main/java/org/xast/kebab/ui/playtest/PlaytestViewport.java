package org.xast.kebab.ui.playtest;

import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.playtest.PathRays;
import org.xast.kebab.playtest.PlaytestSession;
import org.xast.kebab.playtest.reach.ReachabilityMarks;
import org.xast.kebab.ui.navigation.NavigationMode;
import org.xast.kebab.utils.Maybe;

/** What a playtest needs from the viewport it runs in. */
public interface PlaytestViewport {

    /** Resolves models and prefabs of the open project; none without one. */
    Maybe<BehaviorContext> behaviorContext();

    /** Paths of finished playtests, drawn in the viewport. */
    PathRays pathRays();

    /** The last reachability check, highlighted in the viewport. */
    ReachabilityMarks reachabilityMarks();

    /** Hand the viewport to the playtest; {@code navigation} drives its camera until {@link #stopPlaytest}. */
    void startPlaytest(PlaytestSession session, NavigationMode navigation);

    /** Give the viewport back to editing, with the camera where it was. */
    void stopPlaytest();
}
