package com.bergerkiller.mountiplex.reflection.util;

/**
 * Dumb simple String implementation with a char[] buffer, that creates
 * subsequences referencing the same buffer but a portion of it. This avoids unneeded
 * copying that happens with normal Strings. This helps performance with the template parser.
 */
public final class StringBuffer implements CharSequence {
    private final char[] buffer;
    private final int buffer_start;
    private final int buffer_length;
    private int hash = 0;
    public static final StringBuffer EMPTY = new StringBuffer();

    private StringBuffer() {
        this.buffer = new char[0];
        this.buffer_start = 0;
        this.buffer_length = 0;
    }

    public StringBuffer(char[] buffer) {
        this.buffer = buffer;
        this.buffer_start = 0;
        this.buffer_length = buffer.length;
    }

    public StringBuffer(char[] buffer, int start, int length) {
        this.buffer = buffer;
        this.buffer_start = start;
        this.buffer_length = length;
    }

    public StringBuffer(StringBuffer source, int start, int length) {
        if (start < 0) {
            throw new IndexOutOfBoundsException("Index " + start + " is out of bounds");
        }
        if ((start+length) > source.buffer_length) {
            throw new IndexOutOfBoundsException("Substring " + start + " length " + length + " is out of bounds");
        }
        this.buffer = source.buffer;
        this.buffer_start = source.buffer_start + start;
        this.buffer_length = length;
    }

    public StringBuffer(String contents) {
        this.buffer = contents.toCharArray();
        this.buffer_start = 0;
        this.buffer_length = this.buffer.length;
    }

    @Override
    public int length() {
        return this.buffer_length;
    }

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= this.buffer_length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of range of [0 ... " + (this.buffer_length - 1) + "]");
        }
        return this.buffer[this.buffer_start + index];
    }

    public String substringToString(int start, int end) {
        return String.valueOf(this.buffer, this.buffer_start + start, end - start);
    }

    public String substringToString(int start) {
        return substringToString(start, this.buffer_length);
    }

    public StringBuffer substring(int start, int end) {
        return new StringBuffer(this, start, end-start);
    }

    public StringBuffer substring(int start) {
        return new StringBuffer(this, start, this.buffer_length - start);
    }

    public StringBuffer prepend(String token) {
        int token_len = token.length();
        if (token_len == 0) {
            return this;
        } else if (this.buffer_length == 0) {
            return of(token);
        } else {
            char[] buffer = new char[this.buffer_length + token_len];
            token.getChars(0, token.length(), buffer, 0);
            System.arraycopy(this.buffer, this.buffer_start, buffer, token_len, this.buffer_length);
            return new StringBuffer(buffer, 0, buffer.length);
        }
    }

    public StringBuffer append(String token) {
        int token_len = token.length();
        if (token_len == 0) {
            return this;
        } else if (this.buffer_length == 0) {
            return of(token);
        } else {
            char[] buffer = new char[this.buffer_length + token_len];
            System.arraycopy(this.buffer, this.buffer_start, buffer, 0, this.buffer_length);
            token.getChars(0, token.length(), buffer, this.buffer_length);
            return new StringBuffer(buffer, 0, buffer.length);
        }
    }

    public StringBuffer append(StringBuffer token) {
        return join(this, token);
    }

    public StringBuffer prepend(StringBuffer token) {
        return join(token, this);
    }

    @Override
    public StringBuffer subSequence(int start, int end) {
        return this.subSequence(start, end);
    }

    public int indexOf(char token) {
        return indexOf(token, 0);
    }

    public int indexOf(char token, int startIndex) {
        int bidx = this.buffer_start + startIndex;
        for (int i = startIndex; i < this.buffer_length; i++) {
            if (this.buffer[bidx++] == token) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(String token) {
        return indexOf(token, 0);
    }

    public int indexOf(String token, int startIndex) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Token string is empty");
        }
        int token_len = token.length();
        int start = this.buffer_start + startIndex;
        int end = this.buffer_start + this.buffer_length - token_len;
        while (start < end) {
            int self_idx = start;
            int token_idx = 0;
            while (this.buffer[self_idx++] == token.charAt(token_idx++)) {
                if (token_idx == token_len) {
                    return startIndex;
                }
            }
            start++;
            startIndex++;
        }
        return -1;
    }

    public boolean startsWith(String token) {
        int len = token.length();
        if (len > this.buffer_length) {
            return false;
        }
        int bidx = this.buffer_start;
        for (int i = 0; i < len; i++) {
            if (this.buffer[bidx++] != token.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public boolean endsWith(String token) {
        int len = token.length();
        if (len > this.buffer_length) {
            return false;
        }
        int bidx = this.buffer_start + this.buffer_length;
        for (int i = len - 1; i >= 0; i--) {
            if (this.buffer[--bidx] != token.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(String text) {
        if (this.buffer_length != text.length()) {
            return false;
        }
        int bidx = this.buffer_start;
        for (int i = 0; i < this.buffer_length; i++) {
            if (this.buffer[bidx++] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(StringBuffer text) {
        if (this.buffer_length != text.buffer_length) {
            return false;
        }
        int bidx_a = this.buffer_start;
        int bidx_b = text.buffer_start;
        for (int i = 0; i < this.buffer_length; i++) {
            if (this.buffer[bidx_a++] != text.buffer[bidx_b++]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return String.valueOf(this.buffer, this.buffer_start, this.buffer_length);
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && this.buffer_length > 0) {
            char val[] = this.buffer;
            int bidx = this.buffer_start;
            for (int i = 0; i < this.buffer_length; i++) {
                h = 31 * h + val[bidx++];
            }
            hash = h;
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof StringBuffer) {
            return this.equals((StringBuffer) o);
        } else {
            return false;
        }
    }

    public static StringBuffer of(String contents) {
        if (contents.isEmpty()) {
            return EMPTY;
        } else {
            return new StringBuffer(contents);
        }
    }

    public static StringBuffer of(StringBuilder contents) {
        int len = contents.length();
        if (len == 0) {
            return EMPTY;
        } else {
            char[] buff = new char[len];
            contents.getChars(0, len, buff, 0);
            return new StringBuffer(buff);
        }
    }

    public static StringBuffer join(StringBuffer first, StringBuffer second) {
        if (first.buffer_length == 0) {
            return second;
        } else if (second.buffer_length == 0) {
            return first;
        } else {
            char[] buffer = new char[first.buffer_length + second.buffer_length];
            System.arraycopy(first.buffer, first.buffer_start, buffer, 0, first.buffer_length);
            System.arraycopy(second.buffer, second.buffer_start, buffer, first.buffer_length, second.buffer_length);
            return new StringBuffer(buffer);
        }
    }
}
