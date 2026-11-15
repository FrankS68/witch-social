package de.witchcafe.social.ui;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.auth.CurrentUser;
import de.witchcafe.social.AccountService;
import de.witchcafe.social.client.SocialNetwork;
import de.witchcafe.social.client.SocialNetworks;
import de.witchcafe.social.domain.ConnectedAccount;
import jakarta.annotation.security.PermitAll;

/**
 * Konten verbinden und trennen. Welche Netzwerke angeboten werden, bestimmen die eingebundenen
 * Netzwerk-Module über ihre {@link AccountConnector}-Beans.
 */
@Route("konten")
@PageTitle("Konten")
@Menu(order = 3, icon = "vaadin:connect", title = "Konten")
@PermitAll
public class AccountsView extends Main implements BeforeEnterObserver {

    private final AccountService accountService;
    private final SocialNetworks networks;
    private final List<AccountConnector> connectors;
    private final ZoneId zone;
    private final Grid<ConnectedAccount> grid = new Grid<>();

    public AccountsView(AccountService accountService, SocialNetworks networks, List<AccountConnector> connectors,
            CurrentUser currentUser) {
        this.accountService = accountService;
        this.networks = networks;
        this.connectors = connectors.stream().sorted(Comparator.comparingInt(AccountConnector::order)).toList();
        this.zone = currentUser.require().getZoneId();
        addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN,
                LumoUtility.BoxSizing.BORDER);
        setSizeFull();

        var connect = new FlexLayout();
        this.connectors.forEach(connector -> connect.add(connector.connectButton(this::refresh)));
        connect.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        connect.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Margin.Bottom.MEDIUM);

        grid.addComponentColumn(this::badge).setHeader("Netzwerk").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(ConnectedAccount::getDisplayName).setHeader("Konto").setFlexGrow(2);
        grid.addComponentColumn(this::detailsCell).setHeader("Details").setFlexGrow(2);
        grid.addColumn(account -> Ui.format(account.getConnectedAt(), zone)).setHeader("Verbunden")
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actionsCell).setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("Noch keine Konten verbunden.");
        grid.setSizeFull();

        add(Ui.title("Konten verbinden"));
        if (this.connectors.isEmpty()) {
            add(new Paragraph("In dieser App ist kein Netzwerk-Modul eingebunden (z.B. witch-bluesky, witch-mastodon)."));
        } else {
            add(connect);
        }
        add(grid);
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

    private Optional<AccountConnector> connector(ConnectedAccount account) {
        return connectors.stream().filter(c -> c.networks().contains(account.getNetwork())).findFirst();
    }

    private Component badge(ConnectedAccount account) {
        Optional<SocialNetwork> network = networks.find(account.getNetwork());
        return network.map(Ui::badge).orElseGet(() -> Ui.badge(account.getNetwork(), null));
    }

    private Component detailsCell(ConnectedAccount account) {
        if (networks.find(account.getNetwork()).isEmpty()) {
            return new Span("Netzwerk-Modul nicht eingebunden");
        }
        return new Span(connector(account).map(c -> c.details(account)).orElse(""));
    }

    private Component actionsCell(ConnectedAccount account) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        connector(account).ifPresent(c -> c.actions(account, this::refresh).forEach(actions::add));
        var remove = new Button(VaadinIcon.UNLINK.create(), e -> confirmDisconnect(account));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
        remove.setTooltipText("Trennen");
        remove.setAriaLabel("Trennen");
        actions.add(remove);
        return actions;
    }

    private void confirmDisconnect(ConnectedAccount account) {
        var dialog = new ConfirmDialog("Konto trennen?",
                networks.label(account) + " wird aus der App entfernt. Geplante Beiträge an dieses Konto schlagen dann fehl. "
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
}
