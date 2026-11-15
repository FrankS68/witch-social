package de.witchcafe.social.pinterest;

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
 * OAuth-Flow für Pinterest. Redirect-URI für die Pinterest-App:
 * {@code <social.public-base-url>/connect/pinterest/callback}.
 */
@Controller
public class PinterestConnectController {

    private static final String CALLBACK = "/connect/pinterest/callback";

    private final AccountService accountService;
    private final ConnectSupport connect;
    private final PinterestClient pinterest;

    public PinterestConnectController(AccountService accountService, ConnectSupport connect, PinterestClient pinterest) {
        this.accountService = accountService;
        this.connect = connect;
        this.pinterest = pinterest;
    }

    @GetMapping("/connect/pinterest")
    public void pinterest(HttpSession session, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!pinterest.configured()) {
            connect.back(response, "Pinterest-App ist nicht konfiguriert (social.pinterest.client-id / client-secret)");
            return;
        }
        response.sendRedirect(pinterest.authorizeUrl(connect.url(request, CALLBACK), connect.newState(session)));
    }

    @GetMapping(CALLBACK)
    public void pinterestCallback(@RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(name = "error", required = false) @Nullable String error,
            HttpSession session, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!connect.validCallback(session, code, state, error, response) || code == null) {
            return;
        }
        try {
            PinterestClient.Token token = pinterest.exchangeCode(code, connect.url(request, CALLBACK));
            String username = pinterest.username(token.accessToken());
            accountService.upsertMine(PinterestClient.ID, username, account -> {
                account.setDisplayName(username);
                account.setHandle(username);
                account.setAccessToken(token.accessToken());
                account.setRefreshToken(token.refreshToken());
                account.setTokenExpiresAt(token.expiresAt());
            });
            connect.backOk(response, "Pinterest-Konto " + username + " verbunden");
        } catch (RuntimeException e) {
            connect.fail(response, PinterestClient.NAME, e);
        }
    }
}
