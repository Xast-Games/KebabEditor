package org.xast.kebab.ui.project;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.project.ProjectAssets;
import org.xast.kebab.scene.error.SceneException;
import org.xast.kebab.ui.UnsavedChanges;
import org.xast.kebab.ui.validation.Rule;
import org.xast.kebab.ui.validation.Rules;
import org.xast.kebab.ui.validation.ValidationGroup;
import org.xast.kebab.utils.DialogKeys;
import org.xast.kebab.utils.ErrorDialogs;
import org.xast.kebab.utils.KebabStyle;
import org.xast.kebab.utils.Maybe;

/** Creates an empty scene in the open project, registers it and opens it. */
public class NewSceneDialog extends JDialog {

    private final Editor editor;
    private final Path root;
    private final JTextField folderField;
    private final JTextField nameField = new JTextField(24);
    private final JButton createButton = new JButton("Create");
    private final ValidationGroup validation = new ValidationGroup();

    public NewSceneDialog(JFrame owner, Editor editor) {
        super(owner, "New scene", true);
        this.editor = editor;
        ProjectAssets assets = editor.projectAssets().expect("No project is open");
        this.root = assets.root();
        this.folderField = new JTextField(root.relativize(assets.scenesFolder()).toString().replace('\\', '/'), 24);

        KebabStyle style = KebabStyle.getCurrent();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildFields(style), BorderLayout.CENTER);
        add(buildFooter(style), BorderLayout.SOUTH);

        validation.add(folderField, this::checkFolder);
        validation.add(nameField, Rules.all(Rules.notEmpty("Scene name"), this::checkName));
        validation.addListener(() -> createButton.setEnabled(validation.isValid()));
        createButton.setEnabled(validation.isValid());

        getRootPane().setDefaultButton(createButton);
        DialogKeys.closeOnEscape(this, this::close);
        setMinimumSize(new Dimension(460, 0));
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildFields(KebabStyle style) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 8, 0);

        constraints.gridy = 0;
        content.add(labeledRow(style, "Folder", Maybe.some("/"), folderField), constraints);

        constraints.gridy = 1;
        content.add(labeledRow(style, "Name", Maybe.some(".gltf"), nameField), constraints);

        constraints.gridy = 2;
        JLabel hint = new JLabel("<html>The folder is relative to the asset root, " + escape(root.toString())
            + ". It starts as the project's scenes folder (Project &gt; Asset folders). "
            + "Leave it empty to put the scene in the root.</html>");
        hint.setFont(style.uiFont().deriveFont(style.uiFont().getSize2D() - 1f));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        content.add(hint, constraints);

        return content;
    }

    private JPanel buildFooter(KebabStyle style) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setFont(style.uiFont());
        cancelButton.addActionListener(e -> close());

        createButton.setFont(style.uiFont());
        createButton.addActionListener(e -> create());

        footer.add(cancelButton);
        footer.add(createButton);
        return footer;
    }

    private void create() {
        if (!validation.isValid()) {
            return;
        }
        validation.hidePopups();
        if (!UnsavedChanges.confirm(this, editor, "create a new scene", "create")) {
            return;
        }
        try {
            editor.createScene(folderField.getText().strip(), nameField.getText().strip());
            close();
        } catch (SceneException e) {
            ErrorDialogs.show(this, "Scene error, cannot open the new scene", e);
        } catch (IOException e) {
            ErrorDialogs.show(this, "I/O error, cannot create the scene", e);
        }
    }

    private void close() {
        validation.hidePopups();
        dispose();
    }

    private Maybe<String> checkFolder(String text) {
        return folder(text).isSome() ? Maybe.none() : Maybe.some("The folder must be inside the asset root");
    }

    private Maybe<Path> folder(String text) {
        try {
            return Maybe.some(ProjectAssets.resolveFolder(root, text));
        } catch (IOException e) {
            return Maybe.none();
        }
    }

    private Maybe<String> checkName(String text) {
        String name = stripExtension(text.strip());
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
            return Maybe.some("Scene name must be a file name, without folders");
        }
        return sceneFile(name)
            .filter(Files::exists)
            .map(file -> "A scene named " + file.getFileName() + " already exists in this folder");
    }

    private Maybe<Path> sceneFile(String name) {
        try {
            return folder(folderField.getText()).map(dir -> dir.resolve(name + ".gltf"));
        } catch (InvalidPathException e) {
            return Maybe.none();
        }
    }

    private static String stripExtension(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".gltf") ? name.substring(0, name.length() - ".gltf".length()).strip() : name;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JPanel labeledRow(KebabStyle style, String label, Maybe<String> suffix, JComponent field) {
        field.setFont(style.uiFont());

        JPanel row = new JPanel(new BorderLayout(6, 6));
        JLabel text = new JLabel(label);
        text.setFont(style.uiFont());
        row.add(text, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);

        suffix.ifSome(suffixText -> {
            JLabel suffixLabel = new JLabel(suffixText);
            suffixLabel.setFont(style.uiFont());
            row.add(suffixLabel, BorderLayout.EAST);
        });
        return row;
    }
}
