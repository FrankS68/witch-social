package de.witchcafe.social.client;

import java.time.Instant;

import org.jspecify.annotations.Nullable;


/**
 * Ein Eintrag aus dem Feed eines verbundenen Kontos, netzwerkübergreifend vereinheitlicht.
 */
public record FeedItem(
        SocialNetwork network,
        String accountLabel,
        String author,
        String text,
        @Nullable String imageUrl,
        @Nullable String url,
        Instant createdAt) {
}
