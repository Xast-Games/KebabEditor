package org.xast.kebab.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.logging.LogService;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.ui.assets.AssetExplorerTab;
import org.xast.kebab.ui.assets.AssetWindows;
import org.xast.kebab.ui.logs.LogsTab;
import org.xast.kebab.ui.menu.EditMenu;
import org.xast.kebab.ui.menu.FileMenu;
import org.xast.kebab.ui.menu.HelpMenu;
import org.xast.kebab.ui.menu.PlaytestMenu;
import org.xast.kebab.ui.menu.ProjectMenu;
import org.xast.kebab.ui.menu.ViewMenu;
import org.xast.kebab.ui.menu.generic.MenuBar;
import org.xast.kebab.ui.menu.generic.MenuButton;
import org.xast.kebab.ui.navigation.FreeFlightNavigation;
import org.xast.kebab.ui.object.ObjectDialog;
import org.xast.kebab.ui.outliner.OutlinerPane;
import org.xast.kebab.ui.playtest.PlaytestController;
import org.xast.kebab.ui.playtest.PlayerStartDialog;
import org.xast.kebab.utils.AppIdentity;
import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.LucideIcon;
import org.xast.kebab.utils.Maybe;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme;
import com.jogamp.opengl.GLException;

public class MainWindow {

    private static final String APP_TITLE = "Kebab Editor";
    private static final String RUN_TOOLTIP = "Run level (F5)";
    private static final double WORKSPACE_WEIGHT = 0.75;
    private static final int INSPECTOR_WIDTH = 350;
    private static final int INSPECTOR_MIN_WIDTH = 240;
    /** The smallest window the editor layout is designed for (NF-12). */
    private static final Dimension MIN_WINDOW_SIZE = new Dimension(1280, 720);

    private final JFrame frame;
    private final Editor editor;    
    private final LogsTab logsTab;
    private final AssetExplorerTab assetExplorer;
    private final AssetWindows assetWindows;
    private final org.xast.kebab.ui.inspector.InspectorPane inspector;
    private final SceneNavigator navigator;
    private final OutlinerPane outliner;

    private final MenuBar menuBar;
    private CollapsiblePanel inspectorPanel;
    private CollapsiblePanel bottomPanel;
    private EditorView editorView;
    private Maybe<PlaytestController> playtest = Maybe.none();

    static {
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        FlatMTGitHubDarkIJTheme.setup();
    }

    public MainWindow(Editor editorInstance) {
        setupStyle();

        editor = editorInstance;

        frame = new JFrame(APP_TITLE);
        menuBar = new MenuBar(frame);
        logsTab = new LogsTab(LogService.get());
        navigator = new SceneNavigator(frame, editor);
        assetWindows = new AssetWindows(frame, editor, navigator::open);
        assetExplorer = new AssetExplorerTab(editor, assetWindows.handler());
        inspector = new org.xast.kebab.ui.inspector.InspectorPane(editor);
        // The viewport is made later, with the layout
        outliner = new OutlinerPane(editor, nodes -> {
            if (editorView != null) {
                editorView.frame(nodes);
            }
        }, navigator);
        // File > Exit and the window manager's close (Alt+F4, the title bar) both land here.
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!UnsavedChanges.confirm(frame, editor, "exit", "exit")) {
                    return;
                }
                playtest.ifSome(PlaytestController::stop);
                inspector.close();
                outliner.close();
                assetWindows.close();
                if (editorView != null) {
                    editorView.close();
                }
                frame.dispose();
                System.exit(0);
            }
        });

        setupLayout();

        editor.addProjectListener(this::updateTitle);
        updateTitle();
    }

    private Vec3 spawnPoint() {
        return editorView != null ? editorView.spawnPoint() : Vec3.zero();
    }


    // Falls back to an explanatory placeholder when no OpenGL 3 context can be made.
    private JComponent createViewport() {
        try {
            editorView = new EditorView(editor);
            editorView.setNavigationMode(new FreeFlightNavigation());
            editorView.addNodeActivatedListener(node -> new ObjectDialog(frame, editor, node).setVisible(true));
            playtest = Maybe.some(new PlaytestController(
                editor,
                editorView.getPlaytest(),
                (title, message) -> JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE),
                PlayerStartDialog.chooser(frame),
                LogService.get()
            ));
            return editorView;
        } catch (GLException e) {
            LogService.get().error("OpenGL 3.x is not available, the 3D viewport is disabled", e);
            JLabel placeholder = new JLabel("3D viewport unavailable: OpenGL 3.x is not supported", SwingConstants.CENTER);
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(placeholder, BorderLayout.CENTER);
            return panel;
        }
    }

    private void updateTitle() {
        // A project with no scene open yet is named after its folder
        String title = editor.currentSceneFile()
            .map(file -> file.getFileName().toString())
            .or(editor.currentProjectFile().map(file -> file.toAbsolutePath().getParent().getFileName() + " (no scene)"))
            .map(name -> name + " - " + APP_TITLE)
            .unwrapOr(APP_TITLE);

        frame.setTitle(title);
    }

    public void show() {
        FocusDummy focusDummy = new FocusDummy();
        frame.add(focusDummy, BorderLayout.SOUTH);
        frame.setVisible(true);

        SwingUtilities.invokeLater(focusDummy::requestFocusInWindow);
    }

    private void setupStyle() {
        KebabStyle style = KebabStyle.getCurrent();

        // Title bar
        UIManager.put("TitlePane.iconSize", new Dimension(
            KebabStyle.ICON_WIDTH, 
            KebabStyle.ICON_HEIGHT
        ));
        UIManager.put("TitlePane.titleMargins", new Insets(12,12,12,12));
        UIManager.put("TitlePane.font", style.uiFont());

        // Tabbed panes
        UIManager.put("TabbedPane.font", style.uiFont());
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 14, 6, 14));
        UIManager.put("TabbedPane.showTabSeparators", true);

        // Focused items
        UIManager.put("Component.focusWidth", 3);
        UIManager.put("Component.focusColor", new Color(172, 108, 64, 172));

        // File chooser
        UIManager.put("FileChooser.font", style.uiFont());
        UIManager.put("FileChooser.listFont", style.uiFont());
        UIManager.put("FileChooser.textFont", style.uiFont());
        UIManager.put("FileChooser.buttonFont", style.uiFont());
        UIManager.put("FileChooser.labelFont", style.uiFont());

        // Menu
        UIManager.put("PopupMenu.borderInsets", new Insets(6, 0, 6, 0));
    }

    private void setupLayout() {
        setupFrame();
        frame.add(createLayout(), BorderLayout.CENTER);
        setupMenuBar();
    }

    private void setupFrame() {
        KebabStyle style = KebabStyle.getCurrent();

        // The close listener exits, after asking about unsaved changes.
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(style.windowSize().width, style.windowSize().height);
        frame.setMinimumSize(MIN_WINDOW_SIZE);
        frame.setLocationRelativeTo(null);
        AppIdentity.applyIcon(frame);
        frame.setLayout(new BorderLayout());
    }

    // The workspace on the left, the inspector and outliner as tabs on the right
    private JSplitPane createLayout() {
        JTabbedPane side = new JTabbedPane();
        side.addTab("Inspector", inspector);
        side.addTab("Outliner", outliner);
        side.setMinimumSize(new Dimension(INSPECTOR_MIN_WIDTH, 0));

        JSplitPane layout = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            createWorkspace(),
            side
        );
        inspectorPanel = new CollapsiblePanel(layout, side);
        layout.setResizeWeight(1.0);
        layout.setContinuousLayout(false);
        SwingUtilities.invokeLater(() -> {
            int dividerLocation = layout.getWidth() - INSPECTOR_WIDTH - layout.getDividerSize();
            layout.setDividerLocation(Math.max(INSPECTOR_MIN_WIDTH, dividerLocation));
        });

        return layout;
    }

    // The 3D viewport above a resizable bottom tool window
    private JSplitPane createWorkspace() {
        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.addTab("Asset Explorer", assetExplorer);
        bottomTabs.addTab("Logs", logsTab);

        JSplitPane workspace = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            createViewport(),
            bottomTabs
        );
        bottomPanel = new CollapsiblePanel(workspace, bottomTabs);
        workspace.setContinuousLayout(false);
        workspace.setResizeWeight(WORKSPACE_WEIGHT);
        SwingUtilities.invokeLater(() -> workspace.setDividerLocation(WORKSPACE_WEIGHT));

        return workspace;
    }

    private void setupMenuBar() {
        menuBar.addMenu(new FileMenu(frame, editor, assetExplorer::refresh));
        menuBar.addMenu(new ProjectMenu(frame, editor));
        menuBar.addMenu(new EditMenu(frame, editor, this::spawnPoint, Maybe.ofNullable(editorView), navigator));
        menuBar.addMenu(new ViewMenu(
            frame,
            editor,
            Maybe.ofNullable(editorView),
            Maybe.some(inspectorPanel),
            Maybe.some(bottomPanel)
        ));
        menuBar.addMenu(new PlaytestMenu(playtest));
        menuBar.addMenu(new HelpMenu(frame));

        // Glue between the menus and the buttons
        menuBar.add(Box.createGlue());

        menuBar.add(createRunButton());
        menuBar.add(createMoreButton());
    }

    private MenuButton createRunButton() {
        MenuButton runButton = new MenuButton(
            LucideIcon.PLAY,
            RUN_TOOLTIP,
            MenuButton.PLAY_BUTTON_COLOR,
            this::runLevel
        );

        playtest.ifSome(controller -> controller.addListener(() -> {
            if (controller.isRunning()) {
                runButton.setAppearance(LucideIcon.SQUARE, MenuButton.STOP_BUTTON_COLOR, "Stop playtest (Esc)");
            } else {
                runButton.setAppearance(LucideIcon.PLAY, MenuButton.PLAY_BUTTON_COLOR, RUN_TOOLTIP);
            }
        }));

        return runButton;
    }

    private void runLevel() {
        playtest.ifSomeOrElse(
            PlaytestController::toggle,
            () -> JOptionPane.showMessageDialog(
                frame,
                "The 3D viewport is unavailable, so the level cannot be run.",
                "Run level",
                JOptionPane.INFORMATION_MESSAGE
            )
        );
    }

    private MenuButton createMoreButton() {
        return new MenuButton(
            LucideIcon.ELLIPSIS_VERTICAL,
            "More...",
            new Insets(8, 2, 8, 2),
            () -> {
                // TODO: More...
            }
        );
    }
}
