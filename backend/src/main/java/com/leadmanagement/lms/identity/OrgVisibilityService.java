package com.leadmanagement.lms.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Central place to answer: "which owners' resources can viewer V see?"
 * and "can V see a resource owned by O?" using {@link VisibilityScope} policies.
 *
 * Use this for templates, peer groups, and future shared artifacts — not one-off rules per controller.
 */
@Service
public class OrgVisibilityService {

    private final UserRepository users;

    public OrgVisibilityService(UserRepository users) {
        this.users = users;
    }

    // ── Snapshot (one findAll per request-style evaluation) ─────────────────

    /** In-memory org graph for a single evaluation pass. */
    public static final class Snapshot {
        public final List<User> everyone;
        public final Map<UUID, User> byId;
        public final Map<UUID, List<UUID>> children;

        Snapshot(List<User> everyone) {
            this.everyone = everyone;
            this.byId = index(everyone);
            this.children = childrenIndex(everyone);
        }
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        return new Snapshot(users.findAll());
    }

    /**
     * User-ids whose owned resources are visible to {@code viewer} under {@code policy}.
     * SUPER_ADMIN (or policy ALL) gets every user id.
     */
    @Transactional(readOnly = true)
    public Set<UUID> visibleOwnerIds(User viewer, EnumSet<VisibilityScope> policy) {
        return visibleOwnerIds(viewer, policy, snapshot());
    }

    public Set<UUID> visibleOwnerIds(User viewer, EnumSet<VisibilityScope> policy, Snapshot snap) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(snap, "snap");
        if (isSuperAdmin(viewer) || policy.contains(VisibilityScope.ALL)) {
            Set<UUID> all = new HashSet<>();
            for (User u : snap.everyone) all.add(u.getId());
            return all;
        }

        Set<UUID> out = new HashSet<>();
        UUID vid = viewer.getId();

        if (policy.contains(VisibilityScope.SELF)) {
            out.add(vid);
        }
        if (policy.contains(VisibilityScope.DOWN)) {
            out.addAll(subtree(vid, snap.children));
        }
        if (policy.contains(VisibilityScope.UP)) {
            out.addAll(ancestors(vid, snap.byId));
        }
        if (policy.contains(VisibilityScope.PEERS)) {
            out.addAll(peerIds(viewer, snap.everyone));
        }
        return out;
    }

    /**
     * True if {@code viewer} may see a resource owned by {@code ownerId} under {@code policy}.
     * {@code ownerId} null = system/global — visible when SUPER_ADMIN / ALL, or {@code systemVisibleToAll}.
     */
    @Transactional(readOnly = true)
    public boolean canSeeOwner(User viewer, UUID ownerId, EnumSet<VisibilityScope> policy,
                               boolean systemVisibleToAll) {
        return canSeeOwner(viewer, ownerId, policy, systemVisibleToAll, snapshot());
    }

    public boolean canSeeOwner(User viewer, UUID ownerId, EnumSet<VisibilityScope> policy,
                               boolean systemVisibleToAll, Snapshot snap) {
        if (viewer == null) return false;
        if (isSuperAdmin(viewer) || policy.contains(VisibilityScope.ALL)) return true;
        if (ownerId == null) return systemVisibleToAll;
        return visibleOwnerIds(viewer, policy, snap).contains(ownerId);
    }

    /**
     * Collaborative resource (e.g. peer groups):
     * <ul>
     *   <li>SUPER_ADMIN → yes</li>
     *   <li>creator visible via {@code ownerPolicy} (usually TEAM_SPHERE around the creator)</li>
     *   <li>explicit {@code memberIds} always see it</li>
     *   <li>management chain <b>above any member</b> (UP from members) always see it</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public boolean canSeeCollaborative(User viewer, UUID creatorId, Collection<UUID> memberIds,
                                       EnumSet<VisibilityScope> ownerPolicy) {
        return canSeeCollaborative(viewer, creatorId, memberIds, ownerPolicy, snapshot());
    }

    public boolean canSeeCollaborative(User viewer, UUID creatorId, Collection<UUID> memberIds,
                                       EnumSet<VisibilityScope> ownerPolicy, Snapshot snap) {
        if (viewer == null) return false;
        if (isSuperAdmin(viewer)) return true;

        // Creator's sphere (SELF | UP | DOWN | PEERS of creator, as seen from viewer)
        if (creatorId != null && canSeeOwner(viewer, creatorId, ownerPolicy, false, snap)) {
            return true;
        }

        UUID vid = viewer.getId();
        if (memberIds != null) {
            for (UUID mid : memberIds) {
                if (mid == null) continue;
                if (mid.equals(vid)) return true; // explicit member
                // UP from member: is viewer an ancestor of this member?
                if (ancestors(mid, snap.byId).contains(vid)) return true;
            }
        }
        return false;
    }

    /** Resolve user by email (template created_by is stored as email today). */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return users.findByEmailIgnoreCase(email.trim());
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return users.findById(id);
    }

    public static boolean isSuperAdmin(User u) {
        return u.getRoles().stream().map(Role::getName).anyMatch("SUPER_ADMIN"::equals);
    }

    // ── graph helpers ───────────────────────────────────────────────────────

    private static Map<UUID, User> index(List<User> all) {
        Map<UUID, User> m = new HashMap<>(all.size() * 2);
        for (User u : all) m.put(u.getId(), u);
        return m;
    }

    private static Map<UUID, List<UUID>> childrenIndex(List<User> all) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (User u : all) {
            if (u.getManagerId() != null)
                children.computeIfAbsent(u.getManagerId(), k -> new ArrayList<>()).add(u.getId());
        }
        return children;
    }

    private static Set<UUID> subtree(UUID root, Map<UUID, List<UUID>> children) {
        Set<UUID> result = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (!result.add(cur)) continue;
            for (UUID c : children.getOrDefault(cur, List.of())) stack.push(c);
        }
        return result;
    }

    private static Set<UUID> ancestors(UUID userId, Map<UUID, User> byId) {
        Set<UUID> out = new HashSet<>();
        User u = byId.get(userId);
        if (u == null) return out;
        UUID p = u.getManagerId();
        int guard = 0;
        while (p != null && guard++ < 1000) {
            out.add(p);
            User mgr = byId.get(p);
            p = mgr == null ? null : mgr.getManagerId();
        }
        return out;
    }

    private static Set<UUID> peerIds(User viewer, List<User> everyone) {
        Set<UUID> out = new HashSet<>();
        if (viewer.getPeerGroupId() == null) return out;
        UUID gid = viewer.getPeerGroupId();
        for (User u : everyone) {
            if (gid.equals(u.getPeerGroupId()) && !u.getId().equals(viewer.getId())) {
                out.add(u.getId());
            }
        }
        return out;
    }
}
