package de.witchcafe.app.social.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

/**
 * Hochgeladenes Bild zu einem Beitrag. Wird unter /images/social/{id} öffentlich
 * ausgeliefert, weil Instagram Bilder nur per URL entgegennimmt.
 */
@Entity
public class MediaFile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String ownerId = "";

    @Column(nullable = false)
    private String fileName = "";

    @Column(nullable = false)
    private String contentType = "";

    @Lob
    @Column(nullable = false)
    private byte[] data = new byte[0];

    private Instant createdAt = Instant.now();

    protected MediaFile() {
    }

    public MediaFile(String ownerId, String fileName, String contentType, byte[] data) {
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.data = data;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof MediaFile other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
