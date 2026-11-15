package de.witchcafe.social;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.witchcafe.social.domain.MediaFileRepository;

/**
 * Liefert hochgeladene Bilder aus. Liegt unter /images/**, das witch-auth ohne
 * Anmeldung freigibt - nötig, weil Instagram Bilder selbst per URL abholt.
 * Die IDs sind zufällige UUIDs und damit nicht erratbar.
 */
@RestController
public class MediaController {

    public static final String PATH = "/images/social/";

    private final MediaFileRepository repository;

    public MediaController(MediaFileRepository repository) {
        this.repository = repository;
    }

    @GetMapping(PATH + "{id}")
    public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body(file.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}
