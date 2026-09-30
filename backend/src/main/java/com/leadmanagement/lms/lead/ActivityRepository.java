package com.leadmanagement.lms.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
    void deleteByLeadId(UUID leadId);
}
