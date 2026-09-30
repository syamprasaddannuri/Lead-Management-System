package com.leadmanagement.lms.email;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Super-admin utility to verify the SES/SMTP setup. */
@RestController
@RequestMapping("/api/admin")
public class MailController {

    private final EmailService email;
    private final EmailSendService emailSends;

    public MailController(EmailService email, EmailSendService emailSends) {
        this.email = email;
        this.emailSends = emailSends;
    }

    public record TestEmailRequest(@NotBlank @Email String to) {}

    @PostMapping("/test-email")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> test(@Valid @RequestBody TestEmailRequest req) {
        String subject = "Lead Management System SES test";
        boolean sent = email.send(req.to(), subject,
                "<p>If you can read this, Amazon SES is wired up correctly for the Lead Management System sales console. ✅</p>");
        if (sent) {
            emailSends.record(EmailSend.CAT_TEST, EmailSend.LABEL_TEST, null, req.to(), subject, null);
        }
        return Map.of("status", "send attempted", "to", req.to(),
                "note", "Check the inbox and the backend logs for the delivery result.");
    }
}
