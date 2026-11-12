package de.witchcafe.app.social.connect;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import de.witchcafe.app.social.AccountService;
import de.witchcafe.app.social.SocialProperties;
import de.witchcafe.app.social.client.MastodonClient;
import de.witchcafe.app.social.client.MetaGraph;
import de.witchcafe.app.social.client.PinterestClient;
import de.witchcafe.app.social.domain.MastodonApp;
import de.witchcafe.app.social.domain.MastodonAppRepository;
import de.witchcafe.app.social.domain.Network;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * OAuth-Flows zum Verbinden von Konten (nicht zum Anmelden an der App - das macht witch-auth).
 * Jeder Flow: /connect/{netz} leitet zum Netzwerk, /connect/{netz}/callback speichert das Konto
 * und kehrt zur Kontenübersicht zurück. Die Pfade erfordern eine Anmeldung.
 *
 * <p>Redirect-URIs, die in den Entwickler-Konsolen eingetragen werden müssen:
 * {@code <social.public-base-url>/connect/meta/callback} und {@code .../connect/pinterest/callback}.
 */
@Controller
public class ConnectController {

    private static final Logger log = LoggerFactory.getLogger(ConnectController.class);
    private static final String STATE = "social.connect.state";
    private static final String MASTODON_INSTANCE = "social.connect.mastodon";
    private static final String ACCOUNTS_VIEW = "/konten";

    private final AccountService accountService;
    private final SocialProperties properties;
    private final MetaGraph meta;
    private final PinterestClient pinterest;
    private final MastodonClient mastodon;
    private final MastodonAppRepository mastodonApps;
    private final SecureRandom random = new SecureRandom();

    public ConnectController(AccountService accountService, SocialProperties properties, MetaGraph meta,
            PinterestClient pinterest, MastodonClient mastodon, MastodonAppRepository mastodonApps) {
        this.accountService = accountService;
        this.properties = properties;
        this.meta = meta;
        this.pinterest = pinterest;
        this.mastodon = mastodon;
        this.mastodonApps = mastodonApps;
    }

    // ---- Facebook & Instagram (Meta) ----

    @GetMapping("/connect/meta")
    public void meta(HttpSession session, HttpServletResponse response) throws IOException {
        if (!meta.configured()) {
            back(response, "Meta-App ist nicht konfiguriert (social.meta.app-id / app-secret)");
            return;
        }
        response.sendRedirect(meta.authorizeUrl(properties.url("/connect/meta/callback"), newState(session)));
    }

    @GetMapping("/connect/meta/callback")
    public void metaCallback(@RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(name = "error_description", required = false) @Nullable String error,
            HttpSession session, HttpServletResponse response) throws IOException {
        if (!validCallback(session, code, state, error, response)) {
            return;
        }
        try {
            String owner = accountService.currentOwnerId();
            String userToken = meta.exchangeCode(code, properties.url("/connect/meta/callback"));
            var pages = meta.pages(userToken);
            int count = 0;
            for (MetaGraph.Page page : pages) {
                accountService.upsert(owner, Network.FACEBOOK, page.id(), account -> {
                    account.setDisplayName(page.name());
                    account.setAccessToken(page.accessToken());
                });
                count++;
                if (page.instagramId() != null) {
                    accountService.upsert(owner, Network.INSTAGRAM, page.instagramId(), account -> {
                        account.setDisplayName("@" + page.instagramUsername());
                        account.setHandle(page.instagramUsername());
                        account.setAccessToken(page.accessToken());
                    });
                    count++;
                }
            }
            if (count == 0) {
                back(response, "Keine Facebook-Seite gefunden. Nur Seiten (nicht persönliche Profile) können angebunden werden.");
            } else {
                backOk(response, count + " Meta-Konto/Konten verbunden");
            }
        } catch (RuntimeException e) {
            fail(response, "Meta", e);
        }
    }

    // ---- Pinterest ----

    @GetMapping("/connect/pinterest")
    public void pinterest(HttpSession session, HttpServletResponse response) throws IOException {
        if (!pinterest.configured()) {
            back(response, "Pinterest-App ist nicht konfiguriert (social.pinterest.client-id / client-secret)");
            return;
        }
        response.sendRedirect(pinterest.authorizeUrl(properties.url("/connect/pinterest/callback"), newState(session)));
    }

    @GetMapping("/connect/pinterest/callback")
    public void pinterestCallback(@RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(name = "error", required = false) @Nullable String error,
            HttpSession session, HttpServletResponse response) throws IOException {
        if (!validCallback(session, code, state, error, response)) {
            return;
        }
        try {
            PinterestClient.Token token = pinterest.exchangeCode(code, properties.url("/connect/pinterest/callback"));
            String username = pinterest.username(token.accessToken());
            accountService.upsert(accountService.currentOwnerId(), Network.PINTEREST, username, account -> {
                account.setDisplayName(username);
                account.setHandle(username);
                account.setAccessToken(token.accessToken());
                account.setRefreshToken(token.refreshToken());
                account.setTokenExpiresAt(token.expiresAt());
            });
            backOk(response, "Pinterest-Konto " + username + " verbunden");
        } catch (RuntimeException e) {
            fail(response, "Pinterest", e);
        }
    }

    // ---- Mastodon ----

    @GetMapping("/connect/mastodon")
    public void mastodon(@RequestParam String instance, HttpSession session, HttpServletResponse response)
            throws IOException {
        try {
            String instanceUrl = MastodonClient.normalizeInstance(instance);
            String redirectUri = properties.url("/connect/mastodon/callback");
            // neu registrieren, falls sich die öffentliche Adresse der App geändert hat
            MastodonApp app = mastodonApps.findById(instanceUrl)
                    .filter(existing -> existing.getRedirectUri().equals(redirectUri))
                    .orElseGet(() -> mastodonApps.save(mastodon.registerApp(instanceUrl,
                            properties.mastodon().appName(), redirectUri, properties.publicBaseUrl())));
            session.setAttribute(MASTODON_INSTANCE, instanceUrl);
            response.sendRedirect(mastodon.authorizeUrl(app, newState(session)));
        } catch (RuntimeException e) {
            fail(response, "Mastodon", e);
        }
    }

    @GetMapping("/connect/mastodon/callback")
    public void mastodonCallback(@RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(name = "error_description", required = false) @Nullable String error,
            HttpSession session, HttpServletResponse response) throws IOException {
        if (!validCallback(session, code, state, error, response)) {
            return;
        }
        Object instance = session.getAttribute(MASTODON_INSTANCE);
        session.removeAttribute(MASTODON_INSTANCE);
        MastodonApp app = instance == null ? null : mastodonApps.findById(instance.toString()).orElse(null);
        if (app == null) {
            back(response, "Mastodon-Instanz unbekannt, bitte erneut verbinden");
            return;
        }
        try {
            String token = mastodon.exchangeCode(app, code);
            MastodonClient.Profile profile = mastodon.verifyCredentials(app.getInstanceUrl(), token);
            accountService.upsert(accountService.currentOwnerId(), Network.MASTODON, profile.acct(), account -> {
                account.setDisplayName(profile.displayName() + " (" + profile.acct() + ")");
                account.setHandle(profile.acct());
                account.setInstanceUrl(app.getInstanceUrl());
                account.setAccessToken(token);
            });
            backOk(response, "Mastodon-Konto " + profile.acct() + " verbunden");
        } catch (RuntimeException e) {
            fail(response, "Mastodon", e);
        }
    }

    // ---- Hilfsmethoden ----

    private String newState(HttpSession session) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        session.setAttribute(STATE, state);
        return state;
    }

    /** Prüft state (CSRF-Schutz) und Fehlerantworten des Netzwerks. */
    private boolean validCallback(HttpSession session, @Nullable String code, @Nullable String state,
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

    private void fail(HttpServletResponse response, String network, RuntimeException e) throws IOException {
        log.warn("Verbinden mit {} fehlgeschlagen", network, e);
        back(response, network + ": " + e.getMessage());
    }

    private static void back(HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(ACCOUNTS_VIEW + "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
    }

    private static void backOk(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect(ACCOUNTS_VIEW + "?ok=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
