package de.witchcafe.app.social.client;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import de.witchcafe.app.social.domain.Network;

/**
 * Ein Eintrag aus dem Feed eines verbundenen Kontos, netzwerkübergreifend vereinheitlicht.
 */
public record FeedItem(
        Network network,
        String accountLabel,
        String author,
        String text,
        @Nullable String imageUrl,
        @Nullable String url,
        Instant createdAt) {
}
