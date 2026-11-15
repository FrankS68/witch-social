package de.witchcafe.social.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Ein mit einem Benutzer verknüpftes Konto in einem sozialen Netzwerk.
 *
 * <p>Die Felder sind bewusst allgemein gehalten, damit Netzwerk-Module ohne eigene Tabellen
 * auskommen. Bedeutung der Felder je Netzwerk:
 * <ul>
 * <li>Facebook: externalId = Seiten-ID, accessToken = Seiten-Token</li>
 * <li>Instagram: externalId = Instagram-Business-ID, accessToken = Token der verknüpften Facebook-Seite</li>
 * <li>Pinterest: accessToken/refreshToken aus OAuth, boardId = Ziel-Board für neue Pins</li>
 * <li>Bluesky: externalId = DID, handle = Anmeldename, accessToken = App-Passwort, instanceUrl = PDS</li>
 * <li>Mastodon: instanceUrl = Instanz, accessToken = OAuth-Token</li>
 * </ul>
 */
@Entity
@Table(name = "connected_account",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "network", "external_id"}))
public class ConnectedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId = "";

    /** {@link de.witchcafe.social.client.SocialNetwork#id()}, z.B. "BLUESKY". */
    @Column(nullable = false, length = 20)
    private String network = "";

    @Column(name = "external_id", nullable = false)
    private String externalId = "";

    @Column(nullable = false)
    private String displayName = "";

    private @Nullable String handle;

    private @Nullable String instanceUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 4000)
    private @Nullable String accessToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 4000)
    private @Nullable String refreshToken;

    private @Nullable Instant tokenExpiresAt;

    private @Nullable String boardId;

    private @Nullable String boardName;

    private Instant connectedAt = Instant.now();

    protected ConnectedAccount() {
    }

    public ConnectedAccount(String ownerId, String network, String externalId) {
        this.ownerId = ownerId;
        this.network = network;
        this.externalId = externalId;
    }

    public @Nullable Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    /** ID des Netzwerks, siehe {@link de.witchcafe.social.client.SocialNetworks}. */
    public String getNetwork() {
        return network;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public @Nullable String getHandle() {
        return handle;
    }

    public void setHandle(@Nullable String handle) {
        this.handle = handle;
    }

    public @Nullable String getInstanceUrl() {
        return instanceUrl;
    }

    public void setInstanceUrl(@Nullable String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    public @Nullable String getAccessToken() {
        return accessToken;
    }

    public String requireAccessToken() {
        if (accessToken == null) {
            throw new IllegalStateException("Kein Zugangstoken fuer Konto " + displayName + " (" + network + ")");
        }
        return accessToken;
    }

    public void setAccessToken(@Nullable String accessToken) {
        this.accessToken = accessToken;
    }

    public @Nullable String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(@Nullable String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public @Nullable Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(@Nullable Instant tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public @Nullable String getBoardId() {
        return boardId;
    }

    public @Nullable String getBoardName() {
        return boardName;
    }

    public void setBoard(@Nullable String boardId, @Nullable String boardName) {
        this.boardId = boardId;
        this.boardName = boardName;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof ConnectedAccount other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
