package de.witchcafe.app.social.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import de.witchcafe.app.social.SocialProperties;
import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.ConnectedAccountRepository;
import de.witchcafe.app.social.domain.Network;
import tools.jackson.databind.JsonNode;

/**
 * Pinterest API v5. Pins brauchen ein Bild und ein Board; das Board wird pro
 * Konto in der Kontenübersicht gewählt (sonst das erste Board).
 *
 * <p>Apps mit Trial-Zugang dürfen nur gegen die Sandbox schreiben
 * ({@code social.pinterest.api-base-url=https://api-sandbox.pinterest.com}).
 */
@Component
public class PinterestClient implements NetworkClient {

    static final String SCOPES = "boards:read,pins:read,pins:write,user_accounts:read";

    public record Token(String accessToken, @Nullable String refreshToken, @Nullable Instant expiresAt) {
    }

    public record Board(String id, String name) {
    }

    private final RestClient http;
    private final SocialProperties.Pinterest config;
    private final ConnectedAccountRepository accounts;

    public PinterestClient(RestClient.Builder builder, SocialProperties properties,
            ConnectedAccountRepository accounts) {
        this.config = properties.pinterest();
        this.http = builder.baseUrl(config.apiBaseUrl()).build();
        this.accounts = accounts;
    }

    @Override
    public Network network() {
        return Network.PINTEREST;
    }

    public boolean configured() {
        return config.configured();
    }

    public String authorizeUrl(String redirectUri, String state) {
        return UriComponentsBuilder.fromUriString("https://www.pinterest.com/oauth/")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    public Token exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        return tokenRequest(form);
    }

    private Token tokenRequest(MultiValueMap<String, String> form) {
        String basic = Base64.getEncoder().encodeToString(
                (config.clientId() + ":" + config.clientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode token = http.post()
                    .uri("/v5/oauth/token")
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            long expiresIn = token.path("expires_in").asLong(0);
            return new Token(Json.require(token, "access_token"), Json.text(token, "refresh_token"),
                    expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null);
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.PINTEREST, e);
        }
    }

    /** Liefert ein gültiges Token und erneuert es bei Bedarf über das Refresh-Token. */
    private String accessToken(ConnectedAccount account) {
        Instant expires = account.getTokenExpiresAt();
        if (expires == null || expires.isAfter(Instant.now().plus(Duration.ofMinutes(5)))
                || account.getRefreshToken() == null) {
            return account.requireAccessToken();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", account.getRefreshToken());
        Token token = tokenRequest(form);
        account.setAccessToken(token.accessToken());
        if (token.refreshToken() != null) {
            account.setRefreshToken(token.refreshToken());
        }
        account.setTokenExpiresAt(token.expiresAt());
        accounts.save(account);
        return token.accessToken();
    }

    public String username(String accessToken) {
        try {
            JsonNode user = http.get()
                    .uri("/v5/user_account")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            return Json.require(user, "username");
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.PINTEREST, e);
        }
    }

    public List<Board> boards(ConnectedAccount account) {
        try {
            JsonNode response = http.get()
                    .uri("/v5/boards?page_size=100")
                    .header("Authorization", "Bearer " + accessToken(account))
                    .retrieve()
                    .body(JsonNode.class);
            List<Board> boards = new ArrayList<>();
            for (JsonNode board : response.path("items")) {
                boards.add(new Board(Json.require(board, "id"), Json.require(board, "name")));
            }
            return boards;
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.PINTEREST, e);
        }
    }

    @Override
    public PublishResult publish(ConnectedAccount account, PublishRequest request) {
        if (request.media() == null) {
            throw new SocialApiException(Network.PINTEREST, "Pinterest braucht ein Bild");
        }
        String boardId = account.getBoardId();
        if (boardId == null) {
            boardId = boards(account).stream().findFirst()
                    .orElseThrow(() -> new SocialApiException(Network.PINTEREST, "Kein Board vorhanden"))
                    .id();
        }
        String text = request.text().strip();
        String title = text.lines().findFirst().orElse("");
        if (title.length() > 100) {
            title = title.substring(0, 99) + "…";
        }

        Map<String, Object> pin = new LinkedHashMap<>();
        pin.put("board_id", boardId);
        pin.put("title", title);
        pin.put("description", text.length() > 500 ? text.substring(0, 500) : text);
        if (request.link() != null && !request.link().isBlank()) {
            pin.put("link", request.link());
        }
        pin.put("media_source", Map.of(
                "source_type", "image_base64",
                "content_type", request.media().getContentType(),
                "data", Base64.getEncoder().encodeToString(request.media().getData())));
        try {
            JsonNode created = http.post()
                    .uri("/v5/pins")
                    .header("Authorization", "Bearer " + accessToken(account))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pin)
                    .retrieve()
                    .body(JsonNode.class);
            String id = Json.require(created, "id");
            return new PublishResult(id, "https://www.pinterest.com/pin/" + id + "/");
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.PINTEREST, e);
        }
    }

    @Override
    public List<FeedItem> feed(ConnectedAccount account, int limit) {
        try {
            JsonNode response = http.get()
                    .uri("/v5/pins?page_size={size}", Math.min(limit, 100))
                    .header("Authorization", "Bearer " + accessToken(account))
                    .retrieve()
                    .body(JsonNode.class);
            List<FeedItem> items = new ArrayList<>();
            for (JsonNode pin : response.path("items")) {
                String title = Json.text(pin, "title");
                String description = Json.text(pin, "description");
                String text = title != null && description != null ? title + "\n" + description
                        : Objects.requireNonNullElse(title != null ? title : description, "");
                String id = Json.require(pin, "id");
                items.add(new FeedItem(Network.PINTEREST, account.label(), account.getDisplayName(), text,
                        Json.text(pin.path("media").path("images").path("600x"), "url"),
                        "https://www.pinterest.com/pin/" + id + "/",
                        Json.time(Json.text(pin, "created_at"))));
            }
            return items;
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.PINTEREST, e);
        }
    }
}
