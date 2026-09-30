package com.leadmanagement.lms.email;

import com.leadmanagement.lms.identity.User;
import com.leadmanagement.lms.identity.UserRepository;
import com.leadmanagement.lms.lead.Activity;
import com.leadmanagement.lms.lead.ActivityRepository;
import com.leadmanagement.lms.lead.Lead;
import com.leadmanagement.lms.lead.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns an inbound lead reply (delivered by SendGrid Inbound Parse) into a timeline entry. */
@Service
public class InboundEmailService {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);
    private static final int MAX_BODY = 8000;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // Common markers where a reply's quoted history begins; we trim from the first hit.
    private static final Pattern QUOTE_START = Pattern.compile(
            "(?m)^(On .+ wrote:|-{2,}\\s*Original Message\\s*-{2,}|_{5,}|From:\\s.+|>{1,}.*)$");

    private final LeadRepository leads;
    private final ActivityRepository activities;
    private final UserRepository users;
    private final EmailService email;
    private final EmailSendService emailSends;
    private final ReplyToken replyToken;
    private final String frontendUrl;

    public InboundEmailService(LeadRepository leads, ActivityRepository activities, UserRepository users,
                               EmailService email, EmailSendService emailSends, ReplyToken replyToken,
                               @Value("${app.frontend-url:https://sales.example.com}") String frontendUrl) {
        this.leads = leads;
        this.activities = activities;
        this.users = users;
        this.email = email;
        this.emailSends = emailSends;
        this.replyToken = replyToken;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Logs an inbound reply on the matching lead's timeline.
     * @return true if it was matched and logged; false if the recipient carried no valid lead tag.
     */
    @Transactional
    public boolean ingest(String recipient, String fromHeader, String subject, String text, String html) {
        UUID leadId = replyToken.leadIdFrom(recipient);
        if (leadId == null) {
            log.warn("Inbound email ignored: no valid lead tag in recipient [{}]", recipient);
            return false;
        }
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null) {
            log.warn("Inbound email ignored: lead {} not found", leadId);
            return false;
        }
        String from = extractEmail(fromHeader);
        String body = cleanBody(text, html);
        String header = "Reply" + (subject != null && !subject.isBlank() ? " · " + subject.trim() : "");
        activities.save(new Activity(leadId, "EMAIL_IN", header + "\n" + body, from != null ? from : lead.getEmail()));
        lead.setUpdatedAt(OffsetDateTime.now());
        leads.save(lead);
        notifyOwner(lead, from, subject);
        log.info("Inbound reply logged for lead {} from {}", leadId, from);
        return true;
    }

    /** Best-effort heads-up to the lead's owner that a reply came in. Never blocks ingestion. */
    private void notifyOwner(Lead lead, String from, String subject) {
        try {
            if (lead.getOwnerId() == null || !email.isConfigured()) return;
            User owner = users.findById(lead.getOwnerId()).orElse(null);
            if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) return;
            String name = ((lead.getFirstName() == null ? "" : lead.getFirstName()) + " "
                    + (lead.getLastName() == null ? "" : lead.getLastName())).trim();
            if (name.isBlank()) name = from != null ? from : "your lead";
            String link = frontendUrl + "/leads/" + lead.getId();
            String html = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111\">"
                    + "<p>" + escape(name) + " replied"
                    + (subject != null && !subject.isBlank() ? " (" + escape(subject.trim()) + ")" : "") + ".</p>"
                    + "<p><a href=\"" + link + "\">Open the lead in the sales console</a></p></div>";
            String notifySubject = "New reply from " + name;
            if (email.send(owner.getEmail(), notifySubject, html)) {
                emailSends.record(EmailSend.CAT_REPLY_NOTIFY, EmailSend.LABEL_REPLY_NOTIFY,
                        null, owner.getEmail(), notifySubject, lead.getId());
            }
        } catch (Exception e) {
            log.warn("Could not notify owner of inbound reply: {}", e.getMessage());
        }
    }

    /** Prefer plain text; fall back to stripped HTML. Trim quoted history and cap length. */
    private static String cleanBody(String text, String html) {
        String raw = (text != null && !text.isBlank()) ? text
                : (html != null ? html.replaceAll("(?s)<[^>]+>", " ") : "");
        if (raw == null) raw = "";
        Matcher q = QUOTE_START.matcher(raw);
        if (q.find() && q.start() > 0) raw = raw.substring(0, q.start());
        raw = raw.replaceAll("[\\t ]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (raw.length() > MAX_BODY) raw = raw.substring(0, MAX_BODY) + "\n…(truncated)";
        return raw.isBlank() ? "(empty reply)" : raw;
    }

    private static String extractEmail(String header) {
        if (header == null) return null;
        Matcher m = EMAIL.matcher(header);
        return m.find() ? m.group().toLowerCase() : null;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
