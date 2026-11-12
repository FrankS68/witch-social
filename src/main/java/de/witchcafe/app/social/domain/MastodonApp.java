package de.witchcafe.app.social.domain;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Bei einer Mastodon-Instanz automatisch registrierte App (POST /api/v1/apps).
 * Jede Instanz vergibt eigene Client-Zugangsdaten.
 */
@Entity
public class MastodonApp {

    /** Basis-URL der Instanz, z.B. https://mastodon.social */
    @Id
    private String instanceUrl = "";

    @Column(nullable = false)
    private String clientId = "";

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String clientSecret = "";

    @Column(nullable = false)
    private String redirectUri = "";

    protected MastodonApp() {
    }

    public MastodonApp(String instanceUrl, String clientId, String clientSecret, String redirectUri) {
        this.instanceUrl = instanceUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof MastodonApp other && instanceUrl.equals(other.instanceUrl);
    }

    @Override
    public int hashCode() {
        return instanceUrl.hashCode();
    }
}
