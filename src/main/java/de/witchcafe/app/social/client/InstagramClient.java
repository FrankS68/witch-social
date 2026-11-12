package de.witchcafe.app.social.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;

import de.witchcafe.app.social.MediaController;
import de.witchcafe.app.social.SocialProperties;
import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.Network;
import tools.jackson.databind.JsonNode;

/**
 * Instagram-Business-/Creator-Konten über die Graph API (Content Publishing).
 *
 * <p>Instagram lädt das Bild selbst von einer öffentlichen URL und akzeptiert nur
 * JPEG. Lokal (localhost) funktioniert das Veröffentlichen deshalb nicht -
 * {@code social.public-base-url} muss aus dem Internet erreichbar sein.
 */
@Component
public class InstagramClient implements NetworkClient {

    private final MetaGraph graph;
    private final SocialProperties properties;

    public InstagramClient(MetaGraph graph, SocialProperties properties) {
        this.graph = graph;
        this.properties = properties;
    }

    @Override
    public Network network() {
        return Network.INSTAGRAM;
    }

    @Override
    public PublishResult publish(ConnectedAccount account, PublishRequest request) {
        if (request.media() == null) {
            throw new SocialApiException(Network.INSTAGRAM, "Instagram braucht ein Bild");
        }
        String igId = account.getExternalId();
        String token = account.requireAccessToken();
        try {
            MultiValueMap<String, String> container = new LinkedMultiValueMap<>();
            container.add("image_url", properties.url(MediaController.PATH + request.media().getId()));
            container.add("caption", request.textWithLink());
            container.add("access_token", token);
            JsonNode created = graph.http().post()
                    .uri("/{ig}/media", igId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(container)
                    .retrieve()
                    .body(JsonNode.class);

            String containerId = Json.require(created, "id");
            awaitContainer(containerId, token);

            MultiValueMap<String, String> publish = new LinkedMultiValueMap<>();
            publish.add("creation_id", containerId);
            publish.add("access_token", token);
            JsonNode published = graph.http().post()
                    .uri("/{ig}/media_publish", igId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(publish)
                    .retrieve()
                    .body(JsonNode.class);
            String mediaId = Json.require(published, "id");

            JsonNode details = graph.http().get()
                    .uri(b -> b.path("/{id}").queryParam("fields", "permalink").queryParam("access_token", token)
                            .build(mediaId))
                    .retrieve()
                    .body(JsonNode.class);
            return new PublishResult(mediaId, Json.text(details, "permalink"));
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.INSTAGRAM, e);
        }
    }

    /** Instagram verarbeitet das Bild asynchron; warten bis der Container fertig ist (max. ~30 s). */
    private void awaitContainer(String containerId, String token) {
        for (int attempt = 0; attempt < 15; attempt++) {
            JsonNode status = graph.http().get()
                    .uri(b -> b.path("/{id}").queryParam("fields", "status_code").queryParam("access_token", token)
                            .build(containerId))
                    .retrieve()
                    .body(JsonNode.class);
            String code = Json.text(status, "status_code");
            if ("FINISHED".equals(code)) {
                return;
            }
            if ("ERROR".equals(code) || "EXPIRED".equals(code)) {
                throw new SocialApiException(Network.INSTAGRAM, "Bild wurde nicht angenommen (" + code + ")");
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SocialApiException(Network.INSTAGRAM, "Abgebrochen", e);
            }
        }
        throw new SocialApiException(Network.INSTAGRAM, "Zeitueberschreitung beim Verarbeiten des Bildes");
    }

    @Override
    public List<FeedItem> feed(ConnectedAccount account, int limit) {
        try {
            JsonNode response = graph.http().get()
                    .uri(b -> b.path("/{ig}/media")
                            .queryParam("fields", "caption,media_type,media_url,thumbnail_url,permalink,timestamp,username")
                            .queryParam("limit", limit)
                            .queryParam("access_token", account.requireAccessToken())
                            .build(account.getExternalId()))
                    .retrieve()
                    .body(JsonNode.class);
            List<FeedItem> items = new ArrayList<>();
            for (JsonNode media : response.path("data")) {
                String image = "VIDEO".equals(Json.text(media, "media_type"))
                        ? Json.text(media, "thumbnail_url")
                        : Json.text(media, "media_url");
                items.add(new FeedItem(Network.INSTAGRAM, account.label(),
                        "@" + Objects.requireNonNullElse(Json.text(media, "username"), account.getDisplayName()),
                        Objects.requireNonNullElse(Json.text(media, "caption"), ""),
                        image,
                        Json.text(media, "permalink"),
                        Json.time(Json.text(media, "timestamp"))));
            }
            return items;
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(Network.INSTAGRAM, e);
        }
    }
}
