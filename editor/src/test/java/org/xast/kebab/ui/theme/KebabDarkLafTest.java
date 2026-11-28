package org.xast.kebab.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class KebabDarkLafTest {

    private LookAndFeel previous;

    @BeforeEach
    void install() {
        previous = UIManager.getLookAndFeel();
        KebabDarkLaf.setup();
    }

    @AfterEach
    void restore() throws Exception {
        UIManager.setLookAndFeel(previous);
    }

    @Test
    void readsItsProperties() {
        assertEquals(KebabDarkLaf.NAME, UIManager.getLookAndFeel().getName());
        assertEquals(new Color(0x2b2d30).getRGB(), UIManager.getColor("Panel.background").getRGB());
        assertEquals(new Color(0x3574f0).getRGB(), UIManager.getColor("Button.default.background").getRGB());
    }

    @ParameterizedTest
    @ValueSource(strings = { "close", "iconify", "maximize", "restore" })
    void windowButtonsAreRound(String button) {
        assertInstanceOf(RoundWindowIcon.class, UIManager.getIcon("TitlePane." + button + "Icon"));
    }

    @ParameterizedTest
    @CsvSource({ "close, 0xee7676, 0xb94a4a", "iconify, 0x7cc482, 0x4a8a50", "maximize, 0xf2c55c, 0xba9752" })
    void windowButtonsAreGreyInAnInactiveWindowUntilHovered(String button, String hover, String pressed) {
        Icon icon = UIManager.getIcon("TitlePane." + button + "Icon");
        JButton target = new JButton();

        assertEquals(0x4f5156, edgeColor(icon, target));

        target.getModel().setRollover(true);
        assertEquals(Integer.decode(hover), edgeColor(icon, target));

        target.getModel().setArmed(true);
        target.getModel().setPressed(true);
        assertEquals(Integer.decode(pressed), edgeColor(icon, target));
    }

    /** The circle's colour near its top, above the symbol. */
    private static int edgeColor(Icon icon, JButton button) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        icon.paintIcon(button, g, 0, 0);
        g.dispose();

        return image.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2 - 6) & 0xffffff;
    }
}
