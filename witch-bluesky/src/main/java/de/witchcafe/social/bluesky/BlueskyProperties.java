package de.witchcafe.social.bluesky;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Einstellungen unter {@code social.bluesky.*}.
 *
 * @param defaultPds Server, wenn beim Verbinden kein eigener PDS angegeben wird
 */
@ConfigurationProperties("social.bluesky")
public record BlueskyProperties(@DefaultValue("https://bsky.social") String defaultPds) {
}
