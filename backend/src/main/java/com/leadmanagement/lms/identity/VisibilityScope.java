package com.leadmanagement.lms.identity;

import java.util.EnumSet;

/**
 * Reusable org-graph visibility axes for owned resources (templates, future docs, etc.).
 * Combine into a policy with {@link #policy} helpers — do not invent per-API rules.
 *
 * <ul>
 *   <li>{@link #SELF} — the resource owner / creator</li>
 *   <li>{@link #UP} — management chain above the owner (managers, their managers, …)</li>
 *   <li>{@link #DOWN} — reporting subtree under the owner (direct + indirect reports)</li>
 *   <li>{@link #PEERS} — users in the same peer group as the owner</li>
 *   <li>{@link #ALL} — everyone (typically SUPER_ADMIN / global catalogs)</li>
 * </ul>
 */
public enum VisibilityScope {
    SELF,
    UP,
    DOWN,
    PEERS,
    ALL;

    /** Owner + managers above + reports below + peer-group peers. */
    public static EnumSet<VisibilityScope> TEAM_SPHERE() {
        return EnumSet.of(SELF, UP, DOWN, PEERS);
    }

    /** Owner + people under them (typical write policy for managers). */
    public static EnumSet<VisibilityScope> SELF_AND_DOWN() {
        return EnumSet.of(SELF, DOWN);
    }

    public static EnumSet<VisibilityScope> ALL_ONLY() {
        return EnumSet.of(ALL);
    }
}
