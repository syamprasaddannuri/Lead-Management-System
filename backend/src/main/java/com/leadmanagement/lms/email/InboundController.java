package com.leadmanagement.lms.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Receives lead replies from SendGrid Inbound Parse.
 * Configure the Parse webhook URL as: https://sales.example.com/api/inbound/sendgrid?key=&lt;INBOUND_WEBHOOK_SECRET&gt;
 * SendGrid POSTs the parsed message as multipart/form-data (to, from, subject, text, html, envelope, ...).
 */
@RestController
@RequestMapping("/api/inbound")
public class InboundController {

    private static final Logger log = LoggerFactory.getLogger(InboundController.class);

    private final InboundEmailService inbound;
    private final String webhookSecret;

    public InboundController(InboundEmailService inbound,
                             @Value("${inbound.webhook-secret:}") String webhookSecret) {
        this.inbound = inbound;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    @PostMapping("/sendgrid")
    public ResponseEntity<String> sendgrid(
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "html", required = false) String html,
            @RequestParam(value = "envelope", required = false) String envelope) {

        if (webhookSecret.isBlank() || key == null || !constantEquals(key, webhookSecret)) {
            log.warn("Inbound webhook rejected: bad or missing key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("forbidden");
        }
        // The lead tag may sit in the envelope JSON or the To header; let ReplyToken scan both.
        String recipient = (envelope == null ? "" : envelope) + " " + (to == null ? "" : to);
        boolean logged = inbound.ingest(recipient, from, subject, text, html);
        // Always 200 so SendGrid doesn't retry indefinitely on mail we can't match.
        return ResponseEntity.ok(logged ? "logged" : "ignored");
    }

    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
