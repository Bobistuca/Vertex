package io.github.bobistuca.vertex.core.i18n;

/** Neutralizes untrusted text (names, client brands, channels) before it reaches logs or messages. */
public final class Sanitizer {
    private Sanitizer() { }

    /**
     * Replaces control characters, the legacy formatting character, and bidirectional overrides, then caps the
     * length. The result is plain data: it is still inserted as an escaped text component, never parsed.
     */
    public static String clean(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(input.length(), maxLength + 1));
        int copied = 0;
        for (int i = 0; i < input.length() && copied < maxLength; ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            boolean bad = cp < 0x20 || (cp >= 0x7F && cp <= 0x9F) || cp == '§'
                    || (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069)
                    || cp == 0x200B || cp == 0x200E || cp == 0x200F || cp == 0xFEFF
                    || Character.isSurrogate((char) cp) && !Character.isValidCodePoint(cp);
            out.appendCodePoint(bad ? '?' : cp);
            copied++;
        }
        if (copied >= maxLength && input.codePointCount(0, input.length()) > maxLength) {
            out.append('…');
        }
        return out.toString();
    }
}
