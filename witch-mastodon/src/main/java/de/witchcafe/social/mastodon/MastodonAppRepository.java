package de.witchcafe.social.mastodon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonAppRepository extends JpaRepository<MastodonApp, String> {
}
