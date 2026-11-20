package org.xast.kebab.ui.playtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.xast.kebab.behavior.BehaviorContext;
import org.xast.kebab.editor.Editor;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.playtest.PathRays;
import org.xast.kebab.playtest.PathRecording;
import org.xast.kebab.playtest.PlayerStart;
import org.xast.kebab.playtest.PlaytestLevel;
import org.xast.kebab.playtest.PlaytestSession;
import org.xast.kebab.playtest.PlaytestSummary;
import org.xast.kebab.scene.model.Scene;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

/**
 * Starts and stops playtests in the viewport. A playtest runs on a snapshot of
 * the scene and never changes it; each finished run is kept as path rays.
 */
public class PlaytestController {

    private final Editor editor;
    private final PlaytestViewport viewport;
    private final UserNotice notice;
    private final LogService log;
    private final StartChooser chooser;
    private final ReachabilityCheck reachability;
    private final List<Runnable> listeners = new ArrayList<>();

    private Maybe<PlaytestSession> session = Maybe.none();
    private Maybe<Scene> playedScene = Maybe.none();
    private Maybe<Scene> raysScene = Maybe.none();
    // Offered first next time, so running the same start again is one key press
    private Maybe<SceneNode> lastStart = Maybe.none();

    /** Takes the selected player start, or the first one, without asking. */
    public PlaytestController(Editor editor, PlaytestViewport viewport, UserNotice notice, LogService log) {
        this(editor, viewport, notice, StartChooser.SUGGESTED, log);
    }

    /**
     * @param notice  tells the user why a playtest cannot start
     * @param chooser asks which player start to use when the level has several
     * @param log     where starts, stops and the run's events are written
     */
    public PlaytestController(
        Editor editor,
        PlaytestViewport viewport,
        UserNotice notice,
        StartChooser chooser,
        LogService log
    ) {
        this.editor = editor;
        this.viewport = viewport;
        this.notice = notice;
        this.chooser = chooser;
        this.log = log;
        this.reachability = new ReachabilityCheck(editor, viewport, log);

        // Opening or closing a project replaces the scene: stop playing it and drop its paths.
        editor.addProjectListener(() -> {
            if (!sameScene(playedScene)) {
                stop();
            }
            if (!sameScene(raysScene)) {
                viewport.pathRays().clear();
                raysScene = editor.scene();
            }
        });
    }

    public boolean isRunning() {
        return session.isSome();
    }

    public PathRays pathRays() {
        return viewport.pathRays();
    }

    public ReachabilityCheck reachability() {
        return reachability;
    }

    /** Called when a playtest starts or stops, e.g. to switch the Run button's icon. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void toggle() {
        if (isRunning()) {
            stop();
        } else {
            start();
        }
    }

    public void start() {
        if (isRunning()) {
            return;
        }

        Maybe<BehaviorContext> context = viewport.behaviorContext();
        if (editor.scene().isNone() || context.isNone()) {
            if (editor.isProjectOpen()) {
                notice.show("No scene open", "Create or open a scene first (File > New scene).");
            } else {
                notice.show(
                    "No project open",
                    "Open or create a project first (File > Open project / Create new project)."
                );
            }
            return;
        }

        Scene scene = editor.scene().unwrap();
        PlaytestLevel level = PlaytestLevel.build(scene, context.unwrap());

        if (level.hasNothingToStandOn()) {
            log.warn("Playtest not started: the level has nothing to stand on");
            notice.show(
                "Nothing to stand on",
                "The level has no solid objects. Add greybox shapes or colliders before running it."
            );
            return;
        }

        if (level.starts().isEmpty()) {
            log.warn("Playtest not started: the level has no player start");
            notice.show(
                "No player start",
                "The level has no player start. Add one with Edit > Add player start."
            );
            return;
        }

        Maybe<PlayerStart> chosen = chooseStart(level);
        if (chosen.isNone()) {
            return; // the user cancelled the choice
        }

        PlayerStart start = chosen.unwrap();
        lastStart = Maybe.some(start.node());
        if (start.settings().isNone()) {
            log.warn("Playtest not started: player start '" + start.name() + "' is invalid");
            notice.show(
                "Invalid player start",
                "Player start '" + start.name() + "' has invalid parameters:\n- "
                    + String.join("\n- ", start.problems())
            );
            return;
        }

        launch(scene, level, start);
    }

    private void launch(Scene scene, PlaytestLevel level, PlayerStart start) {
        PlaytestSession running = new PlaytestSession(level.world(), start, log);
        session = Maybe.some(running);
        playedScene = Maybe.some(scene);
        raysScene = Maybe.some(scene);
        editor.setPlaytesting(true);
        viewport.startPlaytest(running, new PlaytestNavigation(running, this::stop, this::failed));

        log.info(String.format(
            Locale.ROOT,
            "Playtest started at '%s' with %d solid(s) and %d trigger(s). "
                + "WASD / arrows run, Space jumps, drag to look around, Esc stops.",
            start.name(),
            level.world().solids().size(),
            level.world().triggers().size()
        ));
        notifyListeners();
    }

    public void stop() {
        if (session.isNone()) {
            return;
        }

        PlaytestSession finished = session.unwrap();
        session = Maybe.none();
        playedScene = Maybe.none();
        viewport.stopPlaytest();
        editor.setPlaytesting(false);

        PathRecording recording = finished.finish();
        log.info(PlaytestSummary.of(finished));

        if (recording.isEmpty()) {
            log.warn("Playtest stopped before the actor moved: there is no path to draw");
        } else {
            viewport.pathRays().add(recording);
        }

        notifyListeners();
    }

    public void clearPathRays() {
        if (isRunning()) {
            return;
        }
        viewport.pathRays().clear();
        log.info("Path rays cleared");
    }

    void failed(RuntimeException e) {
        log.error("Playtest stopped by an error in the simulation", e);
        stop();
    }

    // With several starts the user picks one; the selected start, else the last one played, is offered first.
    private Maybe<PlayerStart> chooseStart(PlaytestLevel level) {
        boolean startSelected = editor.selection().primary()
            .isSomeAnd(node -> level.starts().stream().anyMatch(start -> start.node() == node));
        PlayerStart suggested = level.startFor(startSelected ? editor.selection().primary() : lastStart).unwrap();

        if (level.starts().size() == 1) {
            return Maybe.some(suggested);
        }
        return chooser.choose(level.starts(), suggested);
    }

    private boolean sameScene(Maybe<Scene> scene) {
        Maybe<Scene> current = editor.scene();
        return scene.isNone() && current.isNone()
            || scene.isSome() && current.isSomeAnd(s -> s == scene.unwrap());
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
