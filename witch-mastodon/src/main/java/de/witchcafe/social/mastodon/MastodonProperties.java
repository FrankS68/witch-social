package de.witchcafe.social.mastodon;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Einstellungen unter {@code social.mastodon.*}. Mastodon-Apps werden pro Instanz automatisch
 * registriert, Zugangsdaten sind nicht nötig.
 *
 * @param appName Name, unter dem die App bei den Instanzen registriert wird
 */
@ConfigurationProperties("social.mastodon")
public record MastodonProperties(@DefaultValue("Witch Social") String appName) {
}
