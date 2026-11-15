package de.witchcafe.social.client;

import org.jspecify.annotations.Nullable;

import de.witchcafe.social.domain.MediaFile;

/**
 * Inhalt eines Beitrags, wie ihn die Clients brauchen.
 */
public record PublishRequest(String text, @Nullable String link, @Nullable MediaFile media) {

    /** Text mit angehängtem Link, sofern der Link nicht schon im Text steht. */
    public String textWithLink() {
        if (link == null || link.isBlank() || text.contains(link)) {
            return text;
        }
        return text.isBlank() ? link : text + "\n\n" + link;
    }
}
