package de.witchcafe.social.ui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;

/**
 * Legt die Feeds ({@link FeedView}, Route /feeds) zusaetzlich auf die Startseite "/".
 * Apps mit eigener Startseite schalten das ab:
 *
 * <pre>
 * social.feeds-start-page=false   # "/" gehoert der App, Feeds nur unter /feeds
 * </pre>
 *
 * Die Startseite wird programmatisch registriert statt per {@code @RouteAlias("")},
 * weil Vaadin doppelte Routen schon beim Einlesen der Annotationen abweist und eine
 * App sonst "/" nicht selbst belegen koennte.
 */
@Configuration
public class SocialViewsConfig {

    @Bean
    VaadinServiceInitListener witchSocialFeedsStartPage(
            @Value("${social.feeds-start-page:true}") boolean feedsStartPage) {
        return event -> {
            if (!feedsStartPage) {
                return;
            }
            RouteConfiguration routes = RouteConfiguration.forRegistry(
                    ApplicationRouteRegistry.getInstance(event.getSource().getContext()));
            // Ohne eigene Layout-Kette greift das @Layout der App (autoLayout von @Route)
            routes.setRoute("", FeedView.class);
        };
    }
}
