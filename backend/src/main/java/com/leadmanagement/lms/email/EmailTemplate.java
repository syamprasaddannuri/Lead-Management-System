package com.leadmanagement.lms.email;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A reusable email template managed by managers+ and selected by reps when emailing a lead. */
@Entity
@Table(name = "email_templates")
public class EmailTemplate {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    /** Optional pipeline stage this template is meant for (NEW, CONTACTED, …) or null. */
    private String stage;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    public EmailTemplate() {}

    public EmailTemplate(String name, String subject, String body, String stage, boolean active, String createdBy) {
        this.name = name; this.subject = subject; this.body = body;
        this.stage = stage; this.active = active; this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
