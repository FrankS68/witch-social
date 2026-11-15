package de.witchcafe.social.mastodon;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import de.witchcafe.social.ui.AccountConnector;
import de.witchcafe.social.ui.Ui;

/**
 * "Mastodon verbinden": fragt die Instanz ab und startet {@link MastodonConnectController}.
 */
@Component
public class MastodonConnector implements AccountConnector {

    private final MastodonClient mastodon;

    public MastodonConnector(MastodonClient mastodon) {
        this.mastodon = mastodon;
    }

    @Override
    public Set<String> networks() {
        return Set.of(MastodonClient.ID);
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public com.vaadin.flow.component.Component connectButton(Runnable refresh) {
        return Ui.connectButton(MastodonClient.NAME, mastodon.color(), this::openDialog);
    }

    private void openDialog() {
        var dialog = new Dialog("Mastodon verbinden");
        var instance = new TextField("Instanz");
        instance.setPlaceholder("mastodon.social");
        instance.setHelperText("Die Adresse deines Servers, z.B. chaos.social oder @name@instanz");
        instance.setWidthFull();
        var go = new Button("Weiter zur Anmeldung", e -> {
            if (instance.getValue().isBlank()) {
                instance.setInvalid(true);
                return;
            }
            UI.getCurrent().getPage().setLocation(
                    "/connect/mastodon?instance=" + URLEncoder.encode(instance.getValue().trim(), StandardCharsets.UTF_8));
        });
        go.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(new VerticalLayout(instance));
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), go);
        dialog.setWidth("420px");
        dialog.open();
        instance.focus();
    }
}
