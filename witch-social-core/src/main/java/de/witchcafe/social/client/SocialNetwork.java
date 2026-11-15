package de.witchcafe.social.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.witchcafe.social.ImageUpload;
import de.witchcafe.social.domain.ConnectedAccount;

/**
 * Ein soziales Netzwerk: Eigenschaften, Veröffentlichen und Feed-Lesen.
 *
 * <p>Jedes Netzwerk-Modul (witch-bluesky, witch-mastodon, ...) stellt eine Spring-Bean dieses
 * Typs bereit. Welche Netzwerke eine App kann, entscheidet allein, welche Module sie als
 * Maven-Abhängigkeit einbindet. Gefunden werden die Beans über {@link SocialNetworks}.
 */
public interface SocialNetwork {

    /**
     * Stabile Kennung, wird bei jedem verbundenen Konto gespeichert (z.B. "BLUESKY").
     * Nie ändern, sonst passen gespeicherte Konten nicht mehr. Höchstens 20 Zeichen.
     */
    String id();

    String displayName();

    /** Markenfarbe für Badges und Buttons in der Oberfläche. */
    String color();

    /** Maximale Textlänge in Zeichen (als Codepoints gezählt). */
    int maxLength();

    /** Netzwerke wie Instagram oder Pinterest akzeptieren keine reinen Textbeiträge. */
    default boolean imageRequired() {
        return false;
    }

    /**
     * Prüft einen Beitrag gegen die Grenzen des Netzwerks; leere Liste = in Ordnung.
     * Netzwerke mit weiteren Einschränkungen (Bildformat, Bildgröße) überschreiben das
     * und rufen {@code SocialNetwork.super.validate(...)} auf.
     */
    default List<String> validate(PublishRequest request, @Nullable ImageUpload image) {
        List<String> problems = new ArrayList<>();
        String text = request.textWithLink();
        int length = text.codePointCount(0, text.length());
        if (length > maxLength()) {
            problems.add(displayName() + ": Text ist " + length + " Zeichen lang, erlaubt sind " + maxLength() + ".");
        }
        if (imageRequired() && image == null) {
            problems.add(displayName() + " braucht ein Bild.");
        }
        return problems;
    }

    PublishResult publish(ConnectedAccount account, PublishRequest request);

    List<FeedItem> feed(ConnectedAccount account, int limit);

    /** Anzeige wie "Mastodon · @frank@mastodon.social". */
    default String label(ConnectedAccount account) {
        return displayName() + " · " + account.getDisplayName();
    }
}
