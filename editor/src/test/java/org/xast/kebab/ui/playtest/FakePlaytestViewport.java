package org.xast.kebab.ui.playtest;

import org.xast.kebab.playtest.PhysicsTestSupport;
import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.playtest.PathRays;
import org.xast.kebab.playtest.PlaytestSession;
import org.xast.kebab.playtest.reach.ReachabilityMarks;
import org.xast.kebab.ui.navigation.NavigationMode;
import org.xast.kebab.utils.Maybe;

/** A viewport without OpenGL: remembers what the playtest asked of it. */
public final class FakePlaytestViewport implements PlaytestViewport {

    public Maybe<BehaviorContext> context = Maybe.some(PhysicsTestSupport.NO_ASSETS);
    public final PathRays pathRays = new PathRays();
    public final ReachabilityMarks reachabilityMarks = new ReachabilityMarks();

    public Maybe<PlaytestSession> playing = Maybe.none();
    public Maybe<NavigationMode> navigation = Maybe.none();
    public int stops;

    @Override
    public Maybe<BehaviorContext> behaviorContext() {
        return context;
    }

    @Override
    public PathRays pathRays() {
        return pathRays;
    }

    @Override
    public ReachabilityMarks reachabilityMarks() {
        return reachabilityMarks;
    }

    @Override
    public void startPlaytest(PlaytestSession session, NavigationMode navigation) {
        this.playing = Maybe.some(session);
        this.navigation = Maybe.some(navigation);
    }

    @Override
    public void stopPlaytest() {
        playing = Maybe.none();
        navigation = Maybe.none();
        stops++;
    }
}
