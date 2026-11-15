package de.witchcafe.social.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.jspecify.annotations.Nullable;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.social.client.SocialNetwork;

/**
 * Gemeinsame Bausteine der Social-Views, auch für die Netzwerk-Module.
 */
public final class Ui {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String UNKNOWN_COLOR = "var(--lumo-contrast-50pct)";

    private Ui() {
    }

    public static H2 title(String text) {
        var title = new H2(text);
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.Bottom.MEDIUM);
        return title;
    }

    /** Farbiges Kürzel des Netzwerks. */
    public static Span badge(SocialNetwork network) {
        return badge(network.displayName(), network.color());
    }

    /** Badge für ein Netzwerk, dessen Modul evtl. nicht eingebunden ist. */
    public static Span badge(String displayName, @Nullable String color) {
        var badge = new Span(displayName);
        badge.getStyle()
                .set("background", color != null ? color : UNKNOWN_COLOR)
                .set("color", "white")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0 var(--lumo-space-s)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap");
        return badge;
    }

    /** "… verbinden"-Button in Markenfarbe, der zu einem OAuth-Flow unter /connect/** springt. */
    public static Button connectRedirectButton(String label, String color, String path, boolean enabled,
            String disabledHint) {
        var button = connectButton(label, color, () -> UI.getCurrent().getPage().setLocation(path));
        button.setEnabled(enabled);
        if (!enabled) {
            button.setTooltipText(disabledHint);
        }
        return button;
    }

    /** "… verbinden"-Button in Markenfarbe, z.B. um einen Dialog zu öffnen. */
    public static Button connectButton(String label, String color, Runnable action) {
        var button = new Button(label + " verbinden", VaadinIcon.PLUS.create(), e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle().set("background", color);
        return button;
    }

    public static String format(@Nullable Instant instant, ZoneId zone) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(zone));
    }

    public static void success(String message) {
        Notification.show(message, 4000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        Notification.show(message, 8000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /** Fehlermeldung einer Exception, nie leer. */
    public static void error(RuntimeException e) {
        error(e.getMessage() != null ? e.getMessage() : e.toString());
    }
}
