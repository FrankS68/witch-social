package de.witchcafe.app.social.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.witchcafe.app.social.domain.Network;

@Component
public class NetworkClients {

    private final Map<Network, NetworkClient> clients = new EnumMap<>(Network.class);

    public NetworkClients(List<NetworkClient> clients) {
        clients.forEach(client -> this.clients.put(client.network(), client));
    }

    public NetworkClient get(Network network) {
        NetworkClient client = clients.get(network);
        if (client == null) {
            throw new IllegalStateException("Kein Client fuer " + network);
        }
        return client;
    }
}
