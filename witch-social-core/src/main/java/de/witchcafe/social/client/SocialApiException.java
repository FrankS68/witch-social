package de.witchcafe.social.client;

import org.springframework.web.client.RestClientResponseException;

/**
 * Fehler beim Aufruf einer Netzwerk-API, mit lesbarer Meldung für die Oberfläche.
 */
public class SocialApiException extends RuntimeException {

    public SocialApiException(String network, String message) {
        super(network + ": " + message);
    }

    public SocialApiException(String network, String message, Throwable cause) {
        super(network + ": " + message, cause);
    }

    public SocialApiException(SocialNetwork network, String message) {
        this(network.displayName(), message);
    }

    public SocialApiException(SocialNetwork network, String message, Throwable cause) {
        this(network.displayName(), message, cause);
    }

    /** Übersetzt eine HTTP-Fehlerantwort inklusive Antworttext. */
    public static SocialApiException of(String network, RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.length() > 500) {
            body = body.substring(0, 500) + "...";
        }
        return new SocialApiException(network, "HTTP " + e.getStatusCode().value() + " " + body, e);
    }

    public static SocialApiException of(SocialNetwork network, RestClientResponseException e) {
        return of(network.displayName(), e);
    }
}
