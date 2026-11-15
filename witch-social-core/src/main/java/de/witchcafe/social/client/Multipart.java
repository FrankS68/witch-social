package de.witchcafe.social.client;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import de.witchcafe.social.domain.MediaFile;

/**
 * Datei-Teil für multipart/form-data-Uploads.
 */
public final class Multipart {

    private Multipart() {
    }

    public static HttpEntity<ByteArrayResource> file(MediaFile media) {
        var resource = new ByteArrayResource(media.getData()) {
            @Override
            public String getFilename() {
                return media.getFileName();
            }
        };
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(media.getContentType()));
        return new HttpEntity<>(resource, headers);
    }
}
