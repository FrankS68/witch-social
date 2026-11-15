package de.witchcafe.social.meta;

import java.util.Set;

import org.springframework.stereotype.Component;

import de.witchcafe.social.domain.ConnectedAccount;
import de.witchcafe.social.ui.AccountConnector;
import de.witchcafe.social.ui.Ui;

/**
 * "Facebook & Instagram verbinden" in der Kontenübersicht, über {@link MetaConnectController}.
 */
@Component
public class MetaConnector implements AccountConnector {

    private final MetaGraph meta;

    public MetaConnector(MetaGraph meta) {
        this.meta = meta;
    }

    @Override
    public Set<String> networks() {
        return Set.of(FacebookClient.ID, InstagramClient.ID);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public com.vaadin.flow.component.Component connectButton(Runnable refresh) {
        return Ui.connectRedirectButton("Facebook & Instagram", "#1877F2", "/connect/meta", meta.configured(),
                "Meta-App fehlt: social.meta.app-id / app-secret setzen");
    }

    @Override
    public String details(ConnectedAccount account) {
        return FacebookClient.ID.equals(account.getNetwork()) ? "Facebook-Seite" : "Business-/Creator-Konto";
    }
}
