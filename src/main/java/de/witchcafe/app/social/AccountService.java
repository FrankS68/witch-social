package de.witchcafe.app.social;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.witchcafe.app.social.client.BlueskyClient;
import de.witchcafe.app.social.client.MastodonClient;
import de.witchcafe.app.social.client.MetaGraph;
import de.witchcafe.app.social.client.PinterestClient;
import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.ConnectedAccountRepository;
import de.witchcafe.app.social.domain.Network;
import de.witchcafe.app.social.domain.SocialPostRepository;
import de.witchcafe.auth.CurrentUser;

/**
 * Verwaltung der verbundenen Konten des angemeldeten Benutzers.
 */
@Service
public class AccountService {

    private final ConnectedAccountRepository accounts;
    private final SocialPostRepository posts;
    private final CurrentUser currentUser;
    private final SocialProperties properties;
    private final BlueskyClient bluesky;
    private final PinterestClient pinterest;
    private final MetaGraph meta;

    public AccountService(ConnectedAccountRepository accounts, SocialPostRepository posts, CurrentUser currentUser,
            SocialProperties properties, BlueskyClient bluesky, PinterestClient pinterest, MetaGraph meta) {
        this.accounts = accounts;
        this.posts = posts;
        this.currentUser = currentUser;
        this.properties = properties;
        this.bluesky = bluesky;
        this.pinterest = pinterest;
        this.meta = meta;
    }

    /** ID des angemeldeten Benutzers, wie sie als Besitzer gespeichert wird. */
    public String currentOwnerId() {
        return currentUser.require().getUserId().toString();
    }

    public List<ConnectedAccount> listMine() {
        return accounts.findByOwnerIdOrderByNetworkAscDisplayNameAsc(currentOwnerId());
    }

    public boolean metaConfigured() {
        return meta.configured();
    }

    public boolean pinterestConfigured() {
        return pinterest.configured();
    }

    /** Legt ein Konto an oder aktualisiert es, falls es bereits verbunden war. */
    @Transactional
    public ConnectedAccount upsert(String ownerId, Network network, String externalId,
            Consumer<ConnectedAccount> update) {
        ConnectedAccount account = accounts.findByOwnerIdAndNetworkAndExternalId(ownerId, network, externalId)
                .orElseGet(() -> new ConnectedAccount(ownerId, network, externalId));
        update.accept(account);
        account.setConnectedAt(Instant.now());
        return accounts.save(account);
    }

    /** Bluesky mit Handle und App-Passwort verbinden (ein normales Passwort wird abgelehnt). */
    public ConnectedAccount connectBluesky(String identifier, String appPassword, String pdsInput) {
        String pds = pdsInput.isBlank() ? properties.bluesky().defaultPds() : MastodonClient.normalizeInstance(pdsInput);
        String handle = identifier.trim().replaceFirst("^@", "");
        BlueskyClient.Login login = bluesky.login(pds, handle, appPassword.trim());
        return upsert(currentOwnerId(), Network.BLUESKY, login.did(), account -> {
            account.setDisplayName(login.displayName() + " (@" + login.handle() + ")");
            account.setHandle(login.handle());
            account.setInstanceUrl(pds);
            account.setAccessToken(appPassword.trim());
        });
    }

    @Transactional
    public void disconnect(ConnectedAccount account) {
        ConnectedAccount own = requireOwn(account);
        posts.detachAccount(own);
        accounts.delete(own);
    }

    public List<PinterestClient.Board> pinterestBoards(ConnectedAccount account) {
        return pinterest.boards(requireOwn(account));
    }

    @Transactional
    public void setPinterestBoard(ConnectedAccount account, PinterestClient.Board board) {
        ConnectedAccount own = requireOwn(account);
        own.setBoard(board.id(), board.name());
        accounts.save(own);
    }

    private ConnectedAccount requireOwn(ConnectedAccount account) {
        Long id = account.getId();
        ConnectedAccount stored = id == null ? null : accounts.findById(id).orElse(null);
        if (stored == null || !stored.getOwnerId().equals(currentOwnerId())) {
            throw new IllegalArgumentException("Konto nicht gefunden");
        }
        return stored;
    }
}
