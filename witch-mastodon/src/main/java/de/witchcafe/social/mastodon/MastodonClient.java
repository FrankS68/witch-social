package de.witchcafe.social.mastodon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import de.witchcafe.social.client.FeedItem;
import de.witchcafe.social.client.Json;
import de.witchcafe.social.client.Multipart;
import de.witchcafe.social.client.PublishRequest;
import de.witchcafe.social.client.PublishResult;
import de.witchcafe.social.client.SocialApiException;
import de.witchcafe.social.client.SocialNetwork;
import de.witchcafe.social.domain.ConnectedAccount;
import tools.jackson.databind.JsonNode;

/**
 * Mastodon über die REST-API der jeweiligen Instanz. Die OAuth-App wird pro
 * Instanz automatisch registriert ({@link #registerApp}).
 */
@Component
public class MastodonClient implements SocialNetwork {

    public static final String ID = "MASTODON";
    public static final String NAME = "Mastodon";

    static final String SCOPES = "read write";

    public record Profile(String id, String acct, String displayName) {
    }

    private final RestClient http;

    public MastodonClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return NAME;
    }

    @Override
    public String color() {
        return "#6364FF";
    }

    @Override
    public int maxLength() {
        return 500;
    }

    /** Registriert diese App bei einer Instanz (POST /api/v1/apps). */
    public MastodonApp registerApp(String instanceUrl, String appName, String redirectUri, String website) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_name", appName);
        form.add("redirect_uris", redirectUri);
        form.add("scopes", SCOPES);
        form.add("website", website);
        try {
            JsonNode app = http.post()
                    .uri(instanceUrl + "/api/v1/apps")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return new MastodonApp(instanceUrl, Json.require(app, "client_id"), Json.require(app, "client_secret"),
                    redirectUri);
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(NAME, e);
        }
    }

    public String authorizeUrl(MastodonApp app, String state) {
        return UriComponentsBuilder.fromUriString(app.getInstanceUrl() + "/oauth/authorize")
                .queryParam("client_id", app.getClientId())
                .queryParam("redirect_uri", app.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    public String exchangeCode(MastodonApp app, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", app.getClientId());
        form.add("client_secret", app.getClientSecret());
        form.add("redirect_uri", app.getRedirectUri());
        form.add("scope", SCOPES);
        try {
            JsonNode token = http.post()
                    .uri(app.getInstanceUrl() + "/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return Json.require(token, "access_token");
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(NAME, e);
        }
    }

    public Profile verifyCredentials(String instanceUrl, String accessToken) {
        try {
            JsonNode me = http.get()
                    .uri(instanceUrl + "/api/v1/accounts/verify_credentials")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            String acct = Json.require(me, "acct");
            String displayName = Json.text(me, "display_name");
            String host = instanceUrl.replaceFirst("https?://", "");
            return new Profile(Json.require(me, "id"), "@" + acct + "@" + host,
                    displayName != null ? displayName : acct);
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(NAME, e);
        }
    }

    @Override
    public PublishResult publish(ConnectedAccount account, PublishRequest request) {
        String instance = Objects.requireNonNull(account.getInstanceUrl(), "Mastodon-Instanz fehlt");
        String auth = "Bearer " + account.requireAccessToken();
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("status", request.textWithLink());
            if (request.media() != null) {
                MultiValueMap<String, Object> upload = new LinkedMultiValueMap<>();
                upload.add("file", Multipart.file(request.media()));
                JsonNode media = http.post()
                        .uri(instance + "/api/v2/media")
                        .header("Authorization", auth)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(upload)
                        .retrieve()
                        .body(JsonNode.class);
                form.add("media_ids[]", Json.require(media, "id"));
            }
            JsonNode status = http.post()
                    .uri(instance + "/api/v1/statuses")
                    .header("Authorization", auth)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return new PublishResult(Json.require(status, "id"), Json.text(status, "url"));
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(NAME, e);
        }
    }

    @Override
    public List<FeedItem> feed(ConnectedAccount account, int limit) {
        String instance = Objects.requireNonNull(account.getInstanceUrl(), "Mastodon-Instanz fehlt");
        try {
            JsonNode statuses = http.get()
                    .uri(instance + "/api/v1/timelines/home?limit={limit}", limit)
                    .header("Authorization", "Bearer " + account.requireAccessToken())
                    .retrieve()
                    .body(JsonNode.class);
            List<FeedItem> items = new ArrayList<>();
            for (JsonNode entry : statuses) {
                // Boosts: den geteilten Original-Beitrag anzeigen
                JsonNode status = entry.path("reblog").isObject() ? entry.path("reblog") : entry;
                JsonNode author = status.path("account");
                String name = Json.text(author, "display_name");
                JsonNode media = status.path("media_attachments");
                String image = media.isArray() && !media.isEmpty() ? Json.text(media.get(0), "preview_url") : null;
                items.add(new FeedItem(this, label(account),
                        (name != null ? name + " " : "") + "@" + Json.text(author, "acct"),
                        Json.plain(Json.text(status, "content")),
                        image,
                        Json.text(status, "url"),
                        Json.time(Json.text(entry, "created_at"))));
            }
            return items;
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(NAME, e);
        }
    }
}
