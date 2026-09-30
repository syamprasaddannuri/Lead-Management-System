package com.leadmanagement.lms.lead;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Timeline entry on a lead: notes plus auto-logged stage changes, assignments, creation. */
@Entity
@Table(name = "lead_activities")
public class Activity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    /** NOTE | STAGE_CHANGE | ASSIGNMENT | CREATED | EMAIL */
    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "text")
    private String body;

    /** Who performed it (email), or null for system. */
    @Column(name = "author")
    private String author;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Activity() {}
    public Activity(UUID leadId, String type, String body, String author) {
        this.leadId = leadId; this.type = type; this.body = body; this.author = author;
    }

    public UUID getId() { return id; }
    public UUID getLeadId() { return leadId; }
    public void setLeadId(UUID leadId) { this.leadId = leadId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
