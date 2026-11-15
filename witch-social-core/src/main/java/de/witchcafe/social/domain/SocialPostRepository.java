package de.witchcafe.social.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {

    List<SocialPost> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<SocialPost> findByStatusAndScheduledAtLessThanEqual(PostStatus status, Instant time);

    /** Setzt den Status nur, wenn er noch dem erwarteten entspricht (Schutz gegen Doppelveröffentlichung). */
    @Modifying
    @Transactional
    @Query("update SocialPost p set p.status = :newStatus where p.id = :id and p.status = :expected")
    int compareAndSetStatus(@Param("id") Long id, @Param("expected") PostStatus expected,
            @Param("newStatus") PostStatus newStatus);

    @Modifying
    @Transactional
    @Query("update PostTarget t set t.account = null where t.account = :account")
    void detachAccount(@Param("account") ConnectedAccount account);
}
