package com.leadmanagement.lms.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    List<EmailTemplate> findByActiveTrueOrderByNameAsc();
    List<EmailTemplate> findAllByOrderByNameAsc();
}
