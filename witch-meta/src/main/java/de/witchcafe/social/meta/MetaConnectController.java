package de.witchcafe.social.meta;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import de.witchcafe.social.AccountService;
import de.witchcafe.social.connect.ConnectSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * OAuth-Flow für Facebook-Seiten und die damit verknüpften Instagram-Business-Konten.
 * Redirect-URI für die Meta-App: {@code <social.public-base-url>/connect/meta/callback}.
 */
@Controller
public class MetaConnectController {

    private static final String CALLBACK = "/connect/meta/callback";

    private final AccountService accountService;
    private final ConnectSupport connect;
    private final MetaGraph meta;

    public MetaConnectController(AccountService accountService, ConnectSupport connect, MetaGraph meta) {
        this.accountService = accountService;
        this.connect = connect;
        this.meta = meta;
    }

    @GetMapping("/connect/meta")
    public void meta(HttpSession session, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!meta.configured()) {
            connect.back(response, "Meta-App ist nicht konfiguriert (social.meta.app-id / app-secret)");
            return;
        }
        response.sendRedirect(meta.authorizeUrl(connect.url(request, CALLBACK), connect.newState(session)));
    }

    @GetMapping(CALLBACK)
    public void metaCallback(@RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(name = "error_description", required = false) @Nullable String error,
            HttpSession session, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!connect.validCallback(session, code, state, error, response) || code == null) {
            return;
        }
        try {
            String owner = accountService.currentOwnerId();
            String userToken = meta.exchangeCode(code, connect.url(request, CALLBACK));
            int count = 0;
            for (MetaGraph.Page page : meta.pages(userToken)) {
                accountService.upsert(owner, FacebookClient.ID, page.id(), account -> {
                    account.setDisplayName(page.name());
                    account.setAccessToken(page.accessToken());
                });
                count++;
                String instagramId = page.instagramId();
                if (instagramId != null) {
                    accountService.upsert(owner, InstagramClient.ID, instagramId, account -> {
                        account.setDisplayName("@" + page.instagramUsername());
                        account.setHandle(page.instagramUsername());
                        account.setAccessToken(page.accessToken());
                    });
                    count++;
                }
            }
            if (count == 0) {
                connect.back(response,
                        "Keine Facebook-Seite gefunden. Nur Seiten (nicht persönliche Profile) können angebunden werden.");
            } else {
                connect.backOk(response, count + " Meta-Konto/Konten verbunden");
            }
        } catch (RuntimeException e) {
            connect.fail(response, "Meta", e);
        }
    }
}
