package com.leadmanagement.lms.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {
    List<Lead> findAllByOrderByCreatedAtDesc();
    List<Lead> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Page<Lead> findByOwnerId(UUID ownerId, Pageable pageable);

    /** Leads owned by anyone in a set of users (reporting-subtree scope). */
    List<Lead> findByOwnerIdInOrderByCreatedAtDesc(java.util.Collection<UUID> ownerIds);
    Page<Lead> findByOwnerIdIn(java.util.Collection<UUID> ownerIds, Pageable pageable);

    /** Lead counts grouped by status — for the dashboard, without hydrating every row. */
    @Query("select l.status, count(l) from Lead l group by l.status")
    List<Object[]> countByStatusAll();

    @Query("select l.status, count(l) from Lead l where l.ownerId in :ids group by l.status")
    List<Object[]> countByStatusForOwners(java.util.Collection<UUID> ids);

    /** Existing lead with this normalized phone (for single-add duplicate detection). */
    Optional<Lead> findFirstByPhoneNormalized(String phoneNormalized);
    Optional<Lead> findFirstByEmailIgnoreCase(String email);

    /** Lightweight projections for bulk-import dedup (avoids hydrating every lead entity). */
    @Query("select l.phoneNormalized from Lead l where l.phoneNormalized is not null")
    List<String> findAllPhonesNormalized();

    @Query("select lower(l.email) from Lead l where l.email is not null and l.email <> ''")
    List<String> findAllEmailsLower();

    /** Which of the given normalized phones / emails already exist (for chunked dedup checks). */
    @Query("select l.phoneNormalized from Lead l where l.phoneNormalized in :phones")
    List<String> findPhonesNormalizedIn(java.util.Collection<String> phones);

    @Query("select lower(l.email) from Lead l where lower(l.email) in :emails")
    List<String> findEmailsLowerIn(java.util.Collection<String> emails);

    /** One-time backfill of phone_normalized for rows created before the column existed. */
    @Modifying
    @Transactional
    @Query(value = "update leads set phone_normalized = right(regexp_replace(phone, '\\D', '', 'g'), 10) " +
            "where phone_normalized is null and phone is not null", nativeQuery = true)
    int backfillNormalizedPhones();
}
