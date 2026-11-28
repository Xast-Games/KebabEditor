package org.xast.kebab.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.icons.FlatAbstractIcon;

/**
 * A title bar button drawn as a coloured circle, with its symbol shown only under the pointer.
 * Circles of an inactive window are grey until hovered.
 *
 * Named in the theme's properties as {@code RoundWindowIcon, <kind>}; FlatLaf passes
 * the part after the comma to the constructor.
 */
public final class RoundWindowIcon extends FlatAbstractIcon {

    private static final float DIAMETER = 16;
    private static final float STROKE = 1.7f;
    /** Half the length of a symbol's lines: the cross is measured along an axis, so it is a little shorter. */
    private static final float CROSS = 3;
    private static final float LINE = 3.6f;

    private enum Kind { CLOSE, MAXIMIZE, ICONIFY }

    private final Kind kind;
    private final Color color;
    private final Color hover;
    private final Color pressed;
    private final Color inactive;
    private final Color symbol;

    public RoundWindowIcon(String kind) {
        this(Kind.valueOf(kind.strip().toUpperCase()), UIManager.getDimension("TitlePane.buttonSize"));
    }

    private RoundWindowIcon(Kind kind, Dimension size) {
        super(size.width, size.height, null);

        String prefix = "TitlePane.round." + kind.name().toLowerCase() + ".";
        this.kind = kind;
        this.color = UIManager.getColor(prefix + "background");
        this.hover = UIManager.getColor(prefix + "hoverBackground");
        this.pressed = UIManager.getColor(prefix + "pressedBackground");
        this.inactive = UIManager.getColor("TitlePane.round.inactiveBackground");
        this.symbol = UIManager.getColor("TitlePane.round.symbolColor");
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g) {
        ButtonModel model = c instanceof AbstractButton button ? button.getModel() : null;
        boolean isPressed = model != null && model.isPressed() && model.isArmed();
        boolean isHovered = model != null && model.isRollover();

        g.setColor(isPressed ? pressed : isHovered ? hover : isWindowActive(c) ? color : inactive);
        g.fill(new Ellipse2D.Float((width - DIAMETER) / 2, (height - DIAMETER) / 2, DIAMETER, DIAMETER));

        if (isHovered) {
            g.translate(width / 2.0, height / 2.0);
            g.setColor(symbol);
            g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            paintSymbol(g);
        }
    }

    private void paintSymbol(Graphics2D g) {
        switch (kind) {
            case CLOSE -> {
                g.draw(new Line2D.Float(-CROSS, -CROSS, CROSS, CROSS));
                g.draw(new Line2D.Float(-CROSS, CROSS, CROSS, -CROSS));
            }
            case MAXIMIZE -> {
                g.draw(new Line2D.Float(-LINE, 0, LINE, 0));
                g.draw(new Line2D.Float(0, -LINE, 0, LINE));
            }
            case ICONIFY -> g.draw(new Line2D.Float(-LINE, 0, LINE, 0));
        }
    }

    private static boolean isWindowActive(Component c) {
        Window window = SwingUtilities.getWindowAncestor(c);
        return window != null && window.isActive();
    }
}
