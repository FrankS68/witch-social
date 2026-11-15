package de.witchcafe.social.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {
}
