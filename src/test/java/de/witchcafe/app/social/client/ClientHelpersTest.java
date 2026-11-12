package de.witchcafe.app.social.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ClientHelpersTest {

    @Test
    @SuppressWarnings("unchecked")
    void blueskyFacetsUseUtf8ByteOffsets() {
        String text = "Schöne Grüße 🌻 https://witchcafe.de/garten und mehr";
        List<Map<String, Object>> facets = BlueskyClient.linkFacets(text);

        assertThat(facets).hasSize(1);
        var index = (Map<String, Integer>) facets.get(0).get("index");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String linked = new String(bytes, index.get("byteStart"), index.get("byteEnd") - index.get("byteStart"),
                StandardCharsets.UTF_8);
        assertThat(linked).isEqualTo("https://witchcafe.de/garten");
    }

    @Test
    void blueskyFacetsIgnoreTrailingPunctuation() {
        var facets = BlueskyClient.linkFacets("Siehe https://example.org/a.");
        var feature = ((List<Map<String, Object>>) facets.get(0).get("features")).get(0);
        assertThat(feature.get("uri")).isEqualTo("https://example.org/a");
    }

    @Test
    void blueskyWebUrlFromAtUri() {
        assertThat(BlueskyClient.webUrl("at://did:plc:abc123/app.bsky.feed.post/3kxyz"))
                .isEqualTo("https://bsky.app/profile/did:plc:abc123/post/3kxyz");
    }

    @Test
    void parsesTimeFormatsOfAllNetworks() {
        Instant expected = Instant.parse("2026-11-12T10:15:30Z");
        assertThat(Json.time("2026-11-12T10:15:30.000Z")).isEqualTo(expected); // Bluesky, Mastodon
        assertThat(Json.time("2026-11-12T10:15:30+0000")).isEqualTo(expected); // Facebook, Instagram
        assertThat(Json.time("2026-11-12T10:15:30")).isEqualTo(expected); // Pinterest
        assertThat(Json.time(null)).isEqualTo(Instant.EPOCH);
        assertThat(Json.time("kaputt")).isEqualTo(Instant.EPOCH);
    }

    @Test
    void mastodonHtmlBecomesPlainText() {
        assertThat(Json.plain("<p>Hallo <a href=\"x\">@welt</a></p><p>Zeile 2<br>Zeile 3</p>"))
                .isEqualTo("Hallo @welt\n\nZeile 2\nZeile 3");
    }

    @Test
    void normalizesMastodonInstances() {
        assertThat(MastodonClient.normalizeInstance("mastodon.social")).isEqualTo("https://mastodon.social");
        assertThat(MastodonClient.normalizeInstance("@frank@chaos.social")).isEqualTo("https://chaos.social");
        assertThat(MastodonClient.normalizeInstance(" https://norden.social/ ")).isEqualTo("https://norden.social");
    }

    @Test
    void appendsLinkOnlyWhenMissing() {
        assertThat(new PublishRequest("Text", "https://a.de", null).textWithLink()).isEqualTo("Text\n\nhttps://a.de");
        assertThat(new PublishRequest("Text https://a.de", "https://a.de", null).textWithLink())
                .isEqualTo("Text https://a.de");
        assertThat(new PublishRequest("Text", " ", null).textWithLink()).isEqualTo("Text");
    }
}
