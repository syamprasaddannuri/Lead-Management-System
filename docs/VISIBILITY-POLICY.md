# Org visibility policy (up / down / peers / all)

## Why not “a permission per API”?

Per-endpoint rules (`canSeePeerGroup`, ad-hoc template filters, etc.) diverge over time and are hard to reason about.

We separate two layers:

| Layer | Question | Mechanism |
|-------|----------|-----------|
| **Action permission** | *May I hit this API at all?* | Role / `@PreAuthorize` (ADMIN, SALES_REP, …) |
| **Visibility policy** | *Which owned records may I see?* | `VisibilityScope` + `OrgVisibilityService` |

Do **not** invent `TEMPLATE_READ_UP`, `LEAD_READ_PEER` as dozens of permission strings unless product needs them configurable per role later. Start with **code-level policies** reused everywhere.

## Axes (`VisibilityScope`)

| Flag | Meaning for owner O |
|------|---------------------|
| **SELF** | O themselves |
| **UP** | Management chain above O |
| **DOWN** | Reporting subtree under O |
| **PEERS** | Same peer group as O |
| **ALL** | Everyone (SUPER_ADMIN / global catalogs) |

### Named policy bundles

```java
TEAM_SPHERE   = SELF | UP | DOWN | PEERS   // “my org sphere”
SELF_AND_DOWN = SELF | DOWN                  // write/manage under me
ALL_ONLY      = ALL
```

### Listing from viewer V (efficient form)

Resources owned by O are visible to V when O ∈:

```
{V}
∪ subtree(V)      // DOWN relative to V → creators under me
∪ ancestors(V)    // UP relative to V → my managers’ resources
∪ peers(V)        // PEERS
```

(Equivalent to: V is in SELF∪UP∪DOWN∪PEERS of O.)

## Applied today

| Resource | Read policy | Write policy |
|----------|-------------|--------------|
| **Email templates** | TEAM_SPHERE + system seeds for all | SELF_AND_DOWN (super = all; system seeds super-only delete) |
| **Peer groups** | `canSeeCollaborative`: creator TEAM_SPHERE **+** explicit members **+** UP from any member | create: admin; delete: creator or super |
| **Leads** | existing leadScope (subtree + company + peer group) — domain-specific, keep separate |

### Collaborative helper (peer groups)

```java
orgVisibility.canSeeCollaborative(viewer, creatorId, memberIds, TEAM_SPHERE)
// true if:
//   SUPER_ADMIN
//   OR creator is in viewer's TEAM_SPHERE (SELF|UP|DOWN|PEERS of creator)
//   OR viewer is an explicit member
//   OR viewer is on the management chain above any member
```

## Future role-configurable policies (optional)

If product needs “Marketing only sees ALL templates” vs “Reps only DOWN”:

```
role_visibility_policies(role, resource_type) → EnumSet<VisibilityScope>
```

Still one evaluator (`OrgVisibilityService`); roles only choose the flags — not one permission string per API.

## Code entry points

- `com.leadmanagement.lms.identity.VisibilityScope`
- `com.leadmanagement.lms.identity.OrgVisibilityService` — `visibleOwnerIds`, `canSeeOwner`, `canSeeCollaborative`, `Snapshot`
- `EmailTemplateService` — READ = TEAM_SPHERE, WRITE = SELF_AND_DOWN
- `UserService` peer groups — `canSeeCollaborative`

When adding a new owned resource (scripts, playbooks, scorecards):

1. Store `created_by` (user id preferred; email ok if resolved).
2. Choose READ / WRITE `EnumSet<VisibilityScope>`.
3. Filter lists with `visibleOwnerIds` / `canSeeOwner` / `canSeeCollaborative` (one `Snapshot` per request).
4. Do not copy-paste manager-walk loops into the controller.
