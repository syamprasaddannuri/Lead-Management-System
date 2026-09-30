package com.leadmanagement.lms.email;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Root-admin only: outbound email volume by sender and by category. */
@RestController
@RequestMapping("/api/admin")
public class EmailStatsController {

    private final EmailSendService emailSends;

    public EmailStatsController(EmailSendService emailSends) {
        this.emailSends = emailSends;
    }

    /**
     * Email send stats for SUPER_ADMIN.
     * Optional {@code days}: 1, 7, 30, 90, or omit / 0 for all time.
     */
    @GetMapping("/email-stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EmailSendService.EmailStatsView emailStats(@RequestParam(required = false) Integer days) {
        return emailSends.stats(days);
    }
}
