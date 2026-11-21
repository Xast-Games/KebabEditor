package org.xast.kebab.ui;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

import org.xast.kebab.utils.KebabStyle;

/**
 * A number field with up/down arrows: type a value, or click the arrows to step it.
 * Typed text must be a finite number inside the range, or it is rejected; it is
 * always shown with a dot decimal separator, whatever the system locale.
 */
public class NumberSpinner extends JSpinner {

    private static final int COLUMNS = 3;

    private final SpinnerNumberModel numbers;

    /** A spinner over the whole finite float range. */
    public NumberSpinner(double value, double step) {
        this(value, -Float.MAX_VALUE, Float.MAX_VALUE, step);
    }

    public NumberSpinner(double value, double min, double max, double step) {
        super(new SpinnerNumberModel(Math.clamp(value, min, max), min, max, step));
        numbers = (SpinnerNumberModel) getModel();
        // Values that came from elsewhere are shown as they are, even outside the range.
        numbers.setValue(clean(value));

        DefaultEditor editor = new DefaultEditor(this);
        setEditor(editor);
        JFormattedTextField field = editor.getTextField();
        field.setEditable(true);
        field.setColumns(COLUMNS);
        field.setFont(KebabStyle.getCurrent().techFont());
        field.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
        field.setFormatterFactory(new DefaultFormatterFactory(new StrictFormatter()));
        field.setValue(getValue());
    }

    /** The text field inside, e.g. to name it or listen for Enter. */
    public JFormattedTextField field() {
        return ((DefaultEditor) getEditor()).getTextField();
    }

    public float floatValue() {
        return ((Number) getValue()).floatValue();
    }

    /** Shows {@code value}; negative zero shows as 0. */
    public void setNumber(double value) {
        setValue(clean(value));
    }

    public void setStep(double step) {
        numbers.setStepSize(step);
    }

    /** Whether the text field has keyboard focus, i.e. the user may be typing in it. */
    public boolean isTyping() {
        return field().hasFocus();
    }

    /** Takes what was typed as the value. @throws ParseException when it isn't an acceptable number */
    public void commitText() throws ParseException {
        field().commitEdit();
    }

    private static Double clean(double value) {
        return value == 0 ? 0d : value;
    }

    /** How numbers look in every spinner, e.g. "1.0", "12.25", "0.33333". */
    public static String format(double value) {
        DecimalFormat format = new DecimalFormat("0.0####", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(clean(value));
    }

    private final class StrictFormatter extends DefaultFormatter {

        StrictFormatter() {
            setOverwriteMode(false);
            setCommitsOnValidEdit(false);
            setAllowsInvalid(true);
        }

        @Override
        public Object stringToValue(String text) throws ParseException {
            double value;
            try {
                value = Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                throw new ParseException("Not a number: " + text, 0);
            }
            double min = ((Number) numbers.getMinimum()).doubleValue();
            double max = ((Number) numbers.getMaximum()).doubleValue();
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new ParseException("Out of range: " + text, 0);
            }
            return clean(value);
        }

        @Override
        public String valueToString(Object value) {
            return value instanceof Number number ? format(number.doubleValue()) : "";
        }
    }
}
