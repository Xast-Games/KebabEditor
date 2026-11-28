package org.xast.kebab.ui.project;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.project.ProjectAssets;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.utils.DialogKeys;
import org.xast.kebab.utils.ErrorDialogs;
import org.xast.kebab.utils.FileChooser;
import org.xast.kebab.utils.Fonts;
import org.xast.kebab.utils.Maybe;
import org.xast.kebab.utils.FileChooser.FileChooserMode;

public class AssetScopeDialog extends JDialog {

    private final Editor editor;
    private final Maybe<Path> scene;
    private final Path originalRoot;
    private final JTextField rootField = new JTextField();
    private final JTextField scenesFolderField = new JTextField();
    private final DefaultListModel<Path> folders = new DefaultListModel<>();
    private final DefaultListModel<Path> scenes = new DefaultListModel<>();
    private final List<JList<Path>> views = new ArrayList<>();
    private Path root;
    // Where File > New scene puts scenes by default
    private Path scenesFolder;

    public AssetScopeDialog(JFrame owner, Editor editor) {
        super(owner, "Asset folders", true);
        this.editor = editor;
        ProjectAssets assets = editor.projectAssets().unwrap();
        scene = editor.currentSceneFile().map(file -> file.toAbsolutePath().normalize());
        originalRoot = assets.root();
        root = originalRoot;
        scenesFolder = assets.scenesFolder();
        folders.addAll(assets.folders());
        scenes.addAll(assets.scenes());

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 8));
        rows.add(rootRow());
        rows.add(scenesFolderRow());
        add(rows, BorderLayout.NORTH);
        JPanel lists = new JPanel(new GridLayout(2, 1, 0, 12));
        lists.add(listPanel("Asset folders", folders, this::addFolder));
        lists.add(listPanel("Scenes (open as editable scenes)", scenes, this::addScene));
        add(lists, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);

        setMinimumSize(new Dimension(560, 480));
        DialogKeys.closeOnEscape(this);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel rootRow() {
        JLabel label = new JLabel("Asset root");
        label.setFont(Fonts.ui());
        rootField.setFont(Fonts.ui());
        rootField.setEditable(false);
        rootField.setText(root.toString());
        JButton change = new JButton("Change...");
        change.setFont(Fonts.ui());
        change.addActionListener(event -> chooseRoot());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(label, BorderLayout.WEST);
        row.add(rootField, BorderLayout.CENTER);
        row.add(change, BorderLayout.EAST);
        return row;
    }

    private JPanel scenesFolderRow() {
        JLabel label = new JLabel("Scenes folder");
        label.setFont(Fonts.ui());
        label.setToolTipText("Where File > New scene puts scenes by default");
        scenesFolderField.setFont(Fonts.ui());
        scenesFolderField.setEditable(false);
        showScenesFolder();
        JButton change = new JButton("Change...");
        change.setFont(Fonts.ui());
        change.addActionListener(event -> new FileChooser(FileChooserMode.DIRS)
            .startIn(scenesFolder)
            .open(this, this::changeScenesFolder));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(label, BorderLayout.WEST);
        row.add(scenesFolderField, BorderLayout.CENTER);
        row.add(change, BorderLayout.EAST);
        return row;
    }

    private void changeScenesFolder(File file) {
        Path candidate = file.toPath().toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            error("Choose a folder inside the asset root, or change the asset root first:\n" + root);
            return;
        }
        scenesFolder = candidate;
        showScenesFolder();
    }

    private void showScenesFolder() {
        scenesFolderField.setText(relative(scenesFolder));
    }

    private String relative(Path path) {
        String text = root.relativize(path).toString().replace('\\', '/');
        return text.isEmpty() ? "." : text;
    }

    private void chooseRoot() {
        new FileChooser(FileChooserMode.DIRS)
            .startIn(root)
            .open(this, this::changeRoot);
    }

    private void changeRoot(File file) {
        Path candidate = file.toPath().toAbsolutePath().normalize();
        if (scene.isSomeAnd(open -> !open.startsWith(candidate))) {
            error("The asset root must be a folder that contains the open scene:\n" + scene.unwrap());
            return;
        }
        List<String> outside = Stream.concat(Stream.concat(Collections.list(folders.elements()).stream(),
                Collections.list(scenes.elements()).stream()), Stream.of(scenesFolder))
            .filter(path -> !path.startsWith(candidate))
            .map(Path::toString)
            .toList();
        if (!outside.isEmpty()) {
            error("Remove or change these entries first, they are outside the new root:\n" + String.join("\n", outside));
            return;
        }
        root = candidate;
        rootField.setText(root.toString());
        showScenesFolder();
        views.forEach(JList::repaint);
    }

    private JPanel listPanel(String title, DefaultListModel<Path> model, Runnable add) {
        JLabel label = new JLabel(title);
        label.setFont(Fonts.ui());
        JList<Path> list = new JList<>(model);
        list.setFont(Fonts.ui());
        views.add(list);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focus
            ) {
                String text = root.relativize((Path) value).toString();
                return super.getListCellRendererComponent(list, text.isEmpty() ? "." : text, index, selected, focus);
            }
        });

        JButton addButton = new JButton("Add...");
        addButton.setFont(Fonts.ui());
        addButton.addActionListener(event -> add.run());
        JButton removeButton = new JButton("Remove");
        removeButton.setFont(Fonts.ui());
        removeButton.addActionListener(event -> list.getSelectedValuesList().forEach(model::removeElement));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(addButton);
        buttons.add(removeButton);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel footer() {
        JButton cancel = new JButton("Cancel");
        cancel.setFont(Fonts.ui());
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton("Save");
        save.setFont(Fonts.ui());
        save.addActionListener(event -> save());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.add(cancel);
        footer.add(save);
        return footer;
    }

    private void addFolder() {
        new FileChooser(FileChooserMode.DIRS)
            .startIn(root)
            .open(this, file -> addTo(folders, file));
    }

    private void addScene() {
        new FileChooser(FileChooserMode.FILES)
            .addFilter("glTF scene", "gltf", "glb")
            .startIn(root)
            .open(this, file -> addTo(scenes, file));
    }

    private void addTo(DefaultListModel<Path> model, File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            error("Choose a path inside the asset root, or change the asset root first:\n" + root);
            return;
        }
        if (!model.contains(path)) {
            model.addElement(path);
        }
    }

    private void save() {
        boolean moved = !root.equals(originalRoot);
        if (moved && !confirmReload()) {
            return;
        }
        try {
            editor.updateAssetScope(root, relatives(folders), relatives(scenes), relative(scenesFolder));
            dispose();
        } catch (SceneException e) {
            ErrorDialogs.show(this, "Cannot reload the scene", e);
        } catch (IOException e) {
            ErrorDialogs.show(this, "Cannot save asset folders", e);
        }
    }

    private boolean confirmReload() {
        int answer = JOptionPane.showConfirmDialog(
            this,
            "Moving the asset root moves kebab-project.json and reloads the scene.\n"
                + "Unsaved changes will be lost. Continue?",
            "Move asset root",
            JOptionPane.OK_CANCEL_OPTION
        );
        return answer == JOptionPane.OK_OPTION;
    }

    private List<String> relatives(DefaultListModel<Path> model) {
        return Collections.list(model.elements())
            .stream()
            .map(path -> root.relativize(path).toString().replace('\\', '/'))
            .filter(relative -> !relative.isEmpty())
            .toList();
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Asset folders", JOptionPane.ERROR_MESSAGE);
    }
}
