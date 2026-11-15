package de.witchcafe.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.witchcafe.social.client.PublishRequest;
import de.witchcafe.social.client.PublishResult;
import de.witchcafe.social.client.SocialNetworks;
import de.witchcafe.social.domain.ConnectedAccount;
import de.witchcafe.social.domain.MediaFile;
import de.witchcafe.social.domain.MediaFileRepository;
import de.witchcafe.social.domain.PostStatus;
import de.witchcafe.social.domain.PostTarget;
import de.witchcafe.social.domain.SocialPost;
import de.witchcafe.social.domain.SocialPostRepository;
import de.witchcafe.social.domain.TargetStatus;

/**
 * Anlegen, Veröffentlichen und Planen von Beiträgen.
 *
 * <p>Geplante Beiträge veröffentlicht {@link #publishDue()} alle 30 Sekunden.
 * Netzwerkaufrufe laufen bewusst außerhalb einer Datenbank-Transaktion.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final SocialPostRepository posts;
    private final MediaFileRepository media;
    private final SocialNetworks networks;
    private final AccountService accountService;

    public PostService(SocialPostRepository posts, MediaFileRepository media, SocialNetworks networks,
            AccountService accountService) {
        this.posts = posts;
        this.media = media;
        this.networks = networks;
        this.accountService = accountService;
    }

    public List<SocialPost> listMine() {
        return posts.findByOwnerIdOrderByCreatedAtDesc(accountService.currentOwnerId());
    }

    /** Prüft die Einschränkungen der gewählten Netzwerke; leere Liste = alles in Ordnung. */
    public List<String> validate(String text, @Nullable String link, @Nullable ImageUpload image,
            Collection<ConnectedAccount> targets) {
        List<String> problems = new ArrayList<>();
        if (targets.isEmpty()) {
            problems.add("Bitte mindestens ein Konto auswählen.");
        }
        if (text.isBlank() && image == null) {
            problems.add("Text oder Bild fehlt.");
        }
        var request = new PublishRequest(text, link, null);
        targets.stream().map(ConnectedAccount::getNetwork).distinct().forEach(id -> networks.find(id).ifPresentOrElse(
                network -> problems.addAll(network.validate(request, image)),
                () -> problems.add(id + " ist in dieser App nicht eingebunden.")));
        return problems;
    }

    /**
     * Legt einen Beitrag an. Ohne Zeitpunkt wird er sofort veröffentlicht,
     * sonst vom Scheduler zum gewählten Zeitpunkt.
     */
    public SocialPost create(String text, @Nullable String link, @Nullable ImageUpload image,
            Collection<ConnectedAccount> targets, @Nullable Instant scheduledAt) {
        List<String> problems = validate(text, link, image, targets);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        String owner = accountService.currentOwnerId();
        MediaFile stored = image == null ? null
                : media.save(new MediaFile(owner, image.fileName(), image.contentType(), image.data()));
        var post = new SocialPost(owner, text.strip(), link == null || link.isBlank() ? null : link.strip(),
                stored == null ? null : stored.getId(), scheduledAt);
        targets.forEach(account -> {
            if (!account.getOwnerId().equals(owner)) {
                throw new IllegalArgumentException("Fremdes Konto");
            }
            post.addTarget(account, networks.label(account));
        });
        SocialPost saved = posts.save(post);
        return scheduledAt == null ? publish(Objects.requireNonNull(saved.getId())) : saved;
    }

    /** Veröffentlicht fehlgeschlagene Ziele erneut. */
    public SocialPost retry(SocialPost post) {
        SocialPost own = requireOwn(post);
        own.setStatus(PostStatus.SCHEDULED);
        own.setScheduledAt(null);
        posts.save(own);
        return publish(Objects.requireNonNull(own.getId()));
    }

    public void cancel(SocialPost post) {
        SocialPost own = requireOwn(post);
        posts.compareAndSetStatus(Objects.requireNonNull(own.getId()), PostStatus.SCHEDULED, PostStatus.CANCELLED);
    }

    @Transactional
    public void delete(SocialPost post) {
        posts.delete(requireOwn(post));
    }

    @Scheduled(fixedDelayString = "${social.scheduler-delay:30s}")
    public void publishDue() {
        for (SocialPost due : posts.findByStatusAndScheduledAtLessThanEqual(PostStatus.SCHEDULED, Instant.now())) {
            try {
                publish(Objects.requireNonNull(due.getId()));
            } catch (RuntimeException e) {
                log.error("Geplanter Beitrag {} konnte nicht veroeffentlicht werden", due.getId(), e);
            }
        }
    }

    /** Veröffentlicht alle noch nicht erfolgreichen Ziele eines Beitrags. */
    SocialPost publish(Long postId) {
        if (!claim(postId)) {
            // läuft bereits (z.B. Scheduler und Benutzer gleichzeitig) oder wurde abgebrochen
            return posts.findById(postId).orElseThrow();
        }
        SocialPost post = posts.findById(postId).orElseThrow();
        MediaFile image = post.getMediaId() == null ? null : media.findById(post.getMediaId()).orElse(null);
        var request = new PublishRequest(post.getText(), post.getLink(), image);

        for (PostTarget target : post.getTargets()) {
            if (target.getStatus() == TargetStatus.PUBLISHED) {
                continue;
            }
            ConnectedAccount account = target.getAccount();
            if (account == null) {
                target.markFailed("Konto wurde getrennt");
                continue;
            }
            try {
                PublishResult result = networks.get(account).publish(account, request);
                target.markPublished(result.externalId(), result.url());
            } catch (RuntimeException e) {
                log.warn("Veroeffentlichen auf {} fehlgeschlagen: {}", networks.label(account), e.getMessage());
                target.markFailed(Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
            }
        }
        post.updateStatusFromTargets();
        return posts.save(post);
    }

    private boolean claim(Long postId) {
        return posts.compareAndSetStatus(postId, PostStatus.SCHEDULED, PostStatus.PUBLISHING) == 1;
    }

    private SocialPost requireOwn(SocialPost post) {
        Long id = post.getId();
        SocialPost stored = id == null ? null : posts.findById(id).orElse(null);
        if (stored == null || !stored.getOwnerId().equals(accountService.currentOwnerId())) {
            throw new IllegalArgumentException("Beitrag nicht gefunden");
        }
        return stored;
    }
}
