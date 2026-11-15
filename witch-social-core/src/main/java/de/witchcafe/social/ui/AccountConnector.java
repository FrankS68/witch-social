package de.witchcafe.social.ui;

import java.util.List;
import java.util.Set;

import com.vaadin.flow.component.Component;

import de.witchcafe.social.domain.ConnectedAccount;

/**
 * Oberflächen-Beitrag eines Netzwerk-Moduls zur Kontenübersicht ({@link AccountsView}):
 * ein "verbinden"-Button sowie Details und Aktionen für die Konten seiner Netzwerke.
 *
 * <p>Ein Connector kann mehrere Netzwerke abdecken (z.B. Meta: Facebook und Instagram).
 * Implementierungen sind Spring-Beans (Singletons) und dürfen deshalb keinen UI-Zustand halten;
 * Komponenten jedes Mal neu erzeugen.
 */
public interface AccountConnector {

    /** IDs der Netzwerke ({@code SocialNetwork#id()}), deren Konten dieser Connector anlegt. */
    Set<String> networks();

    /** Reihenfolge der Buttons in der Kontenübersicht, kleinere zuerst. */
    default int order() {
        return 100;
    }

    /**
     * Button (oder andere Komponente) zum Verbinden eines neuen Kontos.
     *
     * @param refresh aktualisiert die Kontenliste, z.B. nach Verbinden über einen Dialog
     */
    Component connectButton(Runnable refresh);

    /** Text für die Spalte "Details". */
    default String details(ConnectedAccount account) {
        return account.getInstanceUrl() != null ? account.getInstanceUrl() : "";
    }

    /** Zusätzliche Aktionen (Icon-Buttons) neben "Trennen". */
    default List<Component> actions(ConnectedAccount account, Runnable refresh) {
        return List.of();
    }
}
