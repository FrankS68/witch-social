package de.witchcafe.social.meta;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import de.witchcafe.social.client.Json;
import de.witchcafe.social.client.SocialApiException;
import tools.jackson.databind.JsonNode;

/**
 * Gemeinsamer Zugang zur Meta Graph API für Facebook und Instagram, inkl. OAuth.
 *
 * <p>Über die API kann nur auf Facebook-<b>Seiten</b> gepostet werden, nicht auf
 * persönliche Profile. Instagram braucht ein Business- oder Creator-Konto, das
 * mit einer dieser Seiten verknüpft ist.
 */
@Component
public class MetaGraph {

    static final String SCOPES = String.join(",",
            "pages_show_list", "pages_read_engagement", "pages_manage_posts", "business_management",
            "instagram_basic", "instagram_content_publish");

    /** Eine verwaltete Facebook-Seite und das ggf. verknüpfte Instagram-Konto. */
    public record Page(String id, String name, String accessToken,
            @Nullable String instagramId, @Nullable String instagramUsername) {
    }

    private final RestClient http;
    private final MetaProperties config;

    public MetaGraph(RestClient.Builder builder, MetaProperties config) {
        this.config = config;
        this.http = builder.baseUrl("https://graph.facebook.com/" + config.graphVersion()).build();
    }

    public boolean configured() {
        return config.configured();
    }

    RestClient http() {
        return http;
    }

    public String authorizeUrl(String redirectUri, String state) {
        return UriComponentsBuilder
                .fromUriString("https://www.facebook.com/" + config.graphVersion() + "/dialog/oauth")
                .queryParam("client_id", config.appId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("scope", SCOPES)
                .queryParam("response_type", "code")
                .encode()
                .toUriString();
    }

    /** Tauscht den Code gegen ein langlebiges Benutzer-Token (ca. 60 Tage). */
    public String exchangeCode(String code, String redirectUri) {
        try {
            JsonNode shortLived = http.get()
                    .uri(b -> b.path("/oauth/access_token")
                            .queryParam("client_id", config.appId())
                            .queryParam("client_secret", config.appSecret())
                            .queryParam("redirect_uri", redirectUri)
                            .queryParam("code", code)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode longLived = http.get()
                    .uri(b -> b.path("/oauth/access_token")
                            .queryParam("grant_type", "fb_exchange_token")
                            .queryParam("client_id", config.appId())
                            .queryParam("client_secret", config.appSecret())
                            .queryParam("fb_exchange_token", Json.require(shortLived, "access_token"))
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return Json.require(longLived, "access_token");
        } catch (RestClientResponseException e) {
            throw SocialApiException.of("Meta", e);
        }
    }

    /**
     * Seiten des Benutzers. Seiten-Token, die aus einem langlebigen Benutzer-Token
     * stammen, laufen nicht ab.
     */
    public List<Page> pages(String userToken) {
        try {
            JsonNode response = http.get()
                    .uri(b -> b.path("/me/accounts")
                            .queryParam("fields", "id,name,access_token,instagram_business_account{id,username}")
                            .queryParam("limit", 100)
                            .queryParam("access_token", userToken)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            List<Page> pages = new ArrayList<>();
            for (JsonNode page : response.path("data")) {
                JsonNode ig = page.path("instagram_business_account");
                pages.add(new Page(Json.require(page, "id"), Json.require(page, "name"),
                        Json.require(page, "access_token"),
                        ig.isObject() ? Json.text(ig, "id") : null,
                        ig.isObject() ? Json.text(ig, "username") : null));
            }
            return pages;
        } catch (RestClientResponseException e) {
            throw SocialApiException.of("Meta", e);
        }
    }
}
