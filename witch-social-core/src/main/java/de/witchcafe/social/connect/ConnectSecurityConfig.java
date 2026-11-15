package de.witchcafe.social.connect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Eigene Filterkette für die OAuth-Verbinden-Flows unter /connect/**.
 *
 * <p>Die Vaadin-Security-Konfiguration aus witch-auth verweigert alle Nicht-Vaadin-Pfade,
 * die nicht ausdrücklich freigegeben sind. Diese Kette greift vorher und erlaubt
 * /connect/** jedem angemeldeten Benutzer; Anonyme landen auf der Login-Seite.
 */
@Configuration
public class ConnectSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain connectSecurityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContextRepository) throws Exception {
        http.securityMatcher("/connect/**")
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        return http.build();
    }
}
