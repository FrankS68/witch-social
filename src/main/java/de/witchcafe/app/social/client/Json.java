package de.witchcafe.app.social.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.jspecify.annotations.Nullable;
import org.jsoup.Jsoup;

import tools.jackson.databind.JsonNode;

/**
 * Kleine Helfer zum Auslesen der API-Antworten.
 */
final class Json {

    private static final DateTimeFormatter FACEBOOK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private Json() {
    }

    /** Textwert oder null, wenn das Feld fehlt oder leer ist. */
    static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asString();
        return s.isBlank() ? null : s;
    }

    static String require(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalStateException("Feld '" + field + "' fehlt in der Antwort: " + node);
        }
        return value;
    }

    /** Liest die Zeitformate von Bluesky/Mastodon (ISO), Facebook (+0000) und Pinterest (ohne Zone = UTC). */
    static Instant time(@Nullable String value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // nächstes Format probieren
        }
        try {
            return OffsetDateTime.parse(value, FACEBOOK_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // nächstes Format probieren
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    /** Wandelt HTML (Mastodon-Beiträge) in reinen Text um. */
    static String plain(@Nullable String html) {
        if (html == null) {
            return "";
        }
        String withBreaks = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>\\s*<p[^>]*>", "\n\n");
        return Jsoup.parse(withBreaks).wholeText().strip();
    }
}
