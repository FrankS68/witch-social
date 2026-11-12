package de.witchcafe.app.social.client;

import java.util.List;

import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.Network;

/**
 * Veröffentlichen und Feed-Lesen für ein Netzwerk. Eine Implementierung pro
 * {@link Network}; sie werden über {@link NetworkClients} gefunden.
 */
public interface NetworkClient {

    Network network();

    PublishResult publish(ConnectedAccount account, PublishRequest request);

    List<FeedItem> feed(ConnectedAccount account, int limit);
}
