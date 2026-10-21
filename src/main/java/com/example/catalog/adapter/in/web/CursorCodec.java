package com.example.catalog.adapter.in.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.catalog.domain.exception.InvalidCursorException;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.query.Cursor;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns cursors into opaque URL-safe tokens and back. Clients only pass tokens back unchanged, so
 * the encoding can evolve freely; the version prefix lets a future format reject old tokens
 * cleanly instead of misreading them.
 */
@Component
public class CursorCodec {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "|";
    private static final int MAX_TOKEN_LENGTH = 512;

    public String encode(Cursor cursor) {
        String raw = String.join(SEPARATOR, VERSION, cursor.sortValue(), cursor.lastId().toString(), cursor.fingerprint());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(UTF_8));
    }

    public Cursor decode(String token) {
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidCursorException("cursor is malformed");
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw new InvalidCursorException("cursor is malformed");
            }
            return new Cursor(parts[1], ProductId.of(UUID.fromString(parts[2])), parts[3]);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException("cursor is malformed");
        }
    }
}
