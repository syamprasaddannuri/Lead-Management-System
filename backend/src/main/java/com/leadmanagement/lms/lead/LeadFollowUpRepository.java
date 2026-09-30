package com.leadmanagement.lms.lead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LeadFollowUpRepository extends JpaRepository<LeadFollowUp, UUID> {

    List<LeadFollowUp> findByLeadIdOrderByDueAtAsc(UUID leadId);

    List<LeadFollowUp> findByStatusOrderByDueAtAsc(String status);

    @Query("SELECT f FROM LeadFollowUp f WHERE f.status = :status AND f.leadId IN :leadIds ORDER BY f.dueAt ASC")
    List<LeadFollowUp> findPendingForLeads(@Param("status") String status, @Param("leadIds") Collection<UUID> leadIds);

    /** Pending follow-ups on leads owned by anyone in the owner-id set (dashboard scope). */
    @Query("SELECT f FROM LeadFollowUp f, Lead l WHERE f.leadId = l.id AND f.status = 'PENDING' " +
            "AND l.ownerId IN :ownerIds ORDER BY f.dueAt ASC")
    List<LeadFollowUp> findPendingForOwners(@Param("ownerIds") Collection<UUID> ownerIds);

    void deleteByLeadId(UUID leadId);
}
