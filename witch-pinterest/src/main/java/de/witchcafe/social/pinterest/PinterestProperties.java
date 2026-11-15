package de.witchcafe.social.pinterest;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Pinterest-App unter {@code social.pinterest.*}; für Trial-Zugang
 * {@code api-base-url} auf https://api-sandbox.pinterest.com setzen.
 */
@ConfigurationProperties("social.pinterest")
public record PinterestProperties(@Nullable String clientId, @Nullable String clientSecret,
        @DefaultValue("https://api.pinterest.com") String apiBaseUrl) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
