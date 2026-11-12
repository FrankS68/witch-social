package de.witchcafe.app.social;

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

import de.witchcafe.app.social.client.BlueskyClient;
import de.witchcafe.app.social.client.NetworkClients;
import de.witchcafe.app.social.client.PublishRequest;
import de.witchcafe.app.social.client.PublishResult;
import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.MediaFile;
import de.witchcafe.app.social.domain.MediaFileRepository;
import de.witchcafe.app.social.domain.Network;
import de.witchcafe.app.social.domain.PostStatus;
import de.witchcafe.app.social.domain.PostTarget;
import de.witchcafe.app.social.domain.SocialPost;
import de.witchcafe.app.social.domain.SocialPostRepository;
import de.witchcafe.app.social.domain.TargetStatus;

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
    private final NetworkClients clients;
    private final AccountService accountService;

    public PostService(SocialPostRepository posts, MediaFileRepository media, NetworkClients clients,
            AccountService accountService) {
        this.posts = posts;
        this.media = media;
        this.clients = clients;
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
        String withLink = new PublishRequest(text, link, null).textWithLink();
        targets.stream().map(ConnectedAccount::getNetwork).distinct().forEach(network -> {
            int length = network == Network.PINTEREST ? text.length() : withLink.codePointCount(0, withLink.length());
            if (length > network.maxLength()) {
                problems.add(network.displayName() + ": Text ist " + length + " Zeichen lang, erlaubt sind "
                        + network.maxLength() + ".");
            }
            if (network.imageRequired() && image == null) {
                problems.add(network.displayName() + " braucht ein Bild.");
            }
            if (network == Network.INSTAGRAM && image != null && !"image/jpeg".equals(image.contentType())) {
                problems.add("Instagram akzeptiert nur JPEG-Bilder.");
            }
            if (network == Network.BLUESKY && image != null && image.data().length > BlueskyClient.MAX_IMAGE_BYTES) {
                problems.add("Bluesky erlaubt Bilder nur bis 976 KB.");
            }
        });
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
            post.addTarget(account);
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
                PublishResult result = clients.get(account.getNetwork()).publish(account, request);
                target.markPublished(result.externalId(), result.url());
            } catch (RuntimeException e) {
                log.warn("Veroeffentlichen auf {} fehlgeschlagen: {}", account.label(), e.getMessage());
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
