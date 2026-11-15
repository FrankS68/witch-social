package de.witchcafe.social;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.witchcafe.auth.CurrentUser;
import de.witchcafe.social.client.SocialNetworks;
import de.witchcafe.social.domain.ConnectedAccount;
import de.witchcafe.social.domain.ConnectedAccountRepository;
import de.witchcafe.social.domain.SocialPostRepository;

/**
 * Verwaltung der verbundenen Konten des angemeldeten Benutzers. Netzwerk-spezifisches
 * (OAuth, App-Passwort, Pinterest-Board) machen die Netzwerk-Module und speichern über
 * {@link #upsert} bzw. {@link #update}.
 */
@Service
public class AccountService {

    private final ConnectedAccountRepository accounts;
    private final SocialPostRepository posts;
    private final CurrentUser currentUser;
    private final SocialNetworks networks;

    public AccountService(ConnectedAccountRepository accounts, SocialPostRepository posts, CurrentUser currentUser,
            SocialNetworks networks) {
        this.accounts = accounts;
        this.posts = posts;
        this.currentUser = currentUser;
        this.networks = networks;
    }

    /** ID des angemeldeten Benutzers, wie sie als Besitzer gespeichert wird. */
    public String currentOwnerId() {
        return currentUser.require().getUserId().toString();
    }

    public List<ConnectedAccount> listMine() {
        return accounts.findByOwnerIdOrderByNetworkAscDisplayNameAsc(currentOwnerId());
    }

    /** Konten des angemeldeten Benutzers, deren Netzwerk-Modul eingebunden ist. */
    public List<ConnectedAccount> listMineUsable() {
        return listMine().stream().filter(account -> networks.find(account.getNetwork()).isPresent()).toList();
    }

    /** Legt ein Konto an oder aktualisiert es, falls es bereits verbunden war. */
    @Transactional
    public ConnectedAccount upsert(String ownerId, String network, String externalId,
            Consumer<ConnectedAccount> update) {
        ConnectedAccount account = accounts.findByOwnerIdAndNetworkAndExternalId(ownerId, network, externalId)
                .orElseGet(() -> new ConnectedAccount(ownerId, network, externalId));
        update.accept(account);
        account.setConnectedAt(Instant.now());
        return accounts.save(account);
    }

    /** Konto des angemeldeten Benutzers anlegen oder aktualisieren. */
    public ConnectedAccount upsertMine(String network, String externalId, Consumer<ConnectedAccount> update) {
        return upsert(currentOwnerId(), network, externalId, update);
    }

    /** Ändert ein eigenes Konto (z.B. Pinterest-Board), ohne es neu zu verbinden. */
    @Transactional
    public ConnectedAccount update(ConnectedAccount account, Consumer<ConnectedAccount> update) {
        ConnectedAccount own = requireOwn(account);
        update.accept(own);
        return accounts.save(own);
    }

    @Transactional
    public void disconnect(ConnectedAccount account) {
        ConnectedAccount own = requireOwn(account);
        posts.detachAccount(own);
        accounts.delete(own);
    }

    /** Lädt das Konto frisch und prüft, dass es dem angemeldeten Benutzer gehört. */
    public ConnectedAccount requireOwn(ConnectedAccount account) {
        Long id = account.getId();
        ConnectedAccount stored = id == null ? null : accounts.findById(id).orElse(null);
        if (stored == null || !stored.getOwnerId().equals(currentOwnerId())) {
            throw new IllegalArgumentException("Konto nicht gefunden");
        }
        return stored;
    }
}
