package de.witchcafe.social.bluesky;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Aktiviert die Einstellungen von witch-bluesky. Gefunden über den Component-Scan von "de.witchcafe".
 */
@Configuration
@EnableConfigurationProperties(BlueskyProperties.class)
public class BlueskyConfiguration {
}
