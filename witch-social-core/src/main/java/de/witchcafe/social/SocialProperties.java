package de.witchcafe.social;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allgemeine Einstellungen unter {@code social.*}, siehe application-local.properties.example.
 * Die Netzwerk-Module haben ihre eigenen Einstellungen darunter ({@code social.meta.*},
 * {@code social.pinterest.*}, ...).
 *
 * @param publicBaseUrl öffentliche Adresse der App; daraus werden OAuth-Redirect-URIs
 *                      und die Bild-URLs für Instagram gebildet. Leer = die Adresse, unter
 *                      der die App gerade aufgerufen wird (lokal z.B. http://127.0.0.1:8080)
 * @param cryptoKey     Base64-kodierter 256-Bit-Schlüssel, mit dem Zugangstoken in der
 *                      Datenbank verschlüsselt werden
 */
@ConfigurationProperties("social")
public record SocialProperties(
        @Nullable String publicBaseUrl,
        @Nullable String cryptoKey) {

    public boolean hasPublicBaseUrl() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    /**
     * Absolute URL zu einem Pfad dieser App ohne Request-Bezug (z.B. Instagram-Bild-URLs
     * im Scheduler). Ohne {@code social.public-base-url} wird http://localhost:8080 angenommen.
     */
    public String url(String path) {
        return url(hasPublicBaseUrl() ? publicBaseUrl : "http://localhost:8080", path);
    }

    public static String url(String baseUrl, String path) {
        return baseUrl.replaceAll("/+$", "") + path;
    }
}
