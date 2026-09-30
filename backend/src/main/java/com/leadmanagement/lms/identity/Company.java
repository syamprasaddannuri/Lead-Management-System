package com.leadmanagement.lms.identity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A super-admin-defined grouping of admins who share visibility over each other's data. */
@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Company() {}
    public Company(String name) { this.name = name; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
