package de.witchcafe.app.social.client;

import org.springframework.web.client.RestClientResponseException;

import de.witchcafe.app.social.domain.Network;

/**
 * Fehler beim Aufruf einer Netzwerk-API, mit lesbarer Meldung für die Oberfläche.
 */
public class SocialApiException extends RuntimeException {

    public SocialApiException(Network network, String message) {
        super(network.displayName() + ": " + message);
    }

    public SocialApiException(Network network, String message, Throwable cause) {
        super(network.displayName() + ": " + message, cause);
    }

    /** Übersetzt eine HTTP-Fehlerantwort inklusive Antworttext. */
    public static SocialApiException of(Network network, RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.length() > 500) {
            body = body.substring(0, 500) + "...";
        }
        return new SocialApiException(network, "HTTP " + e.getStatusCode().value() + " " + body, e);
    }
}
