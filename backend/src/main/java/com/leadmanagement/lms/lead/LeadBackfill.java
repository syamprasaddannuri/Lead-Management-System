package com.leadmanagement.lms.lead;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** One-time backfill of phone_normalized for leads created before that column existed. */
@Component
public class LeadBackfill {

    private final LeadRepository repo;

    public LeadBackfill(LeadRepository repo) { this.repo = repo; }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        try { repo.backfillNormalizedPhones(); } catch (Exception ignored) { /* best-effort on boot */ }
    }
}
