package de.witchcafe.social.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import de.witchcafe.social.SocialProperties;

class EncryptedStringConverterTest {

    private static SocialProperties withKey(String key) {
        return new SocialProperties("http://localhost:8080", key);
    }

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecrypts() {
        var converter = new EncryptedStringConverter(withKey(KEY));
        String stored = converter.convertToDatabaseColumn("geheimes-token");

        assertThat(stored).startsWith("enc:").doesNotContain("geheimes-token");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("geheimes-token");
        // zufälliger IV: zweimal verschlüsseln ergibt unterschiedliche Werte
        assertThat(converter.convertToDatabaseColumn("geheimes-token")).isNotEqualTo(stored);
    }

    @Test
    void storesPlainTextWithoutKey() {
        var converter = new EncryptedStringConverter(withKey(""));
        assertThat(converter.convertToDatabaseColumn("token")).isEqualTo("plain:token");
        assertThat(converter.convertToEntityAttribute("plain:token")).isEqualTo("token");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void readsPlainValuesAfterKeyWasAdded() {
        var converter = new EncryptedStringConverter(withKey(KEY));
        assertThat(converter.convertToEntityAttribute("plain:alt")).isEqualTo("alt");
    }

    @Test
    void rejectsWrongKeyLength() {
        assertThatThrownBy(() -> new EncryptedStringConverter(withKey(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
    }
}
