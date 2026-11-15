package de.witchcafe.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import de.witchcafe.social.client.FeedItem;
import de.witchcafe.social.client.SocialNetworks;
import de.witchcafe.social.domain.ConnectedAccount;

/**
 * Lädt die Feeds aller verbundenen Konten parallel und führt sie zeitlich sortiert zusammen.
 */
@Service
public class FeedService {

    /** Ergebnis inkl. Fehlermeldungen einzelner Konten, damit ein Ausfall nicht alles blockiert. */
    public record Feed(List<FeedItem> items, List<String> errors) {
    }

    private final AccountService accountService;
    private final SocialNetworks networks;

    public FeedService(AccountService accountService, SocialNetworks networks) {
        this.accountService = accountService;
        this.networks = networks;
    }

    public Feed load(int limitPerAccount) {
        List<ConnectedAccount> accounts = accountService.listMineUsable();
        List<FeedItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = accounts.stream()
                    .map(account -> CompletableFuture.supplyAsync(
                            () -> networks.get(account).feed(account, limitPerAccount), executor)
                            .handle((result, error) -> {
                                synchronized (items) {
                                    if (error != null) {
                                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                                        errors.add(networks.label(account) + ": " + cause.getMessage());
                                    } else {
                                        items.addAll(result);
                                    }
                                }
                                return null;
                            }))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        }
        items.sort(Comparator.comparing(FeedItem::createdAt).reversed());
        return new Feed(items, errors);
    }
}
