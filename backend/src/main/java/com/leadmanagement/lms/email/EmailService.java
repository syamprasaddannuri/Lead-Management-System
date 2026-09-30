package com.leadmanagement.lms.email;

import com.leadmanagement.lms.common.ApiException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Sends email over SMTP (Amazon SES). */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String host;
    private final String fromEmail;
    private final String fromName;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.host:}") String host,
                        @Value("${mail.from-email}") String fromEmail,
                        @Value("${mail.from-name}") String fromName) {
        this.mailSender = mailSender;
        this.host = host;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }

    /**
     * Fire-and-forget: logs and skips on any problem (used for non-critical mail like staff invites).
     * @return true if the message was handed to SMTP successfully
     */
    public boolean send(String toEmail, String subject, String html) {
        try {
            sendOrThrow(toEmail, subject, html);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} not sent: {}", toEmail, e.getMessage());
            return false;
        }
    }

    /** Sends and throws on failure, so callers (e.g. emailing a lead) can surface the error. */
    public void sendOrThrow(String toEmail, String subject, String html) {
        sendOrThrow(toEmail, subject, html, null);
    }

    /**
     * Sends and throws on failure. When {@code replyTo} is set, replies route there instead of the
     * From address (used to tag lead emails so the reply lands back on the lead's timeline).
     */
    public void sendOrThrow(String toEmail, String subject, String html, String replyTo) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Email is not configured");
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setFrom(fromEmail, fromName);
            h.setTo(toEmail);
            // Show the brand name on Reply-To so the lead sees "Lead Management System", not the raw routing token.
            if (replyTo != null && !replyTo.isBlank()) h.setReplyTo(replyTo, fromName);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
            log.info("Email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Email delivery failed: " + e.getMessage());
        }
    }
}
