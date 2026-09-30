package com.leadmanagement.lms.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-lead Reply-To tagging so an inbound reply can be matched back to its lead.
 * Outbound mail to a lead carries Reply-To: lead.&lt;uuidhex&gt;.&lt;sig&gt;@&lt;reply-domain&gt;.
 * When the lead replies, SendGrid Inbound Parse delivers it to that address and we
 * verify the signature before trusting the lead id (so the address can't be forged).
 */
@Component
public class ReplyToken {

    private static final Pattern TAG =
            Pattern.compile("lead\\.([0-9a-f]{32})\\.([0-9a-f]{16})", Pattern.CASE_INSENSITIVE);

    private final String domain;     // e.g. reply.example.com
    private final byte[] secret;

    public ReplyToken(@Value("${inbound.reply-domain:}") String domain,
                      @Value("${inbound.hmac-secret:}") String secret) {
        this.domain = domain == null ? "" : domain.trim();
        this.secret = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
    }

    /** Inbound matching only works once a reply domain and secret are configured. */
    public boolean isEnabled() {
        return !domain.isBlank() && secret.length > 0;
    }

    /** Reply-To address for a lead, or null when inbound replies aren't configured. */
    public String addressFor(UUID leadId) {
        if (!isEnabled()) return null;
        String hex = leadId.toString().replace("-", "");
        return "lead." + hex + "." + sign(hex) + "@" + domain;
    }

    /**
     * Extracts and verifies the lead id from an incoming recipient string (which may be a raw
     * address, a "Name &lt;addr&gt;" header, or SendGrid's envelope JSON). Returns null when the
     * string carries no valid, correctly-signed tag.
     */
    public UUID leadIdFrom(String recipient) {
        if (recipient == null || !isEnabled()) return null;
        Matcher m = TAG.matcher(recipient.toLowerCase());
        if (!m.find()) return null;
        String hex = m.group(1);
        String sig = m.group(2);
        if (!constantEquals(sig, sign(hex))) return null;
        try {
            return UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return null;
        }
    }

    /** First 8 bytes of HMAC-SHA256(secret, hex), as 16 lowercase hex chars. */
    private String sign(String hex) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(hex.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", out[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
