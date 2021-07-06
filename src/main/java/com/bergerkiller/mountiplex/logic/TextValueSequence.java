package com.bergerkiller.mountiplex.logic;

/**
 * Represents a String as a series of text-number sequences. For example,
 * the sequence 1-2-3 will store tokens with number 1, a string constant
 * of '-', a number 2, etc. This makes it possible to efficiently compare
 * version numbers. Each sequence can be compared trivially.
 */
public final class TextValueSequence implements Comparable<TextValueSequence> {
    private final String fullText;
    private boolean number;
    private int value;
    private String text;
    private TextValueSequence next;

    /**
     * Parses text into a value sequence token
     *
     * @param text Input text, e.g. "1.2.3"
     * @return value token
     */
    public static TextValueSequence parse(String text) {
        return new TextValueSequence(text);
    }

    /**
     * Combines {@link #parse(String)} with a {@link #compareTo(TextValueSequence)} operation
     *
     * @param value1 Value to be compared
     * @param value2 Value to be compared with
     * @return comparison result
     */
    public static int compareText(String value1, String value2) {
        return parse(value1).compareTo(parse(value2));
    }

    /**
     * Evaluates a logical expression
     * 
     * @param value1 value on the left side of the operand
     * @param operand to evaluate (>, >=, ==, etc.)
     * @param value2 value on the right side of the operand
     * @return True if the evaluation succeeds, False if not
     */
    public static boolean evaluateText(String value1, String operand, String value2) {
        return evaluate(parse(value1), operand, parse(value2));
    }

    /**
     * Evaluates a logical expression
     * 
     * @param value1 value on the left side of the operand
     * @param operand to evaluate (>, >=, ==, etc.)
     * @param value2 value on the right side of the operand
     * @return True if the evaluation succeeds, False if not
     */
    public static boolean evaluate(TextValueSequence value1, String operand, TextValueSequence value2) {
        int len = operand.length();
        if (len == 0 || len > 2) {
            return false;
        }
        char first = operand.charAt(0);
        char second = (len == 2) ? operand.charAt(1) : ' ';
        int comp = value1.compareTo(value2);
        if (first == '>') {
            if (second == '=') {
                return comp >= 0;
            } else {
                return comp > 0;
            }
        } else if (first == '<') {
            if (second == '=') {
                return comp <= 0;
            } else {
                return comp < 0;
            }
        } else if (first == '=' && second == '=') {
            return comp == 0;
        } else if (first == '!' && second == '=') {
            return comp != 0;
        } else {
            return false;
        }
    }

    private TextValueSequence(String text) {
        this.fullText = text;
        this.number = false;
        this.value = 0;
        int cIdx = 0;
        for (; cIdx < text.length() && Character.isDigit(text.charAt(cIdx)); cIdx++) {
            this.number = true;
        }
        if (this.number) {
            // A number, attempt parsing it
            this.text = text.substring(0, cIdx);
            try {
                this.value = Integer.parseInt(this.text);
            } catch (NumberFormatException ex) {
                this.number = false;
            }
        }
        if (!this.number) {
            // Not a number, seek until we find a digit of something that is
            for (; cIdx < text.length() && !Character.isDigit(text.charAt(cIdx)); cIdx++);
            this.text = text.substring(0, cIdx);
        }

        // Parse next value token, if it exists
        if (cIdx < text.length()) {
            this.next = new TextValueSequence(text.substring(cIdx));
        } else {
            this.next = null;
        }
    }

    @Override
    public int hashCode() {
        return this.fullText.hashCode();
    }

    @Override
    public String toString() {
        return this.fullText;
    }

    @Override
    public int compareTo(TextValueSequence o) {
        int result;
        if (this.number && o.number) {
            result = Integer.compare(this.value, o.value);
        } else {
            result = this.text.compareTo(o.text);
        }
        if (result == 0) {
            if (this.next != null && o.next != null) {
                return this.next.compareTo(o.next);
            } else if (this.next != null) {
                return 1;
            } else if (o.next != null) {
                return -1;
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object value) {
        if (value instanceof TextValueSequence) {
            return this.fullText.equals(((TextValueSequence) value).fullText);
        } else {
            return false;
        }
    }
}
