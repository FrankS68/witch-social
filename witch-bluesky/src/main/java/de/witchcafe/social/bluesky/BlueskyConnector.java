package de.witchcafe.social.bluesky;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import de.witchcafe.social.AccountService;
import de.witchcafe.social.client.Urls;
import de.witchcafe.social.domain.ConnectedAccount;
import de.witchcafe.social.ui.AccountConnector;
import de.witchcafe.social.ui.Ui;

/**
 * "Bluesky verbinden" mit Handle und App-Passwort (ein normales Passwort lehnt Bluesky ab).
 */
@Component
public class BlueskyConnector implements AccountConnector {

    private final BlueskyClient bluesky;
    private final BlueskyProperties properties;
    private final AccountService accountService;

    public BlueskyConnector(BlueskyClient bluesky, BlueskyProperties properties, AccountService accountService) {
        this.bluesky = bluesky;
        this.properties = properties;
        this.accountService = accountService;
    }

    @Override
    public Set<String> networks() {
        return Set.of(BlueskyClient.ID);
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public com.vaadin.flow.component.Component connectButton(Runnable refresh) {
        return Ui.connectButton(BlueskyClient.NAME, bluesky.color(), () -> openDialog(refresh));
    }

    /** Meldet sich an, liest das Profil und speichert das Konto des angemeldeten Benutzers. */
    public ConnectedAccount connect(String identifier, String appPassword, String pdsInput) {
        String pds = pdsInput.isBlank() ? properties.defaultPds() : Urls.normalizeInstance(pdsInput);
        String handle = identifier.trim().replaceFirst("^@", "");
        String password = appPassword.trim();
        BlueskyClient.Login login = bluesky.login(pds, handle, password);
        return accountService.upsertMine(BlueskyClient.ID, login.did(), account -> {
            account.setDisplayName(login.displayName() + " (@" + login.handle() + ")");
            account.setHandle(login.handle());
            account.setInstanceUrl(pds);
            account.setAccessToken(password);
        });
    }

    private void openDialog(Runnable refresh) {
        var dialog = new Dialog("Bluesky verbinden");
        var handle = new TextField("Handle");
        handle.setPlaceholder("name.bsky.social");
        handle.setWidthFull();
        var password = new PasswordField("App-Passwort");
        password.setWidthFull();
        password.setHelperText("Nicht dein normales Passwort! Ein App-Passwort legst du in Bluesky unter "
                + "Einstellungen → Datenschutz und Sicherheit → App-Passwörter an.");
        var pds = new TextField("PDS (optional)");
        pds.setPlaceholder("https://bsky.social");
        pds.setHelperText("Nur bei eigenem Server nötig");
        pds.setWidthFull();
        var help = new Anchor("https://bsky.app/settings/app-passwords", "App-Passwort anlegen", AnchorTarget.BLANK);

        var connect = new Button("Verbinden", e -> {
            try {
                ConnectedAccount account = connect(handle.getValue(), password.getValue(), pds.getValue());
                Ui.success(bluesky.label(account) + " verbunden");
                dialog.close();
                refresh.run();
            } catch (RuntimeException ex) {
                Ui.error(ex);
            }
        });
        connect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(new VerticalLayout(handle, password, help, pds));
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), connect);
        dialog.setWidth("460px");
        dialog.open();
        handle.focus();
    }
}
