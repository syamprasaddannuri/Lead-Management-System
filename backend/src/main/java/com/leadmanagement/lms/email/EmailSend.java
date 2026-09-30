package com.leadmanagement.lms.email;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit row for every outbound email the app successfully sends.
 * Used by SUPER_ADMIN email stats (who sent how many, plus system triggers).
 */
@Entity
@Table(name = "email_sends", indexes = {
        @Index(name = "idx_email_sends_created", columnList = "created_at"),
        @Index(name = "idx_email_sends_sender", columnList = "sender_label")
})
public class EmailSend {

    /** Lead outreach from staff. */
    public static final String CAT_LEAD = "LEAD";
    /** Password reset (forgot-password) — grouped under login triggers. */
    public static final String CAT_PASSWORD_RESET = "PASSWORD_RESET";
    /** New staff account invite — grouped under login triggers. */
    public static final String CAT_STAFF_INVITE = "STAFF_INVITE";
    /** Notify lead owner of an inbound reply. */
    public static final String CAT_REPLY_NOTIFY = "REPLY_NOTIFY";
    /** Super-admin SES/SMTP test. */
    public static final String CAT_TEST = "TEST";
    /** Rows imported from historical lead_activities. */
    public static final String CAT_LEAD_BACKFILL = "LEAD_BACKFILL";

    public static final String LABEL_LOGIN_TRIGGERS = "Login triggers";
    public static final String LABEL_REPLY_NOTIFY = "Reply notifications";
    public static final String LABEL_TEST = "Test emails";
    public static final String LABEL_SYSTEM = "System";

    @Id
    @GeneratedValue
    private UUID id;

    /** LEAD | PASSWORD_RESET | STAFF_INVITE | REPLY_NOTIFY | TEST | LEAD_BACKFILL */
    @Column(nullable = false, length = 40)
    private String category;

    /**
     * Bucket shown in stats: staff display name, or a system label
     * (e.g. "Login triggers", "Reply notifications").
     */
    @Column(name = "sender_label", nullable = false, length = 200)
    private String senderLabel;

    /** Actor email when a person sent it; null for pure system mail. */
    @Column(name = "sender_email", length = 320)
    private String senderEmail;

    @Column(name = "to_email", length = 320)
    private String toEmail;

    @Column(length = 500)
    private String subject;

    @Column(name = "lead_id")
    private UUID leadId;

    /** When set, this row was imported from lead_activities (dedup key for backfill). */
    @Column(name = "source_activity_id", unique = true)
    private UUID sourceActivityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public EmailSend() {}

    public EmailSend(String category, String senderLabel, String senderEmail,
                     String toEmail, String subject, UUID leadId) {
        this.category = category;
        this.senderLabel = senderLabel;
        this.senderEmail = senderEmail;
        this.toEmail = toEmail;
        this.subject = subject;
        this.leadId = leadId;
    }

    public EmailSend(String category, String senderLabel, String senderEmail,
                     String toEmail, String subject, UUID leadId,
                     OffsetDateTime createdAt, UUID sourceActivityId) {
        this(category, senderLabel, senderEmail, toEmail, subject, leadId);
        if (createdAt != null) this.createdAt = createdAt;
        this.sourceActivityId = sourceActivityId;
    }

    public UUID getId() { return id; }
    public String getCategory() { return category; }
    public String getSenderLabel() { return senderLabel; }
    public String getSenderEmail() { return senderEmail; }
    public String getToEmail() { return toEmail; }
    public String getSubject() { return subject; }
    public UUID getLeadId() { return leadId; }
    public UUID getSourceActivityId() { return sourceActivityId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
