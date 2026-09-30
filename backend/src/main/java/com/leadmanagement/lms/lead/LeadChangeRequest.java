package com.leadmanagement.lms.lead;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A sales rep's request to change a non-blank lead field, approved by a manager or above. */
@Entity
@Table(name = "lead_change_requests")
public class LeadChangeRequest {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    /** Which field: name | email | phone | source | course. */
    @Column(nullable = false)
    private String field;

    @Column(name = "current_value", columnDefinition = "text")
    private String currentValue;
    @Column(name = "requested_value", columnDefinition = "text")
    private String requestedValue;

    /** PENDING | APPROVED | REJECTED. */
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "requested_by")
    private String requestedBy;
    @Column(name = "decided_by")
    private String decidedBy;
    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    public LeadChangeRequest() {}

    public LeadChangeRequest(UUID leadId, String field, String currentValue, String requestedValue,
                             String requestedBy, String note) {
        this.leadId = leadId;
        this.field = field;
        this.currentValue = currentValue;
        this.requestedValue = requestedValue;
        this.requestedBy = requestedBy;
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getLeadId() { return leadId; }
    public void setLeadId(UUID leadId) { this.leadId = leadId; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getCurrentValue() { return currentValue; }
    public void setCurrentValue(String currentValue) { this.currentValue = currentValue; }
    public String getRequestedValue() { return requestedValue; }
    public void setRequestedValue(String requestedValue) { this.requestedValue = requestedValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
}
