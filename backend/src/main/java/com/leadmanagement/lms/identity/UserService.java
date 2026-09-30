package com.leadmanagement.lms.identity;

import com.leadmanagement.lms.common.ApiException;
import com.leadmanagement.lms.email.EmailSend;
import com.leadmanagement.lms.email.EmailSendService;
import com.leadmanagement.lms.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // Roles a staff member can be created with.
    private static final Set<String> ASSIGNABLE_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SALES_MANAGER", "SALES_REP", "MARKETING");
    // Creating these requires the caller to be SUPER_ADMIN.
    private static final Set<String> ELEVATED_ROLES = Set.of("SUPER_ADMIN", "ADMIN");

    /** Stage / local fixed temp password (never emailed). */
    public static final String STAGE_TEMP_PASSWORD = "123456";

    /** Peer groups & owned labels: creator TEAM_SPHERE + members + chain above members. */
    private static final EnumSet<VisibilityScope> PEER_GROUP_READ = VisibilityScope.TEAM_SPHERE();

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final EmailSendService emailSends;
    private final CompanyRepository companies;
    private final PeerGroupRepository peerGroups;
    private final OrgVisibilityService orgVisibility;
    private final String frontendUrl;
    /** stage = fixed 123456, no email; prod = random + email. */
    private final boolean prodPasswordMode;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       EmailService email, EmailSendService emailSends,
                       CompanyRepository companies, PeerGroupRepository peerGroups,
                       OrgVisibilityService orgVisibility,
                       @Value("${app.frontend-url}") String frontendUrl,
                       @Value("${app.password-mode:stage}") String passwordMode) {
        this.users = users;
        this.roles = roles;
        this.emailSends = emailSends;
        this.encoder = encoder;
        this.email = email;
        this.companies = companies;
        this.peerGroups = peerGroups;
        this.orgVisibility = orgVisibility;
        this.frontendUrl = frontendUrl;
        this.prodPasswordMode = passwordMode != null && passwordMode.trim().equalsIgnoreCase("prod");
    }

    public record UserView(UUID id, String email, String firstName, String lastName,
                           boolean active, boolean mustChangePassword, List<String> roles,
                           UUID managerId, String managerName,
                           UUID companyId, String companyName,
                           UUID peerGroupId, String peerGroupName,
                           OffsetDateTime createdAt) {}
    public record CompanyView(UUID id, String name, List<MemberView> members) {}
    public record PeerGroupView(UUID id, String name, UUID createdById, String createdByName,
                                List<MemberView> members) {}
    public record MemberView(UUID id, String name, List<String> roles) {}

    /**
     * Team list scoped to the caller. Super admin sees everyone; everyone else sees their own
     * subtree (themselves + all reports below) plus their upward manager chain — never lateral peers
     * except via explicit peer-group membership (handled only for lead scope, not team list).
     * Peer-group labels are only shown when the caller is allowed to see that group.
     */
    @Transactional(readOnly = true)
    public List<UserView> list(UUID callerId, boolean isSuper) {
        OrgVisibilityService.Snapshot snap = orgVisibility.snapshot();
        List<User> all = snap.everyone;
        Map<UUID, String> names = new HashMap<>();
        for (User u : all) names.put(u.getId(), displayName(u));
        Map<UUID, String> companyNames = new HashMap<>();
        for (Company c : companies.findAll()) companyNames.put(c.getId(), c.getName());
        Map<UUID, PeerGroup> groupById = new HashMap<>();
        Map<UUID, List<UUID>> membersByGroup = membersByGroup(all);
        for (PeerGroup g : peerGroups.findAll()) groupById.put(g.getId(), g);
        User caller = snap.byId.get(callerId);
        Set<UUID> visible = isSuper ? null : visibleTeamIds(callerId, all);
        return all.stream()
                .filter(u -> visible == null || visible.contains(u.getId()))
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(u -> {
                    UUID peerId = null;
                    String peerName = null;
                    if (u.getPeerGroupId() != null && caller != null) {
                        PeerGroup g = groupById.get(u.getPeerGroupId());
                        if (g != null && canSeePeerGroup(caller, g, membersByGroup, snap)) {
                            peerId = g.getId();
                            peerName = g.getName();
                        }
                    }
                    return toView(u,
                            u.getManagerId() == null ? null : names.get(u.getManagerId()),
                            u.getCompanyId() == null ? null : companyNames.get(u.getCompanyId()),
                            peerId, peerName);
                })
                .toList();
    }

    // ── Companies (super-admin grouping of admins for shared lead visibility) ──

    @Transactional(readOnly = true)
    public List<CompanyView> listCompanies() {
        List<User> all = users.findAll();
        return companies.findAllByOrderByNameAsc().stream().map(c -> {
            List<MemberView> members = all.stream()
                    .filter(u -> c.getId().equals(u.getCompanyId()) && u.isActive())
                    .map(u -> new MemberView(u.getId(), displayName(u),
                            u.getRoles().stream().map(Role::getName).sorted().toList()))
                    .toList();
            return new CompanyView(c.getId(), c.getName(), members);
        }).toList();
    }

    @Transactional
    public CompanyView createCompany(String name) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Company name is required");
        Company c = companies.save(new Company(name.trim()));
        return new CompanyView(c.getId(), c.getName(), List.of());
    }

    @Transactional
    public void deleteCompany(UUID id) {
        if (!companies.existsById(id)) throw ApiException.notFound("Company not found");
        for (User u : users.findByCompanyId(id)) { u.setCompanyId(null); users.save(u); }
        companies.deleteById(id);
    }

    @Transactional
    public UserView setCompany(UUID userId, String companyIdRaw) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        UUID cid = (companyIdRaw == null || companyIdRaw.isBlank()) ? null : UUID.fromString(companyIdRaw.trim());
        if (cid != null && !companies.existsById(cid)) throw ApiException.badRequest("Company not found");
        u.setCompanyId(cid);
        users.save(u);
        return fullView(u);
    }

    // ── Peer groups (explicit peer share — company-like, but visibility-scoped) ──

    /**
     * Peer groups via {@link OrgVisibilityService#canSeeCollaborative}:
     * creator TEAM_SPHERE (SELF|UP|DOWN|PEERS) + explicit members + management chain above members.
     */
    @Transactional(readOnly = true)
    public List<PeerGroupView> listPeerGroups(String actorEmail) {
        User actor = requireUser(actorEmail);
        OrgVisibilityService.Snapshot snap = orgVisibility.snapshot();
        List<User> all = snap.everyone;
        Map<UUID, String> names = new HashMap<>();
        for (User u : all) names.put(u.getId(), displayName(u));
        Map<UUID, List<UUID>> membersByGroup = membersByGroup(all);

        return peerGroups.findAllByOrderByNameAsc().stream()
                .filter(g -> canSeePeerGroup(actor, g, membersByGroup, snap))
                .map(g -> toPeerGroupView(g, all, names))
                .toList();
    }

    @Transactional
    public PeerGroupView createPeerGroup(String actorEmail, String name) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Peer group name is required");
        User actor = requireUser(actorEmail);
        PeerGroup g = peerGroups.save(new PeerGroup(name.trim(), actor.getId()));
        return new PeerGroupView(g.getId(), g.getName(), g.getCreatedById(), displayName(actor), List.of());
    }

    @Transactional
    public void deletePeerGroup(String actorEmail, UUID id) {
        User actor = requireUser(actorEmail);
        PeerGroup g = peerGroups.findById(id).orElseThrow(() -> ApiException.notFound("Peer group not found"));
        if (!OrgVisibilityService.isSuperAdmin(actor) && !actor.getId().equals(g.getCreatedById())) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only the creator (or a super admin) can delete this peer group");
        }
        for (User u : users.findByPeerGroupId(id)) { u.setPeerGroupId(null); users.save(u); }
        peerGroups.deleteById(id);
    }

    /**
     * Assign or clear a user's peer group.
     * SUPER_ADMIN: anyone / any group.
     * ADMIN: target in own subtree; group must pass collaborative visibility.
     */
    @Transactional
    public UserView setPeerGroup(String actorEmail, UUID userId, String peerGroupIdRaw) {
        User actor = requireUser(actorEmail);
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        boolean superAdmin = OrgVisibilityService.isSuperAdmin(actor);
        if (!superAdmin) {
            if (!isAdmin(actor))
                throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Only an admin can assign peer groups");
            Set<UUID> subtree = subtreeUserIds(actor.getId());
            if (!subtree.contains(u.getId()))
                throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "You can only assign peer groups for people who report to you (or yourself)");
        }
        UUID gid = (peerGroupIdRaw == null || peerGroupIdRaw.isBlank()) ? null : UUID.fromString(peerGroupIdRaw.trim());
        if (gid != null) {
            PeerGroup g = peerGroups.findById(gid).orElseThrow(() -> ApiException.badRequest("Peer group not found"));
            if (!superAdmin) {
                OrgVisibilityService.Snapshot snap = orgVisibility.snapshot();
                Map<UUID, List<UUID>> membersByGroup = membersByGroup(snap.everyone);
                if (!canSeePeerGroup(actor, g, membersByGroup, snap))
                    throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                            "You cannot use this peer group");
            }
        }
        u.setPeerGroupId(gid);
        users.save(u);
        return fullView(u);
    }

    private boolean canSeePeerGroup(User actor, PeerGroup g,
                                    Map<UUID, List<UUID>> membersByGroup,
                                    OrgVisibilityService.Snapshot snap) {
        List<UUID> members = membersByGroup.getOrDefault(g.getId(), List.of());
        return orgVisibility.canSeeCollaborative(actor, g.getCreatedById(), members, PEER_GROUP_READ, snap);
    }

    private static Map<UUID, List<UUID>> membersByGroup(List<User> all) {
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (User u : all) {
            if (u.getPeerGroupId() != null)
                map.computeIfAbsent(u.getPeerGroupId(), k -> new ArrayList<>()).add(u.getId());
        }
        return map;
    }

    private PeerGroupView toPeerGroupView(PeerGroup g, List<User> all, Map<UUID, String> names) {
        List<MemberView> members = all.stream()
                .filter(u -> g.getId().equals(u.getPeerGroupId()) && u.isActive())
                .map(u -> new MemberView(u.getId(), displayName(u),
                        u.getRoles().stream().map(Role::getName).sorted().toList()))
                .toList();
        String creatorName = g.getCreatedById() == null ? null : names.get(g.getCreatedById());
        return new PeerGroupView(g.getId(), g.getName(), g.getCreatedById(), creatorName, members);
    }

    /**
     * Owner-id set a user may see leads for:
     * <ul>
     *   <li>their own reporting subtree</li>
     *   <li>if they belong to a company — subtrees of every other company member</li>
     *   <li>if they belong to a peer group — subtrees of every other peer-group member</li>
     * </ul>
     * Built from a single in-memory user load + child index (O(users + edges)), not N recursive queries.
     */
    @Transactional(readOnly = true)
    public Set<UUID> leadScopeUserIds(UUID userId) {
        List<User> all = users.findAll();
        Map<UUID, List<UUID>> children = childrenIndex(all);

        User u = null;
        for (User x : all) if (x.getId().equals(userId)) { u = x; break; }
        Set<UUID> scope = subtreeFromIndex(userId, children);

        if (u != null && u.getCompanyId() != null) {
            UUID companyId = u.getCompanyId();
            for (User peer : all) {
                if (companyId.equals(peer.getCompanyId()) && !peer.getId().equals(userId)) {
                    scope.addAll(subtreeFromIndex(peer.getId(), children));
                }
            }
        }

        if (u != null && u.getPeerGroupId() != null) {
            UUID gid = u.getPeerGroupId();
            for (User peer : all) {
                if (gid.equals(peer.getPeerGroupId()) && !peer.getId().equals(userId)) {
                    scope.addAll(subtreeFromIndex(peer.getId(), children));
                }
            }
        }
        return scope;
    }

    private static Map<UUID, User> indexUsers(List<User> all) {
        Map<UUID, User> byId = new HashMap<>(all.size() * 2);
        for (User x : all) byId.put(x.getId(), x);
        return byId;
    }

    private static Map<UUID, List<UUID>> childrenIndex(List<User> all) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (User u : all) {
            if (u.getManagerId() != null)
                children.computeIfAbsent(u.getManagerId(), k -> new ArrayList<>()).add(u.getId());
        }
        return children;
    }

    private static Set<UUID> subtreeFromIndex(UUID rootId, Map<UUID, List<UUID>> children) {
        Set<UUID> result = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (!result.add(cur)) continue;
            for (UUID child : children.getOrDefault(cur, List.of())) stack.push(child);
        }
        return result;
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> ApiException.unauthorized("Not signed in"));
    }

    private static boolean isSuperAdmin(User u) {
        return u.getRoles().stream().map(Role::getName).anyMatch("SUPER_ADMIN"::equals);
    }

    private static boolean isAdmin(User u) {
        return u.getRoles().stream().map(Role::getName).anyMatch(r -> "ADMIN".equals(r) || "SUPER_ADMIN".equals(r));
    }

    /** Caller's subtree (self + all descendants) plus their upward manager chain to the top. */
    private Set<UUID> visibleTeamIds(UUID userId, List<User> all) {
        Set<UUID> ids = subtreeUserIds(userId, all); // self + descendants (down: show all)
        Map<UUID, UUID> parent = new HashMap<>();
        for (User u : all) parent.put(u.getId(), u.getManagerId());
        UUID p = parent.get(userId);
        int guard = 0;
        while (p != null && guard++ < 1000) { ids.add(p); p = parent.get(p); } // up: chain only, no peers
        return ids;
    }

    /** The user's id plus every user that reports to them, directly or indirectly. */
    @Transactional(readOnly = true)
    public Set<UUID> subtreeUserIds(UUID rootId) {
        return subtreeUserIds(rootId, users.findAll());
    }

    private Set<UUID> subtreeUserIds(UUID rootId, List<User> all) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (User u : all) {
            if (u.getManagerId() != null) children.computeIfAbsent(u.getManagerId(), k -> new ArrayList<>()).add(u.getId());
        }
        Set<UUID> result = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (!result.add(cur)) continue;
            for (UUID child : children.getOrDefault(cur, List.of())) stack.push(child);
        }
        return result;
    }

    /** Set (or clear, with null) a user's manager. Rejects cycles. */
    @Transactional
    public UserView setManager(UUID userId, String managerIdRaw) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        UUID managerId = (managerIdRaw == null || managerIdRaw.isBlank()) ? null : UUID.fromString(managerIdRaw.trim());
        if (managerId != null) {
            if (managerId.equals(userId)) throw ApiException.badRequest("A user cannot report to themselves");
            users.findById(managerId).orElseThrow(() -> ApiException.badRequest("Manager not found"));
            // Walk up from the proposed manager; if we reach this user, it's a cycle.
            Map<UUID, UUID> parent = new HashMap<>();
            for (User x : users.findAll()) parent.put(x.getId(), x.getManagerId());
            UUID p = managerId;
            while (p != null) {
                if (p.equals(userId)) throw ApiException.badRequest("That would create a reporting loop");
                p = parent.get(p);
            }
            u.setManagerId(managerId);
        } else {
            u.setManagerId(null);
        }
        users.save(u);
        return fullView(u);
    }

    @Transactional
    public UserView createStaff(String emailAddr, String firstName, String lastName,
                                String password, String roleName, String managerIdRaw, boolean callerIsSuperAdmin) {
        if (emailAddr == null || emailAddr.isBlank()) throw ApiException.badRequest("Email is required");
        final String rn = roleName == null ? "" : roleName.trim().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(rn)) throw ApiException.badRequest("Unknown role: " + rn);
        if (ELEVATED_ROLES.contains(rn) && !callerIsSuperAdmin) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only a super admin can create " + rn + " users");
        }
        String normalized = emailAddr.toLowerCase().trim();
        if (users.existsByEmailIgnoreCase(normalized)) throw ApiException.conflict("A user with this email already exists");

        Role role = roles.findByName(rn).orElseThrow(() -> ApiException.badRequest("Role not found: " + rn));

        // Always issue a managed temp password (stage: 123456 / prod: random). Optional admin password ignored.
        String pwd = issueTempPlainPassword();

        User u = new User();
        u.setEmail(normalized);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setPasswordHash(encoder.encode(pwd));
        u.setActive(true);
        u.setMustChangePassword(true);
        u.setRoles(Set.of(role));
        if (managerIdRaw != null && !managerIdRaw.isBlank()) {
            UUID mgrId = UUID.fromString(managerIdRaw.trim());
            users.findById(mgrId).orElseThrow(() -> ApiException.badRequest("Manager not found"));
            u.setManagerId(mgrId);
        }
        u = users.save(u);

        deliverTempPassword(u, pwd, "welcome", rn);
        return fullView(u);
    }

    /**
     * Forgot password: always returns a generic outcome to the API (controller maps to success message).
     * Stage → reset to 123456, no email. Prod → random password emailed.
     * User must change password on next login.
     */
    @Transactional
    public void forgotPassword(String emailAddr) {
        if (emailAddr == null || emailAddr.isBlank()) return;
        Optional<User> found = users.findByEmailIgnoreCase(emailAddr.trim());
        if (found.isEmpty() || !found.get().isActive()) {
            log.info("Forgot-password requested for unknown/inactive email (no action)");
            return;
        }
        User u = found.get();
        String pwd = issueTempPlainPassword();
        u.setPasswordHash(encoder.encode(pwd));
        u.setMustChangePassword(true);
        users.save(u);
        deliverTempPassword(u, pwd, "reset", null);
    }

    /**
     * Remove (deactivate) a staff member. A super admin can remove anyone; everyone else can only
     * remove people in their own reporting subtree (descendants), never peers or ancestors.
     * The removed person's direct reports are reparented up to the removed person's manager.
     */
    @Transactional
    public void removeUser(String actorEmail, UUID targetId) {
        User actor = users.findByEmailIgnoreCase(actorEmail == null ? "" : actorEmail.trim())
                .orElseThrow(() -> ApiException.unauthorized("Not signed in"));
        User target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getId().equals(actor.getId())) throw ApiException.badRequest("You cannot remove yourself");

        boolean superAdmin = actor.getRoles().stream().map(Role::getName).anyMatch("SUPER_ADMIN"::equals);
        if (!superAdmin) {
            Set<UUID> subtree = subtreeUserIds(actor.getId()); // includes actor + descendants
            if (!subtree.contains(target.getId()))
                throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "You can only remove people who report to you");
        }

        UUID reparentTo = target.getManagerId();
        for (User u : users.findAll()) {
            if (target.getId().equals(u.getManagerId())) { u.setManagerId(reparentTo); users.save(u); }
        }
        target.setActive(false);
        users.save(target);
    }

    /** A signed-in user changes their own password after confirming the current one. */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8)
            throw ApiException.badRequest("New password must be at least 8 characters");
        // Stage temp is 6 chars; still require strong new password
        if (STAGE_TEMP_PASSWORD.equals(newPassword))
            throw ApiException.badRequest("Please choose a password other than the temporary default");
        User u = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> ApiException.unauthorized("Not signed in"));
        if (currentPassword == null || !encoder.matches(currentPassword, u.getPasswordHash()))
            throw ApiException.badRequest("Current password is incorrect");
        if (encoder.matches(newPassword, u.getPasswordHash()))
            throw ApiException.badRequest("New password must be different from the current one");
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
    }

    /** stage → 123456; prod → random. */
    private String issueTempPlainPassword() {
        return prodPasswordMode ? generatePassword() : STAGE_TEMP_PASSWORD;
    }

    /**
     * stage: log only (no email). prod: send email with temp password.
     * {@code kind} is "welcome" or "reset".
     */
    private void deliverTempPassword(User u, String password, String kind, String roleName) {
        if (!prodPasswordMode) {
            log.info("[{}] Temp password for {} is {} (stage mode — not emailed)",
                    kind, u.getEmail(), password);
            return;
        }
        String name = u.getFirstName() == null ? "there" : u.getFirstName();
        if ("reset".equals(kind)) {
            String subject = "Your Lead Management System password reset";
            String html = "<p>Hi " + name + ",</p>" +
                    "<p>We received a request to reset your Lead Management System sales console password.</p>" +
                    "<p><b>Login:</b> <a href=\"" + frontendUrl + "\">" + frontendUrl + "</a><br>" +
                    "<b>Email:</b> " + u.getEmail() + "<br>" +
                    "<b>Temporary password:</b> " + password + "</p>" +
                    "<p>Sign in and you will be asked to choose a new password.</p>" +
                    "<p>If you did not request this, contact your admin.</p><p>— Lead Management System</p>";
            if (email.send(u.getEmail(), subject, html)) {
                emailSends.record(EmailSend.CAT_PASSWORD_RESET, EmailSend.LABEL_LOGIN_TRIGGERS,
                        null, u.getEmail(), subject, null);
            }
        } else {
            String subject = "Your Lead Management System team account";
            String html = "<p>Hi " + name + ",</p>" +
                    "<p>An account has been created for you on the Lead Management System team console"
                    + (roleName == null ? "" : " as <b>" + roleName + "</b>") + ".</p>" +
                    "<p><b>Login:</b> <a href=\"" + frontendUrl + "\">" + frontendUrl + "</a><br>" +
                    "<b>Email:</b> " + u.getEmail() + "<br>" +
                    "<b>Temporary password:</b> " + password + "</p>" +
                    "<p>Please log in and change your password when prompted.</p><p>— Lead Management System</p>";
            if (email.send(u.getEmail(), subject, html)) {
                emailSends.record(EmailSend.CAT_STAFF_INVITE, EmailSend.LABEL_LOGIN_TRIGGERS,
                        null, u.getEmail(), subject, null);
            }
        }
    }

    private static String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#$";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private UserView fullView(User u) {
        String mgrName = u.getManagerId() == null ? null :
                users.findById(u.getManagerId()).map(UserService::displayName).orElse(null);
        String cName = u.getCompanyId() == null ? null :
                companies.findById(u.getCompanyId()).map(Company::getName).orElse(null);
        String pName = u.getPeerGroupId() == null ? null :
                peerGroups.findById(u.getPeerGroupId()).map(PeerGroup::getName).orElse(null);
        return toView(u, mgrName, cName, u.getPeerGroupId(), pName);
    }

    static UserView toView(User u) { return toView(u, null, null, null, null); }

    static UserView toView(User u, String managerName, String companyName) {
        return toView(u, managerName, companyName, null, null);
    }

    static UserView toView(User u, String managerName, String companyName,
                           UUID peerGroupId, String peerGroupName) {
        return new UserView(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.isActive(), u.isMustChangePassword(),
                u.getRoles().stream().map(Role::getName).sorted().toList(),
                u.getManagerId(), managerName, u.getCompanyId(), companyName,
                peerGroupId, peerGroupName, u.getCreatedAt());
    }

    static String displayName(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " +
                (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }
}
