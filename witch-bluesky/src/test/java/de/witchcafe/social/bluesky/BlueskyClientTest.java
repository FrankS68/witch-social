package de.witchcafe.social.bluesky;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BlueskyClientTest {

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
}
