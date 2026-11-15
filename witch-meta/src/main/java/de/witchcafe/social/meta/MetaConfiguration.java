package de.witchcafe.social.meta;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Aktiviert die Einstellungen von witch-meta. Gefunden über den Component-Scan von "de.witchcafe".
 */
@Configuration
@EnableConfigurationProperties(MetaProperties.class)
public class MetaConfiguration {
}
