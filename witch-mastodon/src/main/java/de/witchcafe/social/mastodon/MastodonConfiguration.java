package de.witchcafe.social.mastodon;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Aktiviert die Einstellungen von witch-mastodon. Gefunden über den Component-Scan von "de.witchcafe".
 */
@Configuration
@EnableConfigurationProperties(MastodonProperties.class)
public class MastodonConfiguration {
}
