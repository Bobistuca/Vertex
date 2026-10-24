package io.github.bobistuca.vertex.core.client;

import io.github.bobistuca.vertex.core.i18n.Sanitizer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

/** Defensive decoder for the {@code minecraft:brand} plugin-message payload. */
public final class BrandParser {
    public static final int MAX_PAYLOAD = 512;
    public static final int MAX_BRAND_CHARS = 64;

    private BrandParser() { }

    /** Decodes a VarInt-prefixed UTF-8 string; falls back to raw text for payloads that are not well formed. */
    public static String parse(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD) {
            return "";
        }
        int index = 0;
        int length = 0;
        int shift = 0;
        boolean wellFormed = false;
        while (index < payload.length && index < 5) {
            int b = payload[index++] & 0xFF;
            length |= (b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) == 0) {
                wellFormed = true;
                break;
            }
        }
        byte[] text;
        if (wellFormed && length >= 0 && length == payload.length - index) {
            text = java.util.Arrays.copyOfRange(payload, index, payload.length);
        } else {
            text = payload;
        }
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(text)).toString();
        } catch (CharacterCodingException e) {
            decoded = new String(text, StandardCharsets.ISO_8859_1);
        }
        return Sanitizer.clean(decoded, MAX_BRAND_CHARS);
    }
}
