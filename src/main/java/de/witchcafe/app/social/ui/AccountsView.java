package de.witchcafe.app.social.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.app.social.AccountService;
import de.witchcafe.app.social.client.PinterestClient;
import de.witchcafe.app.social.domain.ConnectedAccount;
import de.witchcafe.app.social.domain.Network;
import de.witchcafe.auth.CurrentUser;
import jakarta.annotation.security.PermitAll;

/**
 * Konten der fünf Netzwerke verbinden, Pinterest-Board wählen, Konten trennen.
 */
@Route("konten")
@PageTitle("Konten")
@Menu(order = 3, icon = "vaadin:connect", title = "Konten")
@PermitAll
public class AccountsView extends Main implements BeforeEnterObserver {

    private final AccountService accountService;
    private final ZoneId zone;
    private final Grid<ConnectedAccount> grid = new Grid<>();

    public AccountsView(AccountService accountService, CurrentUser currentUser) {
        this.accountService = accountService;
        this.zone = currentUser.require().getZoneId();
        addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN,
                LumoUtility.BoxSizing.BORDER);
        setSizeFull();

        var connect = new FlexLayout(
                redirectButton(Network.FACEBOOK, "Facebook & Instagram", "/connect/meta",
                        accountService.metaConfigured(), "Meta-App fehlt: social.meta.app-id / app-secret setzen"),
                redirectButton(Network.PINTEREST, "Pinterest", "/connect/pinterest",
                        accountService.pinterestConfigured(),
                        "Pinterest-App fehlt: social.pinterest.client-id / client-secret setzen"),
                dialogButton(Network.MASTODON, "Mastodon", this::openMastodonDialog),
                dialogButton(Network.BLUESKY, "Bluesky", this::openBlueskyDialog));
        connect.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        connect.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Margin.Bottom.MEDIUM);

        grid.addComponentColumn(account -> Ui.badge(account.getNetwork())).setHeader("Netzwerk").setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(ConnectedAccount::getDisplayName).setHeader("Konto").setFlexGrow(2);
        grid.addComponentColumn(this::detailsCell).setHeader("Details").setFlexGrow(2);
        grid.addColumn(account -> Ui.format(account.getConnectedAt(), zone)).setHeader("Verbunden")
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actionsCell).setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("Noch keine Konten verbunden.");
        grid.setSizeFull();

        add(Ui.title("Konten verbinden"), connect, grid);
        refresh();
    }

    /** Rückmeldung der OAuth-Callbacks (?ok=... bzw. ?error=...). */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters();
        params.getSingleParameter("ok").ifPresent(Ui::success);
        params.getSingleParameter("error").ifPresent(Ui::error);
        refresh();
    }

    private void refresh() {
        grid.setItems(accountService.listMine());
    }

    private Button redirectButton(Network network, String label, String path, boolean enabled, String hint) {
        var button = new Button(label + " verbinden", VaadinIcon.PLUS.create(),
                e -> UI.getCurrent().getPage().setLocation(path));
        style(button, network);
        button.setEnabled(enabled);
        if (!enabled) {
            button.setTooltipText(hint);
        }
        return button;
    }

    private Button dialogButton(Network network, String label, Runnable open) {
        var button = new Button(label + " verbinden", VaadinIcon.PLUS.create(), e -> open.run());
        style(button, network);
        return button;
    }

    private static void style(Button button, Network network) {
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle().set("background", network.color());
    }

    private Component detailsCell(ConnectedAccount account) {
        return switch (account.getNetwork()) {
            case PINTEREST -> new Span(account.getBoardName() != null
                    ? "Board: " + account.getBoardName()
                    : "Board: erstes Board (noch nicht gewählt)");
            case MASTODON, BLUESKY -> new Span(account.getInstanceUrl() != null ? account.getInstanceUrl() : "");
            case FACEBOOK -> new Span("Facebook-Seite");
            case INSTAGRAM -> new Span("Business-/Creator-Konto");
        };
    }

    private Component actionsCell(ConnectedAccount account) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        if (account.getNetwork() == Network.PINTEREST) {
            var board = new Button(VaadinIcon.PIN.create(), e -> openBoardDialog(account));
            board.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            board.setTooltipText("Board wählen");
            board.setAriaLabel("Board wählen");
            actions.add(board);
        }
        var remove = new Button(VaadinIcon.UNLINK.create(), e -> confirmDisconnect(account));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
        remove.setTooltipText("Trennen");
        remove.setAriaLabel("Trennen");
        actions.add(remove);
        return actions;
    }

    private void confirmDisconnect(ConnectedAccount account) {
        var dialog = new ConfirmDialog("Konto trennen?",
                account.label() + " wird aus der App entfernt. Geplante Beiträge an dieses Konto schlagen dann fehl. "
                        + "Die Berechtigung der App kannst du zusätzlich in den Einstellungen des Netzwerks entziehen.",
                "Trennen", e -> {
                    accountService.disconnect(account);
                    refresh();
                });
        dialog.setCancelable(true);
        dialog.setCancelText("Abbrechen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.open();
    }

    private void openMastodonDialog() {
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

    private void openBlueskyDialog() {
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
                ConnectedAccount account = accountService.connectBluesky(handle.getValue(), password.getValue(),
                        pds.getValue());
                Ui.success(account.label() + " verbunden");
                dialog.close();
                refresh();
            } catch (RuntimeException ex) {
                Ui.error(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
        });
        connect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(new VerticalLayout(handle, password, help, pds));
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), connect);
        dialog.setWidth("460px");
        dialog.open();
        handle.focus();
    }

    private void openBoardDialog(ConnectedAccount account) {
        var dialog = new Dialog("Pinterest-Board wählen");
        var boards = new ComboBox<PinterestClient.Board>("Board");
        boards.setItemLabelGenerator(PinterestClient.Board::name);
        boards.setWidthFull();
        try {
            var items = accountService.pinterestBoards(account);
            boards.setItems(items);
            items.stream().filter(b -> b.id().equals(account.getBoardId())).findFirst().ifPresent(boards::setValue);
        } catch (RuntimeException ex) {
            Ui.error(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return;
        }
        var save = new Button("Speichern", e -> {
            if (boards.getValue() != null) {
                accountService.setPinterestBoard(account, boards.getValue());
                dialog.close();
                refresh();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(new VerticalLayout(boards, new Paragraph("Neue Pins landen auf diesem Board.")));
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), save);
        dialog.setWidth("420px");
        dialog.open();
    }
}
