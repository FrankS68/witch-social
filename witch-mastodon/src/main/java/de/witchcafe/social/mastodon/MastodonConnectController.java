package de.witchcafe.social.mastodon;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import de.witchcafe.social.AccountService;
import de.witchcafe.social.client.Urls;
import de.witchcafe.social.connect.ConnectSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * OAuth-Flow für Mastodon. Die App registriert sich bei jeder Instanz selbst, in keiner
 * Entwickler-Konsole muss etwas eingetragen werden.
 */
@Controller
public class MastodonConnectController {

    private static final String CALLBACK = "/connect/mastodon/callback";
    private static final String INSTANCE = "social.connect.mastodon";

    private final AccountService accountService;
    private final ConnectSupport connect;
    private final MastodonClient mastodon;
    private final MastodonAppRepository mastodonApps;
    private final MastodonProperties properties;

    public MastodonConnectController(AccountService accountService, ConnectSupport connect, MastodonClient mastodon,
            MastodonAppRepository mastodonApps, MastodonProperties properties) {
        this.accountService = accountService;
        this.connect = connect;
        this.mastodon = mastodon;
        this.mastodonApps = mastodonApps;
        this.properties = properties;
    }

    @GetMapping("/connect/mastodon")
    public void mastodon(@RequestParam String instance, HttpSession session, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            String instanceUrl = Urls.normalizeInstance(instance);
            String redirectUri = connect.url(request, CALLBACK);
            // neu registrieren, falls sich die öffentliche Adresse der App geändert hat
            MastodonApp app = mastodonApps.findById(instanceUrl)
                    .filter(existing -> existing.getRedirectUri().equals(redirectUri))
                    .orElseGet(() -> mastodonApps.save(mastodon.registerApp(instanceUrl,
                            properties.appName(), redirectUri, connect.url(request, "/"))));
            session.setAttribute(INSTANCE, instanceUrl);
            response.sendRedirect(mastodon.authorizeUrl(app, connect.newState(session)));
        } catch (RuntimeException e) {
            connect.fail(response, MastodonClient.NAME, e);
        }
    }

    @GetMapping(CALLBACK)
    public void mastodonCallback(@RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(name = "error_description", required = false) @Nullable String error,
            HttpSession session, HttpServletResponse response) throws IOException {
        if (!connect.validCallback(session, code, state, error, response) || code == null) {
            return;
        }
        Object instance = session.getAttribute(INSTANCE);
        session.removeAttribute(INSTANCE);
        MastodonApp app = instance == null ? null : mastodonApps.findById(instance.toString()).orElse(null);
        if (app == null) {
            connect.back(response, "Mastodon-Instanz unbekannt, bitte erneut verbinden");
            return;
        }
        try {
            String token = mastodon.exchangeCode(app, code);
            MastodonClient.Profile profile = mastodon.verifyCredentials(app.getInstanceUrl(), token);
            accountService.upsertMine(MastodonClient.ID, profile.acct(), account -> {
                account.setDisplayName(profile.displayName() + " (" + profile.acct() + ")");
                account.setHandle(profile.acct());
                account.setInstanceUrl(app.getInstanceUrl());
                account.setAccessToken(token);
            });
            connect.backOk(response, "Mastodon-Konto " + profile.acct() + " verbunden");
        } catch (RuntimeException e) {
            connect.fail(response, MastodonClient.NAME, e);
        }
    }
}
