package de.witchcafe.social.meta;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Meta-App (Facebook-Seiten und Instagram-Business-Konten) unter {@code social.meta.*}.
 */
@ConfigurationProperties("social.meta")
public record MetaProperties(@Nullable String appId, @Nullable String appSecret,
        @DefaultValue("v23.0") String graphVersion) {

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
