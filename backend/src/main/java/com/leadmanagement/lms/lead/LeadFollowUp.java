package com.leadmanagement.lms.lead;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Scheduled follow-up on a lead: message + due time; completed from dashboard or lead detail. */
@Entity
@Table(name = "lead_follow_ups", indexes = {
        @Index(name = "idx_followups_status_due", columnList = "status, due_at"),
        @Index(name = "idx_followups_lead", columnList = "lead_id")
})
public class LeadFollowUp {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    /** PENDING | DONE */
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public LeadFollowUp() {}

    public LeadFollowUp(UUID leadId, String message, OffsetDateTime dueAt, String createdBy) {
        this.leadId = leadId;
        this.message = message;
        this.dueAt = dueAt;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public UUID getLeadId() { return leadId; }
    public void setLeadId(UUID leadId) { this.leadId = leadId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
