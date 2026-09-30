package com.leadmanagement.lms.identity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Explicit peer grouping: members assigned to the same group can see each other's leads.
 * Visibility of the group itself is limited to: creator, members, and the management chain
 * above any member (not global to every admin).
 */
@Entity
@Table(name = "peer_groups")
public class PeerGroup {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** User who created the group (admin). Null only for legacy rows. */
    @Column(name = "created_by")
    private UUID createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public PeerGroup() {}
    public PeerGroup(String name, UUID createdById) {
        this.name = name;
        this.createdById = createdById;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
