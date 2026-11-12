package de.witchcafe.app.social.client;

import org.jspecify.annotations.Nullable;

/**
 * @param externalId ID des Beitrags im Netzwerk
 * @param url        öffentlicher Link zum Beitrag, falls bekannt
 */
public record PublishResult(String externalId, @Nullable String url) {
}
