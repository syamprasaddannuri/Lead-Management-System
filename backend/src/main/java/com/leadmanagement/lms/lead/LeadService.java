package com.leadmanagement.lms.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmanagement.lms.common.ApiException;
import com.leadmanagement.lms.email.EmailService;
import com.leadmanagement.lms.identity.Role;
import com.leadmanagement.lms.identity.User;
import com.leadmanagement.lms.identity.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LeadService {

    /** Ordered sales pipeline (D365-style business process flow). */
    public static final List<String> STAGES = List.of(
            "NEW", "CONTACTED", "QUALIFIED", "NURTURING", "NEGOTIATION", "WON", "LOST");
    private static final Set<String> SALES_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "SALES_MANAGER", "SALES_REP");
    /** Roles allowed to edit any field and approve change requests. */
    private static final Set<String> MANAGER_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "SALES_MANAGER");
    /** Lead fields a rep may fill / request changes to, with display labels. */
    private static final Map<String, String> EDITABLE_FIELDS = Map.of(
            "name", "Name", "email", "Email", "phone", "Phone", "source", "Source", "course", "Course");

    private final LeadRepository repo;
    private final ActivityRepository activities;
    private final UserRepository users;
    private final EmailService emailService;
    private final com.leadmanagement.lms.email.EmailSendService emailSends;
    private final com.leadmanagement.lms.email.ReplyToken replyToken;
    private final LeadChangeRequestRepository changeRequests;
    private final LeadFollowUpRepository followUps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LeadService(LeadRepository repo, ActivityRepository activities, UserRepository users,
                       EmailService emailService, com.leadmanagement.lms.email.EmailSendService emailSends,
                       com.leadmanagement.lms.email.ReplyToken replyToken,
                       LeadChangeRequestRepository changeRequests, LeadFollowUpRepository followUps) {
        this.repo = repo;
        this.activities = activities;
        this.users = users;
        this.emailService = emailService;
        this.emailSends = emailSends;
        this.replyToken = replyToken;
        this.changeRequests = changeRequests;
        this.followUps = followUps;
    }

    public record LeadView(UUID id, String firstName, String lastName, String email, String phone,
                           String source, String courseId, String courseName, String message, String extra,
                           String status, UUID ownerId, String ownerName,
                           UUID createdById, String createdByName, OffsetDateTime createdAt) {}
    public record ActivityView(UUID id, String type, String body, String author, OffsetDateTime createdAt) {}
    public record AssigneeView(UUID id, String name, String email) {}
    public record FollowUpView(UUID id, UUID leadId, String leadName, String leadPhone, String leadStatus,
                               String message, OffsetDateTime dueAt, String status,
                               String createdBy, String completedBy, OffsetDateTime completedAt,
                               OffsetDateTime createdAt) {}
    /** Generic page wrapper returned by paged list endpoints. */
    public record PageView<T>(List<T> content, int page, int size, long total, int totalPages) {}

    @Transactional
    public Lead capture(String name, String email, String phone, String program, String message) {
        if (phone == null || phone.isBlank()) throw ApiException.badRequest("Phone number is required");
        String first = name == null ? "" : name.trim();
        String last = "";
        int sp = first.indexOf(' ');
        if (sp > 0) { last = first.substring(sp + 1).trim(); first = first.substring(0, sp).trim(); }

        String p = program == null ? "" : program.trim().toLowerCase();
        String courseId = null, courseName = null, source;
        switch (p) {
            case "agentic-ai" -> { courseId = "agentic-ai"; courseName = "Agentic AI Engineer"; source = "Website - Agentic AI"; }
            case "ai-ml" -> { courseId = "ai-ml"; courseName = "Applied AI & ML"; source = "Website - Applied AI & ML"; }
            default -> source = "Website - Need Help";
        }
        Lead lead = new Lead();
        lead.setFirstName(first); lead.setLastName(last);
        lead.setEmail(email == null ? null : email.trim());
        lead.setPhone(phone.trim());
        lead.setPhoneNormalized(normalizePhone(phone));
        lead.setSource(source); lead.setCourseId(courseId); lead.setCourseName(courseName);
        lead.setMessage(message); lead.setStatus("NEW");
        lead = repo.save(lead);
        activities.save(new Activity(lead.getId(), "CREATED", "Lead captured from " + source, null));
        return lead;
    }

    /** Add a single lead manually (internal staff). Owner + creator default to the actor. Name + phone required; email optional. */
    @Transactional
    public LeadView createSingle(String name, String email, String phone, String program,
                                 String source, String message, String actor) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Name is required");
        if (phone == null || phone.isBlank()) throw ApiException.badRequest("Phone number is required");
        String ph = phone.trim();
        String norm = normalizePhone(ph);
        String em = email == null ? "" : email.trim();
        var dupPhone = repo.findFirstByPhoneNormalized(norm);
        if (dupPhone.isPresent())
            throw ApiException.conflict("This lead is already in the system", Map.of("leadId", dupPhone.get().getId().toString()));
        if (!em.isEmpty()) {
            var dupEmail = repo.findFirstByEmailIgnoreCase(em);
            if (dupEmail.isPresent())
                throw ApiException.conflict("A lead with this email is already in the system", Map.of("leadId", dupEmail.get().getId().toString()));
        }
        String first = name.trim(), last = "";
        int sp = first.indexOf(' ');
        if (sp > 0) { last = first.substring(sp + 1).trim(); first = first.substring(0, sp).trim(); }
        String[] c = course(program);
        UUID actorId = actorId(actor);

        Lead lead = new Lead();
        lead.setFirstName(first); lead.setLastName(last);
        lead.setEmail(em.isEmpty() ? null : em);
        lead.setPhone(ph);
        lead.setPhoneNormalized(norm);
        lead.setSource(source != null && !source.isBlank() ? source.trim() : "Added manually");
        lead.setCourseId(c[0]); lead.setCourseName(c[1]);
        lead.setMessage(message == null || message.isBlank() ? null : message.trim());
        lead.setStatus("NEW");
        lead.setOwnerId(actorId);
        lead.setCreatedById(actorId);
        lead = repo.save(lead);
        activities.save(new Activity(lead.getId(), "CREATED", "Lead added manually", actor));
        return toView(lead, userNames());
    }

    public record ChangeRequestView(UUID id, UUID leadId, String leadName, String field, String fieldLabel,
                                    String currentValue, String requestedValue, String status,
                                    String requestedBy, String decidedBy, String note,
                                    OffsetDateTime createdAt, OffsetDateTime decidedAt) {}

    /**
     * Edit lead contact fields. Managers and above may change anything; a sales rep may only fill
     * fields that are currently blank. Attempting to change a non-blank field as a rep is rejected
     * (they should raise a change request instead).
     */
    @Transactional
    public LeadView editDetails(UUID id, Map<String, String> changes, String actor) {
        Lead lead = get(id);
        boolean manager = isManager(actor);
        boolean any = false;
        for (Map.Entry<String, String> e : changes.entrySet()) {
            String field = e.getKey();
            if (!EDITABLE_FIELDS.containsKey(field)) continue;
            String newVal = e.getValue() == null ? "" : e.getValue().trim();
            String cur = currentValue(lead, field);
            if (Objects.equals(blankToNull(newVal), blankToNull(cur))) continue; // no change
            if (!manager && cur != null && !cur.isBlank()) {
                throw ApiException.badRequest("Sales reps can only fill blank fields. Raise a change request to edit "
                        + EDITABLE_FIELDS.get(field) + ".");
            }
            applyField(lead, field, newVal, id);
            activities.save(new Activity(id, "NOTE",
                    EDITABLE_FIELDS.get(field) + (cur == null || cur.isBlank() ? " set to " : " updated to ")
                            + (newVal.isBlank() ? "(blank)" : newVal), actor));
            any = true;
        }
        if (any) repo.save(lead);
        return toView(lead, userNames());
    }

    /** A rep raises a request to change a non-blank field; a manager approves it later. */
    @Transactional
    public ChangeRequestView raiseChangeRequest(UUID id, String field, String requestedValue, String note, String actor) {
        Lead lead = get(id);
        if (!EDITABLE_FIELDS.containsKey(field)) throw ApiException.badRequest("Field cannot be changed: " + field);
        String requested = requestedValue == null ? "" : requestedValue.trim();
        if (requested.isBlank()) throw ApiException.badRequest("Please provide the new value you want.");
        String cur = currentValue(lead, field);
        LeadChangeRequest cr = changeRequests.save(
                new LeadChangeRequest(id, field, cur, requested, actor, note == null || note.isBlank() ? null : note.trim()));
        activities.save(new Activity(id, "NOTE",
                "Change requested — " + EDITABLE_FIELDS.get(field) + ": \"" + (cur == null ? "" : cur) + "\" → \"" + requested + "\"", actor));
        return toRequestView(cr, lead);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestView> changeRequestsForLead(UUID id) {
        Lead lead = get(id);
        return changeRequests.findByLeadIdOrderByCreatedAtDesc(id).stream().map(r -> toRequestView(r, lead)).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestView> pendingChangeRequests() {
        return changeRequests.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                .map(r -> toRequestView(r, repo.findById(r.getLeadId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public long pendingChangeRequestCount() { return changeRequests.countByStatus("PENDING"); }

    /** Approve (applies the value) or reject a pending change request. Caller must be a manager+. */
    @Transactional
    public ChangeRequestView decideChangeRequest(UUID requestId, boolean approve, String note, String actor) {
        LeadChangeRequest cr = changeRequests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Change request not found"));
        if (!"PENDING".equals(cr.getStatus())) throw ApiException.badRequest("This request was already decided.");
        Lead lead = get(cr.getLeadId());
        if (approve) {
            applyField(lead, cr.getField(), cr.getRequestedValue(), lead.getId());
            repo.save(lead);
            cr.setStatus("APPROVED");
            activities.save(new Activity(lead.getId(), "NOTE",
                    EDITABLE_FIELDS.get(cr.getField()) + " changed to " + cr.getRequestedValue()
                            + " (approved request from " + cr.getRequestedBy() + ")", actor));
        } else {
            cr.setStatus("REJECTED");
            activities.save(new Activity(lead.getId(), "NOTE",
                    "Change request rejected — " + EDITABLE_FIELDS.get(cr.getField())
                            + (note == null || note.isBlank() ? "" : ": " + note.trim()), actor));
        }
        if (note != null && !note.isBlank()) cr.setNote(note.trim());
        cr.setDecidedBy(actor);
        cr.setDecidedAt(OffsetDateTime.now());
        changeRequests.save(cr);
        return toRequestView(cr, lead);
    }

    @Transactional(readOnly = true)
    public List<LeadView> listAll() {
        Map<UUID, String> names = userNames();
        return repo.findAllByOrderByCreatedAtDesc().stream().map(l -> toView(l, names)).toList();
    }

    @Transactional(readOnly = true)
    public List<LeadView> listOwned(UUID ownerId) {
        Map<UUID, String> names = userNames();
        return repo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(l -> toView(l, names)).toList();
    }

    @Transactional(readOnly = true)
    public PageView<LeadView> listAllPaged(Pageable pageable) {
        Map<UUID, String> names = userNames();
        Page<Lead> p = repo.findAll(pageable);
        List<LeadView> content = p.getContent().stream().map(l -> toView(l, names)).toList();
        return new PageView<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageView<LeadView> listOwnedPaged(UUID ownerId, Pageable pageable) {
        Map<UUID, String> names = userNames();
        Page<Lead> p = repo.findByOwnerId(ownerId, pageable);
        List<LeadView> content = p.getContent().stream().map(l -> toView(l, names)).toList();
        return new PageView<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** Leads owned by anyone in the given reporting-subtree user set. */
    @Transactional(readOnly = true)
    public List<LeadView> listSubtree(Collection<UUID> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) return List.of();
        Map<UUID, String> names = userNames();
        return repo.findByOwnerIdInOrderByCreatedAtDesc(ownerIds).stream().map(l -> toView(l, names)).toList();
    }

    @Transactional(readOnly = true)
    public PageView<LeadView> listSubtreePaged(Collection<UUID> ownerIds, Pageable pageable) {
        Map<UUID, String> names = userNames();
        if (ownerIds == null || ownerIds.isEmpty())
            return new PageView<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        Page<Lead> p = repo.findByOwnerIdIn(ownerIds, pageable);
        List<LeadView> content = p.getContent().stream().map(l -> toView(l, names)).toList();
        return new PageView<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /**
     * Paged + searched + filtered leads, scoped to the caller's branch (scope null = all).
     * Filtering, search and sort all run in the database, not in memory.
     */
    @Transactional(readOnly = true)
    public PageView<LeadView> searchPaged(Collection<UUID> scope, String q, String status,
                                          String ownerId, Pageable pageable) {
        Map<UUID, String> names = userNames();
        Page<Lead> p = repo.findAll(leadSpec(scope, q, status, ownerId), pageable);
        List<LeadView> content = p.getContent().stream().map(l -> toView(l, names)).toList();
        return new PageView<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** Same search/status/owner filtering as {@link #searchPaged}, as a reusable Specification (also used to assign-by-filter). */
    private static Specification<Lead> leadSpec(Collection<UUID> scope, String q, String status, String ownerId) {
        final String search = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();
        final String digits = search == null ? null : search.replaceAll("\\D", "");
        final String stage = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();

        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (scope != null) ps.add(root.get("ownerId").in(scope));
            if (stage != null) ps.add(cb.equal(root.get("status"), stage));
            if (ownerId != null && !ownerId.isBlank()) {
                if (ownerId.equalsIgnoreCase("unassigned")) ps.add(cb.isNull(root.get("ownerId")));
                else ps.add(cb.equal(root.get("ownerId"), UUID.fromString(ownerId.trim())));
            }
            if (search != null) {
                String like = "%" + search + "%";
                List<jakarta.persistence.criteria.Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(cb.coalesce(root.get("firstName"), "")), like));
                or.add(cb.like(cb.lower(cb.coalesce(root.get("lastName"), "")), like));
                or.add(cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like));
                or.add(cb.like(cb.coalesce(root.get("phone"), ""), "%" + q.trim() + "%"));
                if (digits != null && !digits.isEmpty())
                    or.add(cb.like(cb.coalesce(root.get("phoneNormalized"), ""), "%" + digits + "%"));
                ps.add(cb.or(or.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public LeadView detail(UUID id) {
        return toView(get(id), userNames());
    }

    @Transactional
    public LeadView update(UUID id, String status, String ownerIdRaw, String lostReason, String program, String actor) {
        Lead lead = get(id);

        if (program != null) { // present (may be "" to clear the course)
            String pp = program.trim().toLowerCase();
            String cid = null, cname = null;
            if (pp.equals("agentic-ai")) { cid = "agentic-ai"; cname = "Agentic AI Engineer"; }
            else if (pp.equals("ai-ml")) { cid = "ai-ml"; cname = "Applied AI & ML"; }
            else if (!pp.isEmpty()) { cname = program.trim(); }
            if (!Objects.equals(cname, lead.getCourseName())) {
                lead.setCourseId(cid);
                lead.setCourseName(cname);
                activities.save(new Activity(id, "NOTE", cname == null ? "Course cleared" : "Course set to " + cname, actor));
            }
        }

        if (status != null && !status.isBlank() && !status.equals(lead.getStatus())) {
            String s = status.trim().toUpperCase();
            if (!STAGES.contains(s)) throw ApiException.badRequest("Unknown stage: " + s);
            String old = lead.getStatus();
            lead.setStatus(s);
            activities.save(new Activity(id, "STAGE_CHANGE", "Stage: " + old + " → " + s, actor));
            if ("LOST".equals(s) && lostReason != null && !lostReason.isBlank()) {
                activities.save(new Activity(id, "NOTE", "Lost reason: " + lostReason.trim(), actor));
            }
        }

        if (ownerIdRaw != null) { // present (may be "" to unassign)
            UUID newOwner = ownerIdRaw.isBlank() ? null : UUID.fromString(ownerIdRaw.trim());
            if (!Objects.equals(newOwner, lead.getOwnerId())) {
                lead.setOwnerId(newOwner);
                String who = newOwner == null ? "Unassigned"
                        : "Assigned to " + userNames().getOrDefault(newOwner, newOwner.toString());
                activities.save(new Activity(id, "ASSIGNMENT", who, actor));
            }
        }
        repo.save(lead);
        return toView(lead, userNames());
    }

    /** Assign (or unassign with a blank ownerId) many leads at once. Returns how many changed. */
    @Transactional
    public int assignBulk(List<UUID> leadIds, String ownerIdRaw, String actor) {
        if (leadIds == null || leadIds.isEmpty()) return 0;
        UUID owner = (ownerIdRaw == null || ownerIdRaw.isBlank()) ? null : UUID.fromString(ownerIdRaw.trim());
        String who = owner == null ? "Unassigned"
                : "Assigned to " + userNames().getOrDefault(owner, owner.toString());
        int changed = 0;
        for (UUID id : leadIds) {
            Lead l = repo.findById(id).orElse(null);
            if (l == null || Objects.equals(owner, l.getOwnerId())) continue;
            l.setOwnerId(owner);
            repo.save(l);
            activities.save(new Activity(id, "ASSIGNMENT", who, actor));
            changed++;
        }
        return changed;
    }

    /**
     * Reassign (or unassign) every lead matching a search/filter, not just a loaded page of IDs.
     * Same per-lead audit trail as {@link #assignBulk}. Scope is the caller's visibility (null = all).
     */
    @Transactional
    public int assignByFilter(Collection<UUID> scope, String q, String status, String ownerId,
                              String newOwnerIdRaw, String actor) {
        UUID newOwner = (newOwnerIdRaw == null || newOwnerIdRaw.isBlank()) ? null : UUID.fromString(newOwnerIdRaw.trim());
        String who = newOwner == null ? "Unassigned"
                : "Assigned to " + userNames().getOrDefault(newOwner, newOwner.toString());
        int changed = 0;
        for (Lead l : repo.findAll(leadSpec(scope, q, status, ownerId))) {
            if (Objects.equals(newOwner, l.getOwnerId())) continue;
            l.setOwnerId(newOwner);
            repo.save(l);
            activities.save(new Activity(l.getId(), "ASSIGNMENT", who, actor));
            changed++;
        }
        return changed;
    }

    @Transactional
    public ActivityView addNote(UUID id, String body, String actor) {
        get(id); // ensure exists
        if (body == null || body.isBlank()) throw ApiException.badRequest("Note cannot be empty");
        Activity a = activities.save(new Activity(id, "NOTE", body.trim(), actor));
        return toActivity(a);
    }

    /** Email the lead directly and log it on the timeline. */
    @Transactional
    public ActivityView emailLead(UUID id, String subject, String body, String actor) {
        Lead lead = get(id);
        if (lead.getEmail() == null || lead.getEmail().isBlank()) throw ApiException.badRequest("This lead has no email address");
        if (subject == null || subject.isBlank()) throw ApiException.badRequest("Subject is required");
        if (body == null || body.isBlank()) throw ApiException.badRequest("Message is required");
        String html = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111\">"
                + body.trim().replace("\n", "<br>") + "</div>";
        // Tag the Reply-To so the lead's reply routes back to this lead's timeline (no-op if inbound isn't configured).
        emailService.sendOrThrow(lead.getEmail(), subject.trim(), html, replyToken.addressFor(lead.getId()));
        Activity a = activities.save(new Activity(id, "EMAIL",
                "Email to " + lead.getEmail() + " · " + subject.trim() + "\n" + body.trim(), actor));
        String senderName = users.findByEmailIgnoreCase(actor == null ? "" : actor)
                .map(LeadService::displayName)
                .orElse(actor == null || actor.isBlank() ? "Unknown" : actor);
        emailSends.record(com.leadmanagement.lms.email.EmailSend.CAT_LEAD, senderName, actor,
                lead.getEmail(), subject.trim(), lead.getId(), a.getId());
        return toActivity(a);
    }

    @Transactional(readOnly = true)
    public List<ActivityView> activities(UUID id) {
        return activities.findByLeadIdOrderByCreatedAtDesc(id).stream().map(LeadService::toActivity).toList();
    }

    // ── Follow-ups ──────────────────────────────────────────────────────────

    /** Schedule a follow-up on a lead; logged on the activity timeline. */
    @Transactional
    public FollowUpView createFollowUp(UUID leadId, String message, OffsetDateTime dueAt, String actor) {
        Lead lead = get(leadId);
        if (message == null || message.isBlank()) throw ApiException.badRequest("Follow-up message is required");
        if (dueAt == null) throw ApiException.badRequest("Follow-up time is required");
        LeadFollowUp f = followUps.save(new LeadFollowUp(leadId, message.trim(), dueAt, actor));
        activities.save(new Activity(leadId, "FOLLOW_UP",
                "Follow-up scheduled for " + dueAt + " — " + message.trim(), actor));
        return toFollowUpView(f, lead);
    }

    @Transactional(readOnly = true)
    public List<FollowUpView> followUpsForLead(UUID leadId) {
        Lead lead = get(leadId);
        return followUps.findByLeadIdOrderByDueAtAsc(leadId).stream()
                .map(f -> toFollowUpView(f, lead)).toList();
    }

    /**
     * Pending follow-ups for the dashboard, scoped to leads the caller can see.
     * {@code ownerScope} null = all leads (super admin / marketing).
     * Sorted by due time ascending (overdue first).
     */
    @Transactional(readOnly = true)
    public List<FollowUpView> pendingFollowUps(Collection<UUID> ownerScope) {
        List<LeadFollowUp> list;
        if (ownerScope == null) {
            list = followUps.findByStatusOrderByDueAtAsc("PENDING");
        } else if (ownerScope.isEmpty()) {
            return List.of();
        } else {
            list = followUps.findPendingForOwners(ownerScope);
        }
        Map<UUID, Lead> leadCache = new HashMap<>();
        return list.stream().map(f -> {
            Lead lead = leadCache.computeIfAbsent(f.getLeadId(), id -> repo.findById(id).orElse(null));
            return toFollowUpView(f, lead);
        }).toList();
    }

    /** Mark follow-up done; drops off dashboard; timeline note. */
    @Transactional
    public FollowUpView completeFollowUp(UUID followUpId, String actor) {
        LeadFollowUp f = followUps.findById(followUpId)
                .orElseThrow(() -> ApiException.notFound("Follow-up not found"));
        if (!"PENDING".equals(f.getStatus())) throw ApiException.badRequest("This follow-up is already completed");
        f.setStatus("DONE");
        f.setCompletedBy(actor);
        f.setCompletedAt(OffsetDateTime.now());
        followUps.save(f);
        activities.save(new Activity(f.getLeadId(), "FOLLOW_UP",
                "Follow-up completed — " + f.getMessage(), actor));
        Lead lead = repo.findById(f.getLeadId()).orElse(null);
        return toFollowUpView(f, lead);
    }

    private FollowUpView toFollowUpView(LeadFollowUp f, Lead lead) {
        String name = lead == null ? null :
                (((lead.getFirstName() == null ? "" : lead.getFirstName()) + " " +
                        (lead.getLastName() == null ? "" : lead.getLastName())).trim());
        if (name != null && name.isBlank()) name = lead.getPhone();
        return new FollowUpView(f.getId(), f.getLeadId(),
                name == null || name.isBlank() ? "Lead" : name,
                lead == null ? null : lead.getPhone(),
                lead == null ? null : lead.getStatus(),
                f.getMessage(), f.getDueAt(), f.getStatus(),
                f.getCreatedBy(), f.getCompletedBy(), f.getCompletedAt(), f.getCreatedAt());
    }

    /** Assignable staff. allowedIds null = everyone (super admin); else scoped to the caller's branch.
     *  Only active users — deactivated staff are omitted to reduce noise. */
    @Transactional(readOnly = true)
    public List<AssigneeView> assignees(Collection<UUID> allowedIds) {
        List<AssigneeView> out = new ArrayList<>();
        for (User u : users.findAllByOrderByCreatedAtDesc()) {
            if (!u.isActive()) continue;
            if (allowedIds != null && !allowedIds.contains(u.getId())) continue;
            boolean sales = u.getRoles().stream().map(Role::getName).anyMatch(SALES_ROLES::contains);
            if (sales) out.add(new AssigneeView(u.getId(), displayName(u), u.getEmail()));
        }
        return out;
    }

    public record StatsView(long total, Map<String, Long> byStatus) {}

    /** Fast lead counts for the dashboard (SQL group-by, no row hydration). Scoped to the caller's branch. */
    @Transactional(readOnly = true)
    public StatsView stats(Collection<UUID> ownerIds) {
        List<Object[]> rows;
        if (ownerIds == null) rows = repo.countByStatusAll();
        else if (ownerIds.isEmpty()) rows = List.of();
        else rows = repo.countByStatusForOwners(ownerIds);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] r : rows) {
            long c = ((Number) r[1]).longValue();
            byStatus.put((String) r[0], c);
            total += c;
        }
        return new StatsView(total, byStatus);
    }

    public record Bucket(String key, long count) {}
    public record ReportView(long total, long open, long won, long lost,
                             Integer days, String periodLabel,
                             double conversionRate, List<Bucket> byStatus, List<Bucket> bySource,
                             List<Bucket> byProgram, List<Bucket> byOwner) {}

    /**
     * Analytics. ownerIds null = all leads (super admin); else scoped to the caller's branch.
     * {@code days} null/&lt;=0 = all time; otherwise only leads created within the last N days.
     */
    @Transactional(readOnly = true)
    public ReportView report(Collection<UUID> ownerIds, Integer days) {
        List<Lead> all = ownerIds == null ? repo.findAll()
                : (ownerIds.isEmpty() ? List.of() : repo.findByOwnerIdInOrderByCreatedAtDesc(ownerIds));
        Map<UUID, String> names = userNames();
        Map<String, Long> status = new LinkedHashMap<>(), source = new HashMap<>(),
                program = new HashMap<>(), owner = new HashMap<>();
        long won = 0, lost = 0;
        OffsetDateTime now = OffsetDateTime.now();
        Integer periodDays = (days == null || days <= 0) ? null : days;
        OffsetDateTime since = periodDays == null ? null : now.minusDays(periodDays);
        String periodLabel = periodDays == null ? "All time"
                : periodDays == 1 ? "Last 24 hours / 1 day"
                : "Last " + periodDays + " days";

        long total = 0;
        for (Lead l : all) {
            if (since != null) {
                if (l.getCreatedAt() == null || l.getCreatedAt().isBefore(since)) continue;
            }
            total++;
            status.merge(l.getStatus(), 1L, Long::sum);
            source.merge(l.getSource() == null ? "Unknown" : l.getSource(), 1L, Long::sum);
            program.merge(l.getCourseName() == null ? "Not specified" : l.getCourseName(), 1L, Long::sum);
            owner.merge(l.getOwnerId() == null ? "Unassigned" : names.getOrDefault(l.getOwnerId(), "Unknown"), 1L, Long::sum);
            if ("WON".equals(l.getStatus())) won++;
            if ("LOST".equals(l.getStatus())) lost++;
        }
        long open = total - won - lost;
        double conv = total == 0 ? 0 : (double) won / total;
        // Ensure every pipeline stage appears (even 0) so UI can show Contacted / Won etc. clearly
        List<Bucket> byStatus = STAGES.stream().map(s -> new Bucket(s, status.getOrDefault(s, 0L))).toList();
        return new ReportView(total, open, won, lost, periodDays, periodLabel, conv,
                byStatus, sortDesc(source), sortDesc(program), sortDesc(owner));
    }

    private String toJson(Map<String, String> m) {
        if (m == null || m.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(m); } catch (Exception e) { return null; }
    }

    private static List<Bucket> sortDesc(Map<String, Long> m) {
        return m.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> new Bucket(e.getKey(), e.getValue())).toList();
    }

    public record DupCheckResult(List<String> phones, List<String> emails) {}

    /** Return which of the given phones / emails already exist in the CRM (for chunked preview checks). */
    @Transactional(readOnly = true)
    public DupCheckResult checkDuplicates(List<String> phones, List<String> emails) {
        List<String> normPhones = (phones == null ? List.<String>of() : phones).stream()
                .map(LeadService::normalizePhone).filter(Objects::nonNull).distinct().toList();
        List<String> normEmails = (emails == null ? List.<String>of() : emails).stream()
                .filter(Objects::nonNull).map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).distinct().toList();
        List<String> existingPhones = normPhones.isEmpty() ? List.of() : repo.findPhonesNormalizedIn(normPhones);
        List<String> existingEmails = normEmails.isEmpty() ? List.of() : repo.findEmailsLowerIn(normEmails);
        return new DupCheckResult(existingPhones, existingEmails);
    }

    public record BulkRow(String name, String email, String phone, String program, String source, Map<String, String> extra) {}
    public record BulkRowResult(int row, String name, String email, String phone, String courseName,
                                String source, String status, String message) {}
    public record BulkResult(int total, int toImport, int duplicates, int invalid, int created, List<BulkRowResult> rows) {}

    /** Validate uploaded rows; with commit=false this is a preview (nothing saved). */
    @Transactional
    public BulkResult bulk(List<BulkRow> rows, boolean commit, String actor) {
        UUID actorId = actorId(actor);
        Set<String> phones = new HashSet<>();
        for (String ph : repo.findAllPhonesNormalized()) if (ph != null && !ph.isBlank()) phones.add(ph);
        Set<String> emails = new HashSet<>();
        for (String em : repo.findAllEmailsLower()) if (em != null && !em.isBlank()) emails.add(em.trim());
        Set<String> seenPhones = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<BulkRowResult> results = new ArrayList<>();
        List<Lead> toSave = new ArrayList<>();
        int idx = 0;
        for (BulkRow r : rows == null ? List.<BulkRow>of() : rows) {
            idx++;
            String name = r.name() == null ? "" : r.name().trim();
            String email = r.email() == null ? "" : r.email().trim();
            String phone = r.phone() == null ? "" : r.phone().trim();
            String phoneNorm = normalizePhone(phone);
            String p = r.program() == null ? "" : r.program().trim().toLowerCase();

            String courseId = null, courseName = null, derivedSource;
            switch (p) {
                case "agentic-ai" -> { courseId = "agentic-ai"; courseName = "Agentic AI Engineer"; derivedSource = "Bulk - Agentic AI"; }
                case "ai-ml" -> { courseId = "ai-ml"; courseName = "Applied AI & ML"; derivedSource = "Bulk - Applied AI & ML"; }
                default -> { if (!p.isEmpty()) courseName = r.program().trim(); derivedSource = "Bulk import"; }
            }
            String source = (r.source() != null && !r.source().isBlank()) ? r.source().trim() : derivedSource;

            String status, message = "";
            String emailLower = email.toLowerCase();
            boolean hasEmail = !emailLower.isEmpty();
            if (name.isEmpty()) { status = "INVALID"; message = "Name is required"; }
            else if (phone.isEmpty()) { status = "INVALID"; message = "Phone is required"; }
            else if (hasEmail && !(email.contains("@") && email.contains("."))) { status = "INVALID"; message = "Invalid email"; }
            else if (phones.contains(phoneNorm) || (hasEmail && emails.contains(emailLower))) { status = "DUPLICATE"; message = "Already in CRM"; }
            else if (seenPhones.contains(phoneNorm) || (hasEmail && seenEmails.contains(emailLower))) { status = "DUPLICATE"; message = "Repeated in file"; }
            else {
                status = "NEW";
                seenPhones.add(phoneNorm);
                if (hasEmail) seenEmails.add(emailLower);
                if (commit) {
                    Lead lead = new Lead();
                    String first = name, last = "";
                    int sp = name.indexOf(' ');
                    if (sp > 0) { last = name.substring(sp + 1).trim(); first = name.substring(0, sp).trim(); }
                    lead.setFirstName(first);
                    lead.setLastName(last);
                    lead.setEmail(hasEmail ? email : null);
                    lead.setPhone(phone);
                    lead.setPhoneNormalized(phoneNorm);
                    lead.setSource(source);
                    lead.setCourseId(courseId);
                    lead.setCourseName(courseName);
                    lead.setExtra(toJson(r.extra()));
                    lead.setStatus("NEW");
                    lead.setOwnerId(actorId);
                    lead.setCreatedById(actorId);
                    toSave.add(lead);
                }
            }
            results.add(new BulkRowResult(idx, name, email, phone, courseName, source, status, message));
        }
        int created = 0;
        if (commit && !toSave.isEmpty()) {
            List<Lead> saved = repo.saveAll(toSave);
            List<Activity> acts = new ArrayList<>(saved.size());
            for (Lead l : saved) acts.add(new Activity(l.getId(), "CREATED", "Lead added via bulk import", actor));
            activities.saveAll(acts);
            created = saved.size();
        }
        int toImport = (int) results.stream().filter(x -> x.status().equals("NEW")).count();
        int dup = (int) results.stream().filter(x -> x.status().equals("DUPLICATE")).count();
        int inv = (int) results.stream().filter(x -> x.status().equals("INVALID")).count();
        return new BulkResult(results.size(), toImport, dup, inv, created, results);
    }

    @Transactional
    public void delete(UUID id) {
        get(id); // ensure exists
        activities.deleteByLeadId(id);
        changeRequests.deleteByLeadId(id);
        followUps.deleteByLeadId(id);
        repo.deleteById(id);
    }

    private Lead get(UUID id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Lead not found"));
    }

    /** Resolve the acting user's id from their email (null if not found). */
    private UUID actorId(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) return null;
        return users.findByEmailIgnoreCase(actorEmail.trim()).map(User::getId).orElse(null);
    }

    /** True when the actor is a sales manager, admin, or super admin. */
    private boolean isManager(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) return false;
        return users.findByEmailIgnoreCase(actorEmail.trim())
                .map(u -> u.getRoles().stream().map(Role::getName).anyMatch(MANAGER_ROLES::contains))
                .orElse(false);
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static String fullName(Lead l) {
        return ((l.getFirstName() == null ? "" : l.getFirstName()) + " "
                + (l.getLastName() == null ? "" : l.getLastName())).trim();
    }

    /** Read the current value of an editable field. */
    private static String currentValue(Lead l, String field) {
        return switch (field) {
            case "name" -> fullName(l);
            case "email" -> l.getEmail();
            case "phone" -> l.getPhone();
            case "source" -> l.getSource();
            case "course" -> l.getCourseName();
            default -> null;
        };
    }

    /** Apply a new value to an editable field, validating required/unique constraints. */
    private void applyField(Lead l, String field, String rawValue, UUID selfId) {
        String value = rawValue == null ? "" : rawValue.trim();
        switch (field) {
            case "name" -> {
                if (value.isBlank()) throw ApiException.badRequest("Name cannot be blank");
                String first = value, last = "";
                int sp = value.indexOf(' ');
                if (sp > 0) { last = value.substring(sp + 1).trim(); first = value.substring(0, sp).trim(); }
                l.setFirstName(first); l.setLastName(last);
            }
            case "email" -> {
                if (!value.isBlank()) {
                    if (!(value.contains("@") && value.contains("."))) throw ApiException.badRequest("Invalid email");
                    assertUnique("email", value, selfId);
                }
                l.setEmail(value.isBlank() ? null : value);
            }
            case "phone" -> {
                if (value.isBlank()) throw ApiException.badRequest("Phone cannot be blank");
                assertUnique("phone", value, selfId);
                l.setPhone(value);
                l.setPhoneNormalized(normalizePhone(value));
            }
            case "source" -> l.setSource(value.isBlank() ? null : value);
            case "course" -> {
                String[] c = course(value);
                l.setCourseId(c[0]); l.setCourseName(c[1]);
            }
            default -> throw ApiException.badRequest("Field cannot be changed: " + field);
        }
    }

    private void assertUnique(String field, String value, UUID selfId) {
        for (Lead other : repo.findAll()) {
            if (other.getId().equals(selfId)) continue;
            boolean clash = field.equals("email")
                    ? other.getEmail() != null && other.getEmail().trim().equalsIgnoreCase(value.trim())
                    : other.getPhoneNormalized() != null && other.getPhoneNormalized().equals(normalizePhone(value));
            if (clash) throw ApiException.conflict("Another lead already has this " + field);
        }
    }

    private static ChangeRequestView toRequestView(LeadChangeRequest r, Lead lead) {
        String leadName = lead == null ? "(deleted lead)" : fullName(lead);
        if (leadName.isBlank()) leadName = lead == null ? "(deleted lead)" : (lead.getPhone() == null ? "—" : lead.getPhone());
        return new ChangeRequestView(r.getId(), r.getLeadId(), leadName, r.getField(),
                EDITABLE_FIELDS.getOrDefault(r.getField(), r.getField()),
                r.getCurrentValue(), r.getRequestedValue(), r.getStatus(),
                r.getRequestedBy(), r.getDecidedBy(), r.getNote(), r.getCreatedAt(), r.getDecidedAt());
    }

    /** Digits-only, last-10 form of a phone for duplicate matching across formats (+91, leading 0, spaces). */
    static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    /** Map a program slug/label to [courseId, courseName]. */
    private static String[] course(String program) {
        String p = program == null ? "" : program.trim().toLowerCase();
        if (p.equals("agentic-ai")) return new String[]{"agentic-ai", "Agentic AI Engineer"};
        if (p.equals("ai-ml")) return new String[]{"ai-ml", "Applied AI & ML"};
        if (!p.isEmpty()) return new String[]{null, program.trim()};
        return new String[]{null, null};
    }

    private Map<UUID, String> userNames() {
        Map<UUID, String> m = new HashMap<>();
        for (User u : users.findAll()) m.put(u.getId(), displayName(u));
        return m;
    }

    private static String displayName(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " +
                (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }

    private static LeadView toView(Lead l, Map<UUID, String> names) {
        return new LeadView(l.getId(), l.getFirstName(), l.getLastName(), l.getEmail(), l.getPhone(),
                l.getSource(), l.getCourseId(), l.getCourseName(), l.getMessage(), l.getExtra(),
                l.getStatus(), l.getOwnerId(), l.getOwnerId() == null ? null : names.get(l.getOwnerId()),
                l.getCreatedById(), l.getCreatedById() == null ? null : names.get(l.getCreatedById()),
                l.getCreatedAt());
    }

    private static ActivityView toActivity(Activity a) {
        return new ActivityView(a.getId(), a.getType(), a.getBody(), a.getAuthor(), a.getCreatedAt());
    }
}
