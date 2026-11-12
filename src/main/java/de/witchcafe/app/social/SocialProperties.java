package de.witchcafe.app.social;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Einstellungen unter {@code social.*}, siehe application-local.properties.example.
 *
 * @param publicBaseUrl öffentliche Adresse der App; daraus werden OAuth-Redirect-URIs
 *                      und die Bild-URLs für Instagram gebildet
 * @param cryptoKey     Base64-kodierter 256-Bit-Schlüssel, mit dem Zugangstoken in der
 *                      Datenbank verschlüsselt werden
 */
@ConfigurationProperties("social")
public record SocialProperties(
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @Nullable String cryptoKey,
        @DefaultValue Meta meta,
        @DefaultValue Pinterest pinterest,
        @DefaultValue Mastodon mastodon,
        @DefaultValue Bluesky bluesky) {

    /** Meta-App (Facebook-Seiten und Instagram-Business-Konten). */
    public record Meta(@Nullable String appId, @Nullable String appSecret,
            @DefaultValue("v23.0") String graphVersion) {

        public boolean configured() {
            return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
        }
    }

    /** Pinterest-App; für Trial-Zugang api-base-url auf https://api-sandbox.pinterest.com setzen. */
    public record Pinterest(@Nullable String clientId, @Nullable String clientSecret,
            @DefaultValue("https://api.pinterest.com") String apiBaseUrl) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    /** Mastodon-Apps werden pro Instanz automatisch registriert. */
    public record Mastodon(@DefaultValue("Witch Social") String appName) {
    }

    public record Bluesky(@DefaultValue("https://bsky.social") String defaultPds) {
    }

    /** Absolute URL zu einem Pfad dieser App, z.B. {@code url("/connect/meta/callback")}. */
    public String url(String path) {
        return publicBaseUrl.replaceAll("/+$", "") + path;
    }
}
