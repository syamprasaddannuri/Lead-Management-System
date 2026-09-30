package com.leadmanagement.lms.lead;

import com.leadmanagement.lms.common.CurrentUser;
import com.leadmanagement.lms.identity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class LeadController {

    private static final String SALES = "hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER','SALES_REP','MARKETING')";
    private static final String WRITE = "hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER','SALES_REP')";

    private final LeadService leadService;
    private final CurrentUser currentUser;
    private final com.leadmanagement.lms.identity.UserService userService;

    public LeadController(LeadService leadService, CurrentUser currentUser,
                          com.leadmanagement.lms.identity.UserService userService) {
        this.leadService = leadService;
        this.currentUser = currentUser;
        this.userService = userService;
    }

    /**
     * Lead visibility scope for the current user:
     * super admin and marketing see everything (null); everyone else sees their reporting subtree.
     */
    private java.util.Set<UUID> visibilityScope() {
        var user = currentUser.get();
        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        boolean seesAll = roles.contains("SUPER_ADMIN") || roles.contains("MARKETING");
        return seesAll ? null : userService.leadScopeUserIds(user.getId());
    }

    public record PublicLeadRequest(@NotBlank String name, String email, @NotBlank String phoneNumber,
                                    String program, String message) {}
    public record UpdateLeadRequest(String status, String ownerId, String lostReason, String program) {}
    public record DetailsRequest(String name, String email, String phone, String source, String course) {}
    public record ChangeRequestBody(@NotBlank String field, @NotBlank String requestedValue, String note) {}
    public record DecisionBody(String note) {}
    public record NoteRequest(@NotBlank String body) {}
    public record EmailRequest(@NotBlank String subject, @NotBlank String body) {}
    public record FollowUpRequest(@NotBlank String message, @NotBlank String dueAt) {}

    private static final String MANAGE_REQUESTS = "hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER')";

    /** Public website capture (no auth). */
    @PostMapping("/public/leads")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> capture(@Valid @RequestBody PublicLeadRequest req) {
        Lead lead = leadService.capture(req.name(), req.email(), req.phoneNumber(), req.program(), req.message());
        return Map.of("id", lead.getId(), "status", "received");
    }

    public record BulkRequest(boolean commit, java.util.List<LeadService.BulkRow> rows) {}
    public record CreateLeadRequest(@NotBlank String name, String email, @NotBlank String phone,
                                    String program, String source, String message) {}
    public record AssignRequest(java.util.List<UUID> leadIds, String ownerId) {}
    public record AssignByFilterRequest(String q, String status, String owner, String ownerId) {}

    /** Bulk import leads (any staff member). commit=false returns a validation preview; true imports. */
    @PostMapping("/leads/bulk")
    @PreAuthorize(SALES)
    public LeadService.BulkResult bulk(@RequestBody BulkRequest req) {
        return leadService.bulk(req.rows(), req.commit(), currentUser.email());
    }

    public record DupCheckRequest(java.util.List<String> phones, java.util.List<String> emails) {}

    /** Chunked duplicate check for the import preview: returns which phones/emails already exist. */
    @PostMapping("/leads/check-duplicates")
    @PreAuthorize(SALES)
    public LeadService.DupCheckResult checkDuplicates(@RequestBody DupCheckRequest req) {
        return leadService.checkDuplicates(req.phones(), req.emails());
    }

    /** Add a single lead manually (any staff member). Owner + creator default to the actor. */
    @PostMapping("/leads")
    @PreAuthorize(SALES)
    @ResponseStatus(HttpStatus.CREATED)
    public LeadService.LeadView create(@Valid @RequestBody CreateLeadRequest req) {
        return leadService.createSingle(req.name(), req.email(), req.phone(), req.program(),
                req.source(), req.message(), currentUser.email());
    }

    /** Assign many leads to a rep at once (managers/admins). */
    @PostMapping("/leads/assign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER')")
    public Map<String, Object> assignBulk(@RequestBody AssignRequest req) {
        int n = leadService.assignBulk(req.leadIds(), req.ownerId(), currentUser.email());
        return Map.of("assigned", n);
    }

    /**
     * Assign every lead matching the given search/status/owner filter, not just a loaded page
     * (managers/admins). Mirrors the filters accepted by {@code GET /leads/page}.
     */
    @PostMapping("/leads/assign-by-filter")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER')")
    public Map<String, Object> assignByFilter(@RequestBody AssignByFilterRequest req) {
        int n = leadService.assignByFilter(visibilityScope(), req.q(), req.status(), req.owner(),
                req.ownerId(), currentUser.email());
        return Map.of("assigned", n);
    }

    /**
     * Lead analytics. Super admin/marketing see all; an admin sees their reporting branch.
     * Optional {@code days}: 1, 7, 30, 90, or omit / 0 for all time (filter by lead created_at).
     */
    @GetMapping("/leads/report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public LeadService.ReportView report(@RequestParam(required = false) Integer days) {
        return leadService.report(visibilityScope(), days);
    }

    /** Lightweight lead counts for the dashboard, scoped to the caller's branch. */
    @GetMapping("/leads/stats")
    @PreAuthorize(SALES)
    public LeadService.StatsView stats() { return leadService.stats(visibilityScope()); }

    /** Ordered pipeline stages (for the board / stage bar). */
    @GetMapping("/leads/stages")
    @PreAuthorize(SALES)
    public List<String> stages() { return LeadService.STAGES; }

    /** Staff a lead can be assigned to — scoped to the caller's reporting branch (all for super admin). */
    @GetMapping("/leads/assignees")
    @PreAuthorize(SALES)
    public List<LeadService.AssigneeView> assignees() { return leadService.assignees(visibilityScope()); }

    @GetMapping("/leads")
    @PreAuthorize(SALES)
    public List<LeadService.LeadView> list() {
        var scope = visibilityScope();
        return scope == null ? leadService.listAll() : leadService.listSubtree(scope);
    }

    /** Paged + searched + filtered + sorted leads. Page 0-indexed; size clamped to [1,200]. */
    @GetMapping("/leads/page")
    @PreAuthorize(SALES)
    public LeadService.PageView<LeadService.LeadView> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String owner,
            @RequestParam(defaultValue = "created") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        int p = Math.max(0, page);
        int s = Math.min(200, Math.max(1, size));
        String field = switch (sort == null ? "" : sort) {
            case "name" -> "firstName";
            case "stage" -> "status";
            default -> "createdAt";
        };
        Sort.Direction d = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(p, s, Sort.by(d, field));
        return leadService.searchPaged(visibilityScope(), q, status, owner, pageable);
    }

    @GetMapping("/leads/{id}")
    @PreAuthorize(SALES)
    public LeadService.LeadView get(@PathVariable UUID id) { return leadService.detail(id); }

    @PatchMapping("/leads/{id}")
    @PreAuthorize(WRITE)
    public LeadService.LeadView update(@PathVariable UUID id, @RequestBody UpdateLeadRequest req) {
        return leadService.update(id, req.status(), req.ownerId(), req.lostReason(), req.program(), currentUser.email());
    }

    /** Edit lead contact fields. Managers+ edit anything; reps may only fill blank fields. */
    @PatchMapping("/leads/{id}/details")
    @PreAuthorize(WRITE)
    public LeadService.LeadView editDetails(@PathVariable UUID id, @RequestBody DetailsRequest req) {
        Map<String, String> changes = new java.util.LinkedHashMap<>();
        if (req.name() != null) changes.put("name", req.name());
        if (req.email() != null) changes.put("email", req.email());
        if (req.phone() != null) changes.put("phone", req.phone());
        if (req.source() != null) changes.put("source", req.source());
        if (req.course() != null) changes.put("course", req.course());
        return leadService.editDetails(id, changes, currentUser.email());
    }

    /** Change requests raised on a lead (visible to anyone who can see the lead). */
    @GetMapping("/leads/{id}/change-requests")
    @PreAuthorize(SALES)
    public List<LeadService.ChangeRequestView> leadChangeRequests(@PathVariable UUID id) {
        return leadService.changeRequestsForLead(id);
    }

    /** A rep raises a request to change a non-blank field. */
    @PostMapping("/leads/{id}/change-requests")
    @PreAuthorize(WRITE)
    public LeadService.ChangeRequestView raiseChangeRequest(@PathVariable UUID id, @Valid @RequestBody ChangeRequestBody req) {
        return leadService.raiseChangeRequest(id, req.field(), req.requestedValue(), req.note(), currentUser.email());
    }

    /** All pending change requests across leads (managers+). */
    @GetMapping("/change-requests")
    @PreAuthorize(MANAGE_REQUESTS)
    public List<LeadService.ChangeRequestView> pendingChangeRequests() {
        return leadService.pendingChangeRequests();
    }

    /** Count of pending change requests, for the nav badge (managers+). */
    @GetMapping("/change-requests/count")
    @PreAuthorize(MANAGE_REQUESTS)
    public Map<String, Object> pendingCount() {
        return Map.of("pending", leadService.pendingChangeRequestCount());
    }

    @PostMapping("/change-requests/{rid}/approve")
    @PreAuthorize(MANAGE_REQUESTS)
    public LeadService.ChangeRequestView approve(@PathVariable UUID rid, @RequestBody(required = false) DecisionBody body) {
        return leadService.decideChangeRequest(rid, true, body == null ? null : body.note(), currentUser.email());
    }

    @PostMapping("/change-requests/{rid}/reject")
    @PreAuthorize(MANAGE_REQUESTS)
    public LeadService.ChangeRequestView reject(@PathVariable UUID rid, @RequestBody(required = false) DecisionBody body) {
        return leadService.decideChangeRequest(rid, false, body == null ? null : body.note(), currentUser.email());
    }

    /** Delete a lead and its activity. Super admin only. */
    @DeleteMapping("/leads/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        leadService.delete(id);
    }

    @GetMapping("/leads/{id}/activities")
    @PreAuthorize(SALES)
    public List<LeadService.ActivityView> activities(@PathVariable UUID id) { return leadService.activities(id); }

    @PostMapping("/leads/{id}/notes")
    @PreAuthorize(WRITE)
    public LeadService.ActivityView addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest req) {
        return leadService.addNote(id, req.body(), currentUser.email());
    }

    /** Email the lead directly (logged on the timeline). */
    @PostMapping("/leads/{id}/email")
    @PreAuthorize(WRITE)
    public LeadService.ActivityView emailLead(@PathVariable UUID id, @Valid @RequestBody EmailRequest req) {
        return leadService.emailLead(id, req.subject(), req.body(), currentUser.email());
    }

    /** Schedule a follow-up (message + due time) on a lead. */
    @PostMapping("/leads/{id}/follow-ups")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public LeadService.FollowUpView createFollowUp(@PathVariable UUID id, @Valid @RequestBody FollowUpRequest req) {
        return leadService.createFollowUp(id, req.message(), parseDueAt(req.dueAt()), currentUser.email());
    }

    /** Parse ISO-8601 with offset, or datetime-local "yyyy-MM-dd'T'HH:mm" in server zone. */
    private static OffsetDateTime parseDueAt(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw com.leadmanagement.lms.common.ApiException.badRequest("Follow-up time is required");
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {}
        try {
            return java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        } catch (Exception e) {
            throw com.leadmanagement.lms.common.ApiException.badRequest("Invalid follow-up time: " + s);
        }
    }

    /** All follow-ups on a lead (pending + done). */
    @GetMapping("/leads/{id}/follow-ups")
    @PreAuthorize(SALES)
    public List<LeadService.FollowUpView> leadFollowUps(@PathVariable UUID id) {
        return leadService.followUpsForLead(id);
    }

    /** Pending follow-ups for the dashboard (scoped to caller's lead visibility). */
    @GetMapping("/follow-ups/pending")
    @PreAuthorize(SALES)
    public List<LeadService.FollowUpView> pendingFollowUps() {
        return leadService.pendingFollowUps(visibilityScope());
    }

    /** Mark a follow-up complete — removes it from the dashboard list. */
    @PostMapping("/follow-ups/{id}/complete")
    @PreAuthorize(WRITE)
    public LeadService.FollowUpView completeFollowUp(@PathVariable UUID id) {
        return leadService.completeFollowUp(id, currentUser.email());
    }
}
