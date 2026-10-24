package io.github.bobistuca.vertex.core.i18n;

/**
 * One run of text with a uniform style, independent of any server messaging API.
 *
 * @param rgb   0xRRGGBB, or {@code -1} for the default color
 * @param hover optional hover text (may be {@code null})
 */
public record RichSpan(String text, int rgb, boolean bold, boolean italic, boolean underlined, Rich hover) { }
