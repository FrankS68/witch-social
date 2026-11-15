package de.witchcafe.social.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Veröffentlichung eines {@link SocialPost} auf genau einem Konto.
 */
@Entity
public class PostTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id")
    private @Nullable SocialPost post;

    /** Null, wenn das Konto inzwischen getrennt wurde. */
    @ManyToOne
    @JoinColumn(name = "account_id")
    private @Nullable ConnectedAccount account;

    /** Für die Anzeige, falls das Konto später getrennt wird. */
    @Column(nullable = false)
    private String accountLabel = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetStatus status = TargetStatus.PENDING;

    private @Nullable String externalId;

    @Column(length = 2000)
    private @Nullable String url;

    @Column(length = 2000)
    private @Nullable String error;

    private @Nullable Instant publishedAt;

    protected PostTarget() {
    }

    PostTarget(SocialPost post, ConnectedAccount account, String accountLabel) {
        this.post = post;
        this.account = account;
        this.accountLabel = accountLabel;
    }

    public void markPublished(String externalId, @Nullable String url) {
        this.status = TargetStatus.PUBLISHED;
        this.externalId = externalId;
        this.url = url;
        this.error = null;
        this.publishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = TargetStatus.FAILED;
        this.error = error.length() > 2000 ? error.substring(0, 2000) : error;
    }

    public void detachAccount() {
        this.account = null;
    }

    public @Nullable Long getId() {
        return id;
    }

    public @Nullable ConnectedAccount getAccount() {
        return account;
    }

    public String getAccountLabel() {
        return accountLabel;
    }

    public TargetStatus getStatus() {
        return status;
    }

    public @Nullable String getExternalId() {
        return externalId;
    }

    public @Nullable String getUrl() {
        return url;
    }

    public @Nullable String getError() {
        return error;
    }

    public @Nullable Instant getPublishedAt() {
        return publishedAt;
    }
}
