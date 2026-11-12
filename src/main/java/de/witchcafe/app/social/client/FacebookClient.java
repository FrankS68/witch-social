package de.witchcafe.app.social.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;

import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.Network;
import tools.jackson.databind.JsonNode;

/**
 * Beiträge auf Facebook-Seiten (Graph API /{page-id}/feed bzw. /photos).
 */
@Component
public class FacebookClient implements NetworkClient {

    private final MetaGraph graph;

    public FacebookClient(MetaGraph graph) {
        this.graph = graph;
    }

    @Override
    public Network network() {
        return Network.FACEBOOK;
    }

    @Override
    public PublishResult publish(ConnectedAccount account, PublishRequest request) {
        String pageId = account.getExternalId();
        try {
            if (request.media() != null) {
                MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
                form.add("source", Multipart.file(request.media()));
                form.add("message", request.textWithLink());
                form.add("access_token", account.requireAccessToken());
                JsonNode photo = graph.http().post()
                        .uri("/{page}/photos", pageId)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                String postId = Objects.requireNonNullElse(Json.text(photo, "post_id"), Json.require(photo, "id"));
                return new PublishResult(postId, "https://www.facebook.com/" + postId);
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("message", request.text());
            if (request.link() != null && !request.link().isBlank()) {
                form.add("link", request.link());
            }
            form.add("access_token", account.requireAccessToken());
            JsonNode post = graph.http().post()
                    .uri("/{page}/feed", pageId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String postId = Json.require(post, "id");
            return new PublishResult(postId, "https://www.facebook.com/" + postId);
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.FACEBOOK, e);
        }
    }

    @Override
    public List<FeedItem> feed(ConnectedAccount account, int limit) {
        try {
            JsonNode response = graph.http().get()
                    .uri(b -> b.path("/{page}/posts")
                            .queryParam("fields", "message,created_time,permalink_url,full_picture,from")
                            .queryParam("limit", limit)
                            .queryParam("access_token", account.requireAccessToken())
                            .build(account.getExternalId()))
                    .retrieve()
                    .body(JsonNode.class);
            List<FeedItem> items = new ArrayList<>();
            for (JsonNode post : response.path("data")) {
                String from = Json.text(post.path("from"), "name");
                items.add(new FeedItem(Network.FACEBOOK, account.label(),
                        from != null ? from : account.getDisplayName(),
                        Objects.requireNonNullElse(Json.text(post, "message"), ""),
                        Json.text(post, "full_picture"),
                        Json.text(post, "permalink_url"),
                        Json.time(Json.text(post, "created_time"))));
            }
            return items;
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.FACEBOOK, e);
        }
    }
}
