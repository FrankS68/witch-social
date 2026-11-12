package de.witchcafe.app.social.ui;

import java.time.ZoneId;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.app.social.FeedService;
import de.witchcafe.app.social.client.FeedItem;
import de.witchcafe.auth.CurrentUser;
import jakarta.annotation.security.PermitAll;

/**
 * Startseite: zusammengeführte Feeds aller verbundenen Konten.
 */
@Route("")
@PageTitle("Feeds")
@Menu(order = 0, icon = "vaadin:newspaper", title = "Feeds")
@PermitAll
public class FeedView extends Main {

    private static final int ITEMS_PER_ACCOUNT = 20;

    private final FeedService feedService;
    private final ZoneId zone;
    private final Div errors = new Div();
    private final Div items = new Div();

    public FeedView(FeedService feedService, CurrentUser currentUser) {
        this.feedService = feedService;
        this.zone = currentUser.require().getZoneId();
        addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.MaxWidth.SCREEN_MEDIUM);

        var refresh = new Button("Aktualisieren", VaadinIcon.REFRESH.create(), e -> load());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        var header = new HorizontalLayout(Ui.title("Feeds"), refresh);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);

        errors.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontSize.SMALL);
        items.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Gap.MEDIUM);
        add(header, errors, items);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        load();
    }

    private void load() {
        FeedService.Feed feed = feedService.load(ITEMS_PER_ACCOUNT);
        errors.removeAll();
        feed.errors().forEach(error -> errors.add(new Paragraph(error)));
        items.removeAll();
        if (feed.items().isEmpty() && feed.errors().isEmpty()) {
            items.add(new Paragraph(new Text("Noch nichts zu sehen. Verbinde zuerst Konten unter "),
                    new RouterLink("Konten", AccountsView.class), new Text(".")));
            return;
        }
        feed.items().forEach(item -> items.add(card(item)));
    }

    private Div card(FeedItem item) {
        var author = new Span(item.author());
        author.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        var time = new Span(Ui.format(item.createdAt(), zone));
        time.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        var head = new HorizontalLayout(Ui.badge(item.network()), author, time);
        head.setAlignItems(FlexComponent.Alignment.CENTER);
        head.setSpacing(true);

        var card = new Div(head);
        card.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Border.ALL, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.BASE);

        if (!item.text().isBlank()) {
            var text = new Paragraph(item.text());
            text.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
            card.add(text);
        }
        if (item.imageUrl() != null) {
            var image = new Image(item.imageUrl(), "");
            image.setMaxWidth("100%");
            image.setMaxHeight("400px");
            image.addClassNames(LumoUtility.BorderRadius.SMALL);
            card.add(image);
        }
        if (item.url() != null) {
            var open = new Anchor(item.url(), "Im Netzwerk öffnen", AnchorTarget.BLANK);
            open.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Display.BLOCK, LumoUtility.Margin.Top.SMALL);
            card.add(open);
        }
        var source = new Span(item.accountLabel());
        source.addClassNames(LumoUtility.TextColor.TERTIARY, LumoUtility.FontSize.XSMALL);
        card.add(source);
        return card;
    }
}
