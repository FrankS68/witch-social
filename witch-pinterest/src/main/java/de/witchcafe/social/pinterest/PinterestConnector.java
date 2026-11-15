package de.witchcafe.social.pinterest;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.witchcafe.social.AccountService;
import de.witchcafe.social.domain.ConnectedAccount;
import de.witchcafe.social.ui.AccountConnector;
import de.witchcafe.social.ui.Ui;

/**
 * "Pinterest verbinden" und Board-Auswahl in der Kontenübersicht.
 */
@Component
public class PinterestConnector implements AccountConnector {

    private final PinterestClient pinterest;
    private final AccountService accountService;

    public PinterestConnector(PinterestClient pinterest, AccountService accountService) {
        this.pinterest = pinterest;
        this.accountService = accountService;
    }

    @Override
    public Set<String> networks() {
        return Set.of(PinterestClient.ID);
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public com.vaadin.flow.component.Component connectButton(Runnable refresh) {
        return Ui.connectRedirectButton(PinterestClient.NAME, pinterest.color(), "/connect/pinterest",
                pinterest.configured(), "Pinterest-App fehlt: social.pinterest.client-id / client-secret setzen");
    }

    @Override
    public String details(ConnectedAccount account) {
        return account.getBoardName() != null
                ? "Board: " + account.getBoardName()
                : "Board: erstes Board (noch nicht gewählt)";
    }

    @Override
    public List<com.vaadin.flow.component.Component> actions(ConnectedAccount account, Runnable refresh) {
        var board = new Button(VaadinIcon.PIN.create(), e -> openBoardDialog(account, refresh));
        board.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        board.setTooltipText("Board wählen");
        board.setAriaLabel("Board wählen");
        return List.of(board);
    }

    private void openBoardDialog(ConnectedAccount account, Runnable refresh) {
        var boards = new ComboBox<PinterestClient.Board>("Board");
        boards.setItemLabelGenerator(PinterestClient.Board::name);
        boards.setWidthFull();
        try {
            var items = pinterest.boards(accountService.requireOwn(account));
            boards.setItems(items);
            items.stream().filter(b -> b.id().equals(account.getBoardId())).findFirst().ifPresent(boards::setValue);
        } catch (RuntimeException ex) {
            Ui.error(ex);
            return;
        }
        var dialog = new Dialog("Pinterest-Board wählen");
        var save = new Button("Speichern", e -> {
            PinterestClient.Board selected = boards.getValue();
            if (selected != null) {
                accountService.update(account, own -> own.setBoard(selected.id(), selected.name()));
                dialog.close();
                refresh.run();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(new VerticalLayout(boards, new Paragraph("Neue Pins landen auf diesem Board.")));
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), save);
        dialog.setWidth("420px");
        dialog.open();
    }
}
