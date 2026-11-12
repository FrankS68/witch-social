package de.witchcafe.app.social.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.jspecify.annotations.Nullable;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.witchcafe.app.social.domain.Network;

/**
 * Gemeinsame Bausteine der Social-Views.
 */
final class Ui {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Ui() {
    }

    static H2 title(String text) {
        var title = new H2(text);
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.Bottom.MEDIUM);
        return title;
    }

    /** Farbiges Kürzel des Netzwerks. */
    static Span badge(Network network) {
        var badge = new Span(network.displayName());
        badge.getStyle()
                .set("background", network.color())
                .set("color", "white")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0 var(--lumo-space-s)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap");
        return badge;
    }

    static String format(@Nullable Instant instant, ZoneId zone) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(zone));
    }

    static void success(String message) {
        Notification.show(message, 4000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    static void error(String message) {
        Notification.show(message, 8000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
