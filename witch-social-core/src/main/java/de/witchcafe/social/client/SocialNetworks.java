package de.witchcafe.social.client;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.witchcafe.social.domain.ConnectedAccount;

/**
 * Alle {@link SocialNetwork}-Beans, die über die eingebundenen Netzwerk-Module vorhanden sind.
 *
 * <p>Konten eines Netzwerks, dessen Modul nicht (mehr) eingebunden ist, bleiben in der Datenbank;
 * sie werden angezeigt, aber Veröffentlichen und Feed schlagen mit einer Meldung fehl.
 */
@Component
public class SocialNetworks {

    private final Map<String, SocialNetwork> networks = new LinkedHashMap<>();

    public SocialNetworks(List<SocialNetwork> networks) {
        networks.forEach(network -> {
            SocialNetwork previous = this.networks.put(network.id(), network);
            if (previous != null) {
                throw new IllegalStateException("Netzwerk-ID " + network.id() + " doppelt: "
                        + previous.getClass().getName() + " und " + network.getClass().getName());
            }
        });
    }

    public Collection<SocialNetwork> all() {
        return Collections.unmodifiableCollection(networks.values());
    }

    public Optional<SocialNetwork> find(String id) {
        return Optional.ofNullable(networks.get(id));
    }

    public SocialNetwork get(String id) {
        return find(id).orElseThrow(() -> new IllegalStateException(
                "Netzwerk " + id + " ist in dieser App nicht eingebunden (Maven-Modul fehlt)"));
    }

    public SocialNetwork get(ConnectedAccount account) {
        return get(account.getNetwork());
    }

    /** Anzeigename, auch für Netzwerke ohne eingebundenes Modul. */
    public String displayName(String id) {
        return find(id).map(SocialNetwork::displayName).orElse(id);
    }

    /** Anzeige wie "Mastodon · @frank@mastodon.social", auch ohne eingebundenes Modul. */
    public String label(ConnectedAccount account) {
        return find(account.getNetwork()).map(network -> network.label(account))
                .orElse(account.getNetwork() + " · " + account.getDisplayName());
    }
}
