package com.leadmanagement.lms.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadChangeRequestRepository extends JpaRepository<LeadChangeRequest, UUID> {
    List<LeadChangeRequest> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
    List<LeadChangeRequest> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    void deleteByLeadId(UUID leadId);
}
