package de.witchcafe.app.social.ui;

import java.time.ZoneId;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.app.social.PostService;
import de.witchcafe.app.social.domain.PostStatus;
import de.witchcafe.app.social.domain.PostTarget;
import de.witchcafe.app.social.domain.SocialPost;
import de.witchcafe.app.social.domain.TargetStatus;
import de.witchcafe.auth.CurrentUser;
import jakarta.annotation.security.PermitAll;

/**
 * Übersicht über veröffentlichte und geplante Beiträge mit Status je Konto.
 */
@Route("beitraege")
@PageTitle("Beiträge")
@Menu(order = 2, icon = "vaadin:list", title = "Beiträge")
@PermitAll
public class PostsView extends Main {

    private final PostService postService;
    private final ZoneId zone;
    private final Grid<SocialPost> grid = new Grid<>();

    public PostsView(PostService postService, CurrentUser currentUser) {
        this.postService = postService;
        this.zone = currentUser.require().getZoneId();
        addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN,
                LumoUtility.BoxSizing.BORDER);
        setSizeFull();

        var refresh = new Button("Aktualisieren", VaadinIcon.REFRESH.create(), e -> refresh());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        var header = new HorizontalLayout(Ui.title("Beiträge"), refresh);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);

        grid.addColumn(post -> Ui.format(post.getScheduledAt() != null ? post.getScheduledAt() : post.getCreatedAt(),
                zone)).setHeader("Zeitpunkt").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::textCell).setHeader("Text").setFlexGrow(3);
        grid.addColumn(post -> post.getStatus().label()).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::targetsCell).setHeader("Konten").setFlexGrow(2);
        grid.addComponentColumn(this::actionsCell).setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Noch keine Beiträge.");
        grid.setSizeFull();

        add(header, grid);
        refresh();
    }

    private void refresh() {
        grid.setItems(postService.listMine());
    }

    private Component textCell(SocialPost post) {
        String text = post.getText();
        var span = new Span(text.length() > 200 ? text.substring(0, 200) + "…" : text);
        if (post.getMediaId() != null) {
            var withImage = new Div(VaadinIcon.PICTURE.create(), span);
            withImage.addClassNames(LumoUtility.Display.FLEX, LumoUtility.Gap.SMALL);
            return withImage;
        }
        return span;
    }

    private Component targetsCell(SocialPost post) {
        var cell = new Div();
        cell.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Gap.XSMALL);
        for (PostTarget target : post.getTargets()) {
            Component line;
            if (target.getStatus() == TargetStatus.PUBLISHED && target.getUrl() != null) {
                line = new Anchor(target.getUrl(), "✓ " + target.getAccountLabel(), AnchorTarget.BLANK);
            } else if (target.getStatus() == TargetStatus.FAILED) {
                var failed = new Span("✗ " + target.getAccountLabel());
                failed.addClassNames(LumoUtility.TextColor.ERROR);
                failed.setTitle(target.getError() != null ? target.getError() : "");
                var error = new Span(target.getError() != null ? target.getError() : "");
                error.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.ERROR);
                line = new Div(failed, new Div(error));
            } else {
                line = new Span((target.getStatus() == TargetStatus.PUBLISHED ? "✓ " : "○ ")
                        + target.getAccountLabel());
            }
            cell.add(line);
        }
        return cell;
    }

    private Component actionsCell(SocialPost post) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        if (post.getStatus() == PostStatus.FAILED || post.getStatus() == PostStatus.PARTIALLY_PUBLISHED) {
            actions.add(iconButton(VaadinIcon.REFRESH, "Fehlgeschlagene erneut senden", () -> {
                SocialPost result = postService.retry(post);
                if (result.getStatus() == PostStatus.PUBLISHED) {
                    Ui.success("Jetzt überall veröffentlicht");
                } else {
                    Ui.error("Weiterhin nicht überall veröffentlicht");
                }
            }));
        }
        if (post.getStatus() == PostStatus.SCHEDULED) {
            actions.add(iconButton(VaadinIcon.BAN, "Planung abbrechen", () -> postService.cancel(post)));
        }
        if (post.getStatus() != PostStatus.PUBLISHING) {
            actions.add(iconButton(VaadinIcon.TRASH, "Aus der Liste löschen", () -> confirmDelete(post)));
        }
        return actions;
    }

    private void confirmDelete(SocialPost post) {
        var dialog = new ConfirmDialog("Beitrag löschen?",
                "Der Eintrag wird nur hier entfernt. Bereits veröffentlichte Beiträge bleiben in den Netzwerken bestehen.",
                "Löschen", e -> {
                    postService.delete(post);
                    refresh();
                });
        dialog.setCancelable(true);
        dialog.setCancelText("Abbrechen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.open();
    }

    private Button iconButton(VaadinIcon icon, String tooltip, Runnable action) {
        var button = new Button(icon.create(), e -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                Ui.error(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            refresh();
        });
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.setTooltipText(tooltip);
        button.setAriaLabel(tooltip);
        return button;
    }
}
