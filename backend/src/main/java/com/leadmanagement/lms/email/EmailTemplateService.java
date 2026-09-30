package com.leadmanagement.lms.email;

import com.leadmanagement.lms.common.ApiException;
import com.leadmanagement.lms.identity.OrgVisibilityService;
import com.leadmanagement.lms.identity.User;
import com.leadmanagement.lms.identity.VisibilityScope;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class EmailTemplateService {

    /**
     * Who can *see / use* a template created by owner C:
     * C, managers above C, reports under C, and C's peer-group peers.
     * System-seeded templates (created_by = system) are visible to everyone who can email.
     */
    private static final EnumSet<VisibilityScope> READ = VisibilityScope.TEAM_SPHERE();

    /**
     * Who can *edit / delete* a template: creator, people in their downward tree (managers
     * cleaning up report templates), or SUPER_ADMIN (ALL via service).
     */
    private static final EnumSet<VisibilityScope> WRITE = VisibilityScope.SELF_AND_DOWN();

    private final EmailTemplateRepository repo;
    private final OrgVisibilityService visibility;

    public EmailTemplateService(EmailTemplateRepository repo, OrgVisibilityService visibility) {
        this.repo = repo;
        this.visibility = visibility;
    }

    public record TemplateView(UUID id, String name, String subject, String body, String stage,
                               boolean active, String createdBy, OffsetDateTime createdAt,
                               boolean canEdit) {}

    @Transactional(readOnly = true)
    public List<TemplateView> listActive(User viewer) {
        OrgVisibilityService.Snapshot snap = visibility.snapshot();
        Set<UUID> owners = visibility.visibleOwnerIds(viewer, READ, snap);
        return repo.findByActiveTrueOrderByNameAsc().stream()
                .filter(t -> isReadable(viewer, t, owners, snap))
                .map(t -> toView(t, canWrite(viewer, t, snap)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateView> listAll(User viewer) {
        OrgVisibilityService.Snapshot snap = visibility.snapshot();
        Set<UUID> owners = visibility.visibleOwnerIds(viewer, READ, snap);
        return repo.findAllByOrderByNameAsc().stream()
                .filter(t -> isReadable(viewer, t, owners, snap))
                .map(t -> toView(t, canWrite(viewer, t, snap)))
                .toList();
    }

    @Transactional
    public TemplateView create(String name, String subject, String body, String stage, Boolean active, User actor) {
        validate(name, subject, body);
        EmailTemplate t = new EmailTemplate(name.trim(), subject.trim(), body.trim(),
                blankToNull(stage), active == null || active, actor.getEmail());
        t = repo.save(t);
        return toView(t, true);
    }

    @Transactional
    public TemplateView update(UUID id, String name, String subject, String body, String stage, Boolean active, User actor) {
        EmailTemplate t = repo.findById(id).orElseThrow(() -> ApiException.notFound("Template not found"));
        OrgVisibilityService.Snapshot snap = visibility.snapshot();
        if (!canWrite(actor, t, snap))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "You can only edit templates you created or that belong to people under you");
        validate(name, subject, body);
        t.setName(name.trim()); t.setSubject(subject.trim()); t.setBody(body.trim());
        t.setStage(blankToNull(stage));
        if (active != null) t.setActive(active);
        return toView(repo.save(t), true);
    }

    @Transactional
    public void delete(UUID id, User actor) {
        EmailTemplate t = repo.findById(id).orElseThrow(() -> ApiException.notFound("Template not found"));
        OrgVisibilityService.Snapshot snap = visibility.snapshot();
        if (!canWrite(actor, t, snap))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "You can only delete templates you created or that belong to people under you");
        // Protect system seeds unless super admin
        if (isSystem(t) && !OrgVisibilityService.isSuperAdmin(actor))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only a super admin can delete system templates");
        repo.delete(t);
    }

    private boolean isReadable(User viewer, EmailTemplate t, Set<UUID> ownerIds, OrgVisibilityService.Snapshot snap) {
        if (isSystem(t)) return true; // global starter templates
        Optional<User> owner = resolveOwner(t, snap);
        if (owner.isEmpty()) {
            return OrgVisibilityService.isSuperAdmin(viewer);
        }
        return ownerIds.contains(owner.get().getId());
    }

    private boolean canWrite(User viewer, EmailTemplate t, OrgVisibilityService.Snapshot snap) {
        if (OrgVisibilityService.isSuperAdmin(viewer)) return true;
        if (isSystem(t)) return false;
        Optional<User> owner = resolveOwner(t, snap);
        if (owner.isEmpty()) return false;
        return visibility.canSeeOwner(viewer, owner.get().getId(), WRITE, false, snap);
    }

    private Optional<User> resolveOwner(EmailTemplate t, OrgVisibilityService.Snapshot snap) {
        if (t.getCreatedBy() == null || t.getCreatedBy().isBlank()) return Optional.empty();
        String email = t.getCreatedBy().trim().toLowerCase();
        for (User u : snap.everyone) {
            if (u.getEmail() != null && u.getEmail().equalsIgnoreCase(email)) return Optional.of(u);
        }
        return Optional.empty();
    }

    private static boolean isSystem(EmailTemplate t) {
        return t.getCreatedBy() == null || "system".equalsIgnoreCase(t.getCreatedBy());
    }

    private static void validate(String name, String subject, String body) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Template name is required");
        if (subject == null || subject.isBlank()) throw ApiException.badRequest("Subject is required");
        if (body == null || body.isBlank()) throw ApiException.badRequest("Body is required");
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static TemplateView toView(EmailTemplate t, boolean canEdit) {
        return new TemplateView(t.getId(), t.getName(), t.getSubject(), t.getBody(), t.getStage(),
                t.isActive(), t.getCreatedBy(), t.getCreatedAt(), canEdit);
    }

    /** Seed a starter set of stage templates the first time the app boots. */
    @PostConstruct
    void seedDefaults() {
        if (repo.count() > 0) return;
        String sig = "\n\nBest,\nLead Management System Team";
        repo.save(new EmailTemplate("New — welcome / intro", "Welcome to Lead Management System, {firstName}",
                "Hi {firstName},\n\nThanks for your interest in {program} at Lead Management System. I would love to walk you through how the program works and help you get started.\n\nWhen is a good time for a quick call?" + sig, "NEW", true, "system"));
        repo.save(new EmailTemplate("Contacted — follow up", "Following up on {program}",
                "Hi {firstName},\n\nI tried reaching you about {program} but could not connect. I would still love to help you explore whether it is the right fit.\n\nCould you share a convenient time to talk?" + sig, "CONTACTED", true, "system"));
        repo.save(new EmailTemplate("Qualified — next steps", "Next steps for {program}",
                "Hi {firstName},\n\nGreat speaking with you. Here are the next steps to join {program}, along with the curriculum and what to expect.\n\nHappy to answer any questions." + sig, "QUALIFIED", true, "system"));
        repo.save(new EmailTemplate("Negotiation — enrollment offer", "Your enrollment details for {program}",
                "Hi {firstName},\n\nAs discussed, here are your enrollment details and options for {program}. Let me know if you would like to proceed and I will help with the next steps." + sig, "NEGOTIATION", true, "system"));
        repo.save(new EmailTemplate("Won — welcome aboard", "Welcome aboard, {firstName}!",
                "Hi {firstName},\n\nWelcome to {program}! We are excited to have you. Your onboarding details will follow shortly." + sig, "WON", true, "system"));
        repo.save(new EmailTemplate("Lost — stay in touch", "Staying in touch",
                "Hi {firstName},\n\nThanks for considering {program}. If anything changes or you would like to revisit it later, we would be glad to help." + sig, "LOST", true, "system"));
    }
}
