package de.witchcafe.social.bluesky;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import de.witchcafe.social.ImageUpload;
import de.witchcafe.social.client.FeedItem;
import de.witchcafe.social.client.Json;
import de.witchcafe.social.client.PublishRequest;
import de.witchcafe.social.client.PublishResult;
import de.witchcafe.social.client.SocialApiException;
import de.witchcafe.social.client.SocialNetwork;
import de.witchcafe.social.domain.ConnectedAccount;
import tools.jackson.databind.JsonNode;

/**
 * Bluesky über die AT-Protocol-XRPC-API, angemeldet mit Handle + App-Passwort
 * (Einstellungen → Datenschutz und Sicherheit → App-Passwörter).
 */
@Component
public class BlueskyClient implements SocialNetwork {

    public static final String ID = "BLUESKY";
    public static final String NAME = "Bluesky";

    /** Bluesky erlaubt Bilder bis knapp 1 MB. */
    public static final int MAX_IMAGE_BYTES = 976_560;

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+[^\\s<>\".,;:!?)]");
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(60);

    public record Login(String did, String handle, String displayName, String accessJwt) {
    }

    private record Session(String accessJwt, Instant validUntil) {
    }

    private final RestClient http;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public BlueskyClient(RestClient.Builder builder) {
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
        return "#1185FE";
    }

    @Override
    public int maxLength() {
        return 300;
    }

    @Override
    public List<String> validate(PublishRequest request, @Nullable ImageUpload image) {
        List<String> problems = new ArrayList<>(SocialNetwork.super.validate(request, image));
        if (image != null && image.data().length > MAX_IMAGE_BYTES) {
            problems.add("Bluesky erlaubt Bilder nur bis 976 KB.");
        }
        return problems;
    }

    /** Meldet sich an und liest das Profil; wird beim Verbinden des Kontos genutzt. */
    public Login login(String pds, String identifier, String appPassword) {
        try {
            JsonNode session = http.post()
                    .uri(pds + "/xrpc/com.atproto.server.createSession")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("identifier", identifier, "password", appPassword))
                    .retrieve()
                    .body(JsonNode.class);
            String did = Json.require(session, "did");
            String handle = Json.require(session, "handle");
            String jwt = Json.require(session, "accessJwt");
            sessions.put(did, new Session(jwt, Instant.now().plus(SESSION_LIFETIME)));

            JsonNode profile = http.get()
                    .uri(pds + "/xrpc/app.bsky.actor.getProfile?actor={did}", did)
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .body(JsonNode.class);
            String displayName = Json.text(profile, "displayName");
            return new Login(did, handle, displayName != null ? displayName : handle, jwt);
        } catch (RestClientResponseException e) {
            throw SocialApiException.of(NAME, e);
        }
    }

    private String accessJwt(ConnectedAccount account) {
        Session session = sessions.get(account.getExternalId());
        if (session != null && session.validUntil().isAfter(Instant.now())) {
            return session.accessJwt();
        }
        String identifier = account.getHandle() != null ? account.getHandle() : account.getExternalId();
        return login(pds(account), identifier, account.requireAccessToken()).accessJwt();
    }

    private static String pds(ConnectedAccount account) {
        return account.getInstanceUrl() != null ? account.getInstanceUrl() : "https://bsky.social";
    }

    @Override
    public PublishResult publish(ConnectedAccount account, PublishRequest request) {
        String jwt = accessJwt(account);
        String text = request.textWithLink();

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("$type", "app.bsky.feed.post");
        record.put("text", text);
        record.put("createdAt", Instant.now().toString());
        List<Map<String, Object>> facets = linkFacets(text);
        if (!facets.isEmpty()) {
            record.put("facets", facets);
        }

        try {
            if (request.media() != null) {
                if (request.media().getData().length > MAX_IMAGE_BYTES) {
                    throw new SocialApiException(NAME, "Bild ist groesser als 976 KB");
                }
                JsonNode blob = http.post()
                        .uri(pds(account) + "/xrpc/com.atproto.repo.uploadBlob")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.parseMediaType(request.media().getContentType()))
                        .body(request.media().getData())
                        .retrieve()
                        .body(JsonNode.class)
                        .path("blob");
                record.put("embed", Map.of(
                        "$type", "app.bsky.embed.images",
                        "images", List.of(Map.of("alt", "", "image", blob))));
            }

            JsonNode created = http.post()
                    .uri(pds(account) + "/xrpc/com.atproto.repo.createRecord")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("repo", account.getExternalId(), "collection", "app.bsky.feed.post", "record", record))
                    .retrieve()
                    .body(JsonNode.class);
            String uri = Json.require(created, "uri");
            return new PublishResult(uri, webUrl(uri));
        } catch (RestClientResponseException e) {
            sessions.remove(account.getExternalId());
            throw SocialApiException.of(NAME, e);
        }
    }

    /** Macht URLs im Text klickbar; Bluesky erwartet dafür Byte-Offsets in UTF-8. */
    static List<Map<String, Object>> linkFacets(String text) {
        List<Map<String, Object>> facets = new ArrayList<>();
        Matcher m = URL.matcher(text);
        while (m.find()) {
            int start = text.substring(0, m.start()).getBytes(StandardCharsets.UTF_8).length;
            int end = start + m.group().getBytes(StandardCharsets.UTF_8).length;
            facets.add(Map.of(
                    "index", Map.of("byteStart", start, "byteEnd", end),
                    "features", List.of(Map.of("$type", "app.bsky.richtext.facet#link", "uri", m.group()))));
        }
        return facets;
    }

    /** at://did/app.bsky.feed.post/rkey → https://bsky.app/profile/did/post/rkey */
    static @Nullable String webUrl(String atUri) {
        String[] parts = atUri.replace("at://", "").split("/");
        return parts.length == 3 ? "https://bsky.app/profile/" + parts[0] + "/post/" + parts[2] : null;
    }

    @Override
    public List<FeedItem> feed(ConnectedAccount account, int limit) {
        try {
            JsonNode response = http.get()
                    .uri(pds(account) + "/xrpc/app.bsky.feed.getTimeline?limit={limit}", limit)
                    .header("Authorization", "Bearer " + accessJwt(account))
                    .retrieve()
                    .body(JsonNode.class);
            List<FeedItem> items = new ArrayList<>();
            for (JsonNode entry : response.path("feed")) {
                JsonNode post = entry.path("post");
                JsonNode author = post.path("author");
                String name = Json.text(author, "displayName");
                String handle = Json.text(author, "handle");
                JsonNode images = post.path("embed").path("images");
                String image = images.isArray() && !images.isEmpty() ? Json.text(images.get(0), "thumb") : null;
                String uri = Json.text(post, "uri");
                items.add(new FeedItem(this, label(account),
                        (name != null ? name + " " : "") + "@" + handle,
                        Objects.requireNonNullElse(Json.text(post.path("record"), "text"), ""),
                        image,
                        uri != null ? webUrl(uri) : null,
                        Json.time(Json.text(post, "indexedAt"))));
            }
            return items;
        } catch (RestClientResponseException e) {
            sessions.remove(account.getExternalId());
            throw SocialApiException.of(NAME, e);
        }
    }
}
