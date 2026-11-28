package org.xast.kebab.ui.playtest;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.xast.kebab.math.Vec3;
import org.xast.kebab.playtest.PlayerStart;
import org.xast.kebab.ui.menu.generic.MenuButton;
import org.xast.kebab.utils.DialogKeys;
import org.xast.kebab.utils.Fonts;
import org.xast.kebab.utils.LucideIcon;
import org.xast.kebab.utils.Maybe;

/**
 * Asks which player start to run the level from. Enter or a double click plays
 * the highlighted start; Esc cancels.
 */
public final class PlayerStartDialog extends JDialog {

    private static final int ICON_SIZE = 16;

    private final JList<PlayerStart> list;
    private Maybe<PlayerStart> chosen = Maybe.none();

    private PlayerStartDialog(Window owner, List<PlayerStart> starts, PlayerStart suggested) {
        super(owner, "Choose player start", ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel prompt = new JLabel("The level has " + starts.size() + " player starts. Where should the run begin?");
        prompt.setFont(Fonts.ui());
        prompt.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        list = new JList<>(starts.toArray(PlayerStart[]::new));
        list.setFont(Fonts.ui());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new StartRenderer());
        list.setSelectedValue(suggested, true);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.locationToIndex(e.getPoint()) >= 0) {
                    play();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, Math.min(60 + starts.size() * 28, 320)));
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        center.add(scroll, BorderLayout.CENTER);

        JButton playButton = new JButton("Play", LucideIcon.PLAY.icon(ICON_SIZE, MenuButton.PLAY_BUTTON_COLOR));
        playButton.setFont(Fonts.ui());
        playButton.addActionListener(e -> play());
        list.addListSelectionListener(e -> playButton.setEnabled(list.getSelectedValue() != null));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setFont(Fonts.ui());
        cancelButton.addActionListener(e -> dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.add(playButton);
        footer.add(cancelButton);

        add(prompt, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(playButton);
        DialogKeys.closeOnEscape(this);

        pack();
        setLocationRelativeTo(owner);
    }

    /** A chooser that shows this dialog over {@code owner}. */
    public static StartChooser chooser(Window owner) {
        return (starts, suggested) -> {
            PlayerStartDialog dialog = new PlayerStartDialog(owner, starts, suggested);
            dialog.list.requestFocusInWindow();
            dialog.setVisible(true); // blocks until closed
            return dialog.chosen;
        };
    }

    private void play() {
        PlayerStart selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        chosen = Maybe.some(selected);
        dispose();
    }

    // Names can repeat, so the position tells starts apart; invalid ones are marked before they are tried.
    private static final class StartRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean selected,
            boolean focused
        ) {
            super.getListCellRendererComponent(list, value, index, selected, focused);

            if (value instanceof PlayerStart start) {
                Vec3 at = start.feet();
                String text = String.format(Locale.ROOT, "%s   (%.1f, %.1f, %.1f)%s",
                    start.name(), at.getX(), at.getY(), at.getZ(),
                    start.settings().isNone() ? "   - invalid parameters" : "");
                setText(text);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            }
            return this;
        }
    }
}
