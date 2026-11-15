package de.witchcafe.social.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ClientHelpersTest {

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
    void normalizesInstances() {
        assertThat(Urls.normalizeInstance("mastodon.social")).isEqualTo("https://mastodon.social");
        assertThat(Urls.normalizeInstance("@frank@chaos.social")).isEqualTo("https://chaos.social");
        assertThat(Urls.normalizeInstance(" https://norden.social/ ")).isEqualTo("https://norden.social");
    }

    @Test
    void appendsLinkOnlyWhenMissing() {
        assertThat(new PublishRequest("Text", "https://a.de", null).textWithLink()).isEqualTo("Text\n\nhttps://a.de");
        assertThat(new PublishRequest("Text https://a.de", "https://a.de", null).textWithLink())
                .isEqualTo("Text https://a.de");
        assertThat(new PublishRequest("Text", " ", null).textWithLink()).isEqualTo("Text");
    }
}
