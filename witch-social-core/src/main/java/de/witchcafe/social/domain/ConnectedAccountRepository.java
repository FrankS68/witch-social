package de.witchcafe.social.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Long> {

    List<ConnectedAccount> findByOwnerIdOrderByNetworkAscDisplayNameAsc(String ownerId);

    Optional<ConnectedAccount> findByOwnerIdAndNetworkAndExternalId(String ownerId, String network, String externalId);
}
