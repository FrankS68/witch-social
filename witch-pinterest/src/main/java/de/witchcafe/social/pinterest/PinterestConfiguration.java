package de.witchcafe.social.pinterest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Aktiviert die Einstellungen von witch-pinterest. Gefunden über den Component-Scan von "de.witchcafe".
 */
@Configuration
@EnableConfigurationProperties(PinterestProperties.class)
public class PinterestConfiguration {
}
