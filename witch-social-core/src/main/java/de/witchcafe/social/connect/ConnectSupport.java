package de.witchcafe.social.connect;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.witchcafe.social.SocialProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Gemeinsame Bausteine für die OAuth-Flows der Netzwerk-Module (Konten verbinden, nicht an der
 * App anmelden - das macht witch-auth).
 *
 * <p>Konvention: Ein Modul bietet {@code /connect/{netz}} an (leitet zum Netzwerk) und
 * {@code /connect/{netz}/callback} (speichert das Konto und kehrt mit {@link #backOk} bzw.
 * {@link #back} zur Kontenübersicht zurück). Alles unter {@code /connect/**} erfordert eine
 * Anmeldung, siehe {@link ConnectSecurityConfig}.
 */
@Component
public class ConnectSupport {

    private static final Logger log = LoggerFactory.getLogger(ConnectSupport.class);
    private static final String STATE = "social.connect.state";
    public static final String ACCOUNTS_VIEW = "/konten";

    private final SocialProperties properties;
    private final SecureRandom random = new SecureRandom();

    public ConnectSupport(SocialProperties properties) {
        this.properties = properties;
    }

    /**
     * Absolute URL für Redirects. Ohne {@code social.public-base-url} die Adresse, unter der
     * der Browser die App gerade aufruft - so bleibt die Session (Cookie) beim Rücksprung
     * erhalten, egal ob localhost oder 127.0.0.1.
     */
    public String url(HttpServletRequest request, String path) {
        String base = properties.hasPublicBaseUrl()
                ? properties.publicBaseUrl()
                : ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        return SocialProperties.url(base, path);
    }

    /** Neuer zufälliger state-Wert (CSRF-Schutz), wird in der Session gemerkt. */
    public String newState(HttpSession session) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        session.setAttribute(STATE, state);
        return state;
    }

    /**
     * Prüft state (CSRF-Schutz) und Fehlerantworten des Netzwerks. Bei false wurde bereits
     * zur Kontenübersicht zurückgeleitet, bei true ist {@code code} nicht null.
     */
    public boolean validCallback(HttpSession session, @Nullable String code, @Nullable String state,
            @Nullable String error, HttpServletResponse response) throws IOException {
        Object expected = session.getAttribute(STATE);
        session.removeAttribute(STATE);
        if (error != null) {
            back(response, "Verbindung abgelehnt: " + error);
            return false;
        }
        if (code == null || state == null || !state.equals(expected)) {
            back(response, "Ungültige Antwort (state stimmt nicht), bitte erneut versuchen");
            return false;
        }
        return true;
    }

    public void fail(HttpServletResponse response, String network, RuntimeException e) throws IOException {
        log.warn("Verbinden mit {} fehlgeschlagen", network, e);
        back(response, network + ": " + e.getMessage());
    }

    /** Zurück zur Kontenübersicht mit Fehlermeldung. */
    public void back(HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(ACCOUNTS_VIEW + "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
    }

    /** Zurück zur Kontenübersicht mit Erfolgsmeldung. */
    public void backOk(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect(ACCOUNTS_VIEW + "?ok=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
