package de.witchcafe.app.social.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonAppRepository extends JpaRepository<MastodonApp, String> {
}
