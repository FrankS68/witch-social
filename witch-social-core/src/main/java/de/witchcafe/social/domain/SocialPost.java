package de.witchcafe.social.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

/**
 * Ein Beitrag, der an ein oder mehrere verbundene Konten geht.
 * Pro Konto gibt es ein {@link PostTarget} mit eigenem Status.
 */
@Entity
public class SocialPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(nullable = false)
    private String ownerId = "";

    @Column(nullable = false, length = 5000)
    private String text = "";

    @Column(length = 2000)
    private @Nullable String link;

    /** Bild aus {@link MediaFile}; nur die ID, damit die Bilddaten nicht mitgeladen werden. */
    private @Nullable UUID mediaId;

    /** Zeitpunkt der geplanten Veröffentlichung; null = sofort. */
    private @Nullable Instant scheduledAt;

    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostStatus status = PostStatus.SCHEDULED;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<PostTarget> targets = new ArrayList<>();

    protected SocialPost() {
    }

    public SocialPost(String ownerId, String text, @Nullable String link, @Nullable UUID mediaId,
            @Nullable Instant scheduledAt) {
        this.ownerId = ownerId;
        this.text = text;
        this.link = link;
        this.mediaId = mediaId;
        this.scheduledAt = scheduledAt;
    }

    public void addTarget(ConnectedAccount account, String accountLabel) {
        targets.add(new PostTarget(this, account, accountLabel));
    }

    /** Leitet den Gesamtstatus aus den Status der einzelnen Ziele ab. */
    public void updateStatusFromTargets() {
        long published = targets.stream().filter(t -> t.getStatus() == TargetStatus.PUBLISHED).count();
        long failed = targets.stream().filter(t -> t.getStatus() == TargetStatus.FAILED).count();
        if (published == targets.size()) {
            status = PostStatus.PUBLISHED;
        } else if (failed == targets.size()) {
            status = PostStatus.FAILED;
        } else if (published > 0 && published + failed == targets.size()) {
            status = PostStatus.PARTIALLY_PUBLISHED;
        }
    }

    public @Nullable Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getText() {
        return text;
    }

    public @Nullable String getLink() {
        return link;
    }

    public @Nullable UUID getMediaId() {
        return mediaId;
    }

    public @Nullable Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(@Nullable Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }

    public List<PostTarget> getTargets() {
        return targets;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof SocialPost other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
