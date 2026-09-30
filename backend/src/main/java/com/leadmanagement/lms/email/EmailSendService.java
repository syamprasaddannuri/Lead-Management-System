package com.leadmanagement.lms.email;

import com.leadmanagement.lms.identity.User;
import com.leadmanagement.lms.identity.UserRepository;
import com.leadmanagement.lms.lead.Activity;
import com.leadmanagement.lms.lead.ActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.*;

/**
 * Records outbound mail for SUPER_ADMIN stats and backfills historical lead emails
 * from the activity timeline once.
 */
@Service
public class EmailSendService {

    private static final Logger log = LoggerFactory.getLogger(EmailSendService.class);

    private final EmailSendRepository repo;
    private final ActivityRepository activities;
    private final UserRepository users;
    private volatile boolean backfillChecked;

    public EmailSendService(EmailSendRepository repo, ActivityRepository activities, UserRepository users) {
        this.repo = repo;
        this.activities = activities;
        this.users = users;
    }

    /** Best-effort audit insert — never fails the actual send. */
    @Transactional
    public void record(String category, String senderLabel, String senderEmail,
                       String toEmail, String subject, UUID leadId) {
        record(category, senderLabel, senderEmail, toEmail, subject, leadId, null);
    }

    /**
     * Best-effort audit insert. When {@code sourceActivityId} is set, backfill will skip that
     * timeline row so lead emails are not double-counted.
     */
    @Transactional
    public void record(String category, String senderLabel, String senderEmail,
                       String toEmail, String subject, UUID leadId, UUID sourceActivityId) {
        try {
            if (sourceActivityId != null && repo.existsBySourceActivityId(sourceActivityId)) return;
            String label = (senderLabel == null || senderLabel.isBlank())
                    ? EmailSend.LABEL_SYSTEM : senderLabel.trim();
            String subj = subject == null ? null
                    : (subject.length() > 500 ? subject.substring(0, 500) : subject);
            repo.save(new EmailSend(category, label,
                    blankToNull(senderEmail), blankToNull(toEmail), subj, leadId,
                    null, sourceActivityId));
        } catch (Exception e) {
            log.warn("Could not record email send ({}): {}", category, e.getMessage());
        }
    }

    public record Bucket(String key, long count) {}
    public record EmailStatsView(long total, Integer days, String periodLabel,
                                 List<Bucket> bySender, List<Bucket> byCategory) {}

    /**
     * Aggregate email volume for the root admin. {@code days} null/&lt;=0 = all time.
     */
    @Transactional
    public EmailStatsView stats(Integer days) {
        ensureBackfill();
        Integer periodDays = (days == null || days <= 0) ? null : days;
        OffsetDateTime since = periodDays == null ? null : OffsetDateTime.now().minusDays(periodDays);
        String periodLabel = periodDays == null ? "All time"
                : periodDays == 1 ? "Last 24 hours / 1 day"
                : "Last " + periodDays + " days";

        long total = since == null ? repo.count() : repo.countByCreatedAtGreaterThanEqual(since);
        List<Object[]> senderRows = since == null
                ? repo.countBySenderLabelAll() : repo.countBySenderLabelSince(since);
        List<Object[]> catRows = since == null
                ? repo.countByCategoryAll() : repo.countByCategorySince(since);

        List<Bucket> bySender = toBuckets(senderRows);
        List<Bucket> byCategory = toBuckets(catRows).stream()
                .map(b -> new Bucket(categoryLabel(b.key()), b.count()))
                .toList();
        return new EmailStatsView(total, periodDays, periodLabel, bySender, byCategory);
    }

    /**
     * Import historical EMAIL activities into email_sends once per process (skips rows already
     * linked via source_activity_id so live LEAD rows are never double-counted).
     */
    @Transactional
    public void ensureBackfill() {
        if (backfillChecked) return;
        synchronized (this) {
            if (backfillChecked) return;
            try {
                // Skip work if we already ran a full backfill and nothing new to import is likely.
                if (repo.countByCategory(EmailSend.CAT_LEAD_BACKFILL) == 0) {
                    backfillFromActivities();
                }
            } catch (Exception e) {
                log.warn("Email send backfill skipped: {}", e.getMessage());
            } finally {
                backfillChecked = true;
            }
        }
    }

    private void backfillFromActivities() {
        List<Activity> emails = activities.findAll().stream()
                .filter(a -> "EMAIL".equals(a.getType()))
                .toList();
        if (emails.isEmpty()) return;

        Map<String, String> nameByEmail = new HashMap<>();
        for (User u : users.findAll()) {
            if (u.getEmail() != null) {
                nameByEmail.put(u.getEmail().toLowerCase(), displayName(u));
            }
        }

        List<EmailSend> batch = new ArrayList<>();
        for (Activity a : emails) {
            if (a.getId() != null && repo.existsBySourceActivityId(a.getId())) continue;
            String author = a.getAuthor();
            String label;
            if (author == null || author.isBlank()) {
                label = EmailSend.LABEL_SYSTEM;
            } else {
                label = nameByEmail.getOrDefault(author.trim().toLowerCase(), author.trim());
            }
            String subject = extractSubject(a.getBody());
            batch.add(new EmailSend(EmailSend.CAT_LEAD_BACKFILL, label, author,
                    null, subject, a.getLeadId(), a.getCreatedAt(), a.getId()));
        }
        if (batch.isEmpty()) return;
        repo.saveAll(batch);
        log.info("Backfilled {} historical lead emails into email_sends", batch.size());
    }

    private static String extractSubject(String body) {
        if (body == null || body.isBlank()) return null;
        // "Email to x@y · Subject\nbody…"
        int nl = body.indexOf('\n');
        String first = nl >= 0 ? body.substring(0, nl) : body;
        int sep = first.indexOf(" · ");
        if (sep >= 0 && sep + 3 < first.length()) {
            String s = first.substring(sep + 3).trim();
            return s.length() > 500 ? s.substring(0, 500) : s;
        }
        return first.length() > 500 ? first.substring(0, 500) : first;
    }

    private static List<Bucket> toBuckets(List<Object[]> rows) {
        List<Bucket> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new Bucket(String.valueOf(r[0]), ((Number) r[1]).longValue()));
        }
        return out;
    }

    private static String categoryLabel(String cat) {
        if (cat == null) return "Unknown";
        return switch (cat) {
            case EmailSend.CAT_LEAD, EmailSend.CAT_LEAD_BACKFILL -> "Lead emails";
            case EmailSend.CAT_PASSWORD_RESET -> "Password reset";
            case EmailSend.CAT_STAFF_INVITE -> "Staff invite";
            case EmailSend.CAT_REPLY_NOTIFY -> "Reply notifications";
            case EmailSend.CAT_TEST -> "Test emails";
            default -> cat;
        };
    }

    private static String displayName(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isBlank() ? u.getEmail() : n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
