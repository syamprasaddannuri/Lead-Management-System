package com.leadmanagement.lms.lead;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "leads")
public class Lead {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    private String email;
    @Column(nullable = false)
    private String phone;

    /** Digits-only, last-10 form of the phone, for duplicate detection across formats. */
    @Column(name = "phone_normalized")
    private String phoneNormalized;

    /** Marketing source, e.g. "Website - Agentic AI" / "Website - Need Help". */
    private String source;
    /** Program slug: agentic-ai | ai-ml | null. */
    @Column(name = "course_id")
    private String courseId;
    @Column(name = "course_name")
    private String courseName;

    @Column(columnDefinition = "text")
    private String message;

    /** Extra/unmapped columns from imports, stored as JSON. */
    @Column(columnDefinition = "text")
    private String extra;

    /** Pipeline stage. NEW | CONTACTED | QUALIFIED | NURTURING | NEGOTIATION | WON | LOST. */
    @Column(nullable = false)
    private String status = "NEW";

    /** Assigned staff user id (nullable until assigned). */
    @Column(name = "owner_id")
    private UUID ownerId;

    /** Staff user who brought this lead in (manual add / bulk import). Null for website captures. */
    @Column(name = "created_by")
    private UUID createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPhoneNormalized() { return phoneNormalized; }
    public void setPhoneNormalized(String phoneNormalized) { this.phoneNormalized = phoneNormalized; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
