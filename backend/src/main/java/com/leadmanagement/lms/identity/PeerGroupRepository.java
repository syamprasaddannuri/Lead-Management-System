package com.leadmanagement.lms.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PeerGroupRepository extends JpaRepository<PeerGroup, UUID> {
    List<PeerGroup> findAllByOrderByNameAsc();
}
