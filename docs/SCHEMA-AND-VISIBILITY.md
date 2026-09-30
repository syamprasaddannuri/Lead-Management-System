# LMS / CRM — Data model, peer visibility, performance notes

## Entity-relationship (current)

```
                     ┌──────────────┐
                     │ permissions  │
                     └──────▲───────┘
                            │ M:N
┌──────────┐   M:N   ┌──────┴───────┐
│  users   │◄───────►│    roles     │
└────┬─────┘         └──────────────┘
     │
     │ manager_id (self-FK, reporting tree)
     │ company_id ──────────► companies
     │ peer_group_id ───────► peer_groups (created_by → users)
     │
     │ owner_id / created_by
     ▼
┌──────────┐ 1:N  ┌────────────────────┐
│  leads   │─────►│  lead_activities   │
└────┬─────┘      └────────────────────┘
     │ 1:N
     ▼
┌──────────────────────┐
│ lead_change_requests │
└──────────────────────┘

email_templates (standalone)
```

### Core tables

| Table | Key columns | Notes |
|-------|-------------|--------|
| `users` | id, email, password_hash, active, manager_id, company_id, **peer_group_id** | Soft-delete via `active=false` |
| `roles` / `permissions` / join tables | RBAC seed on boot | AuthZ uses role names mostly |
| `companies` | id, name | Super-admin only; shared lead visibility across members’ subtrees |
| **`peer_groups`** | id, name, **created_by**, created_at | Explicit peer share; visibility scoped (see below) |
| `leads` | contact, status, owner_id, created_by, phone_normalized, course_*, source | Pipeline stage string |
| `lead_activities` | lead_id, type, body, author | Timeline |
| `lead_change_requests` | lead_id, field, status, … | Rep edit governance |
| `email_templates` | name, subject, body, stage, active | Outreach |

### Peer group visibility (who can *see* a group)

A peer group **G** is visible to a caller **A** only if:

1. **A is SUPER_ADMIN**, or  
2. **A is the creator** (`peer_groups.created_by = A`), or  
3. **A is a member** (`users.peer_group_id = G`), or  
4. **A is on the management chain above any member** (walk `manager_id` up from each member until A).

Sibling admins / other branches **do not** see G.

| Action | Who |
|--------|-----|
| Create group | ADMIN / SUPER_ADMIN (stored as creator) |
| List groups | Filtered by rules above |
| Assign member | ADMIN: target in own subtree + group visible; SUPER: anyone |
| Delete group | Creator or SUPER_ADMIN |
| Share **leads** among members | If two users share the same `peer_group_id`, each lead scope includes the other’s **subtree** |

### Lead visibility (who can *see leads*)

```
leadScope(user) =
    subtree(user)                                           // self + reports
  ∪ subtree(each company peer)        if user.company_id set
  ∪ subtree(each peer-group peer)     if user.peer_group_id set

SUPER_ADMIN / MARKETING → no owner filter (all leads)
```

Company and peer-group expand **ownership scope** for leads; they do not grant Team-page staff list of lateral peers.

---

## Performance notes (current hotspots)

### Already improved

| Area | Before | Now |
|------|--------|-----|
| `leadScopeUserIds` | `findAll` + **N×** full tree walks rebuilding children map each time | One `findAll`, one children index, O(users + edges) union |

### Remaining concerns (scale with #users / #leads)

| Query / path | Cost today | Risk when large | Mitigation |
|--------------|------------|-----------------|------------|
| **`users.findAll()`** in `leadScopeUserIds`, team `list`, peer list, assignees | Full table each request | Medium at 10k+ users | Cache org graph (TTL / after writes); or SQL recursive CTE for subtree |
| **`leadSpec` + `ownerId IN (:scope)`** | Scope set can be large (subtree + peers) | Large `IN` lists; plan quality | Cap scope; use temp table / join; ensure index on `leads.owner_id` |
| **Lead list page** | JPA Specification + page | OK with indexes | Indexes: `(owner_id)`, `(status)`, `(phone_normalized)`, `(created_at)` |
| **`userNames()` / full user map** on many lead ops | Loads all users for name join | Medium | Projection join or small cache of id→name |
| **Bulk import** | `findAll` phones/emails for dedup | High on big CRM | SQL `WHERE phone_normalized IN (...)` / unique constraints |
| **`report` analytics** | May load many leads in memory (check LeadService.report) | High | SQL aggregates only (partially done for stats) |
| **Team list** | Full users + peer visibility walk | Low–medium | Same org-graph cache |
| **Peer `canSeePeerGroup`** | O(members × depth) in-memory | Low for small groups | Fine; avoid re-`findAll` if already loaded |

### Indexes to ensure in production

```sql
-- Leads (critical)
CREATE INDEX IF NOT EXISTS idx_leads_owner ON leads(owner_id);
CREATE INDEX IF NOT EXISTS idx_leads_status ON leads(status);
CREATE INDEX IF NOT EXISTS idx_leads_phone_norm ON leads(phone_normalized);
CREATE INDEX IF NOT EXISTS idx_leads_created ON leads(created_at DESC);

-- Org / peers
CREATE INDEX IF NOT EXISTS idx_users_manager ON users(manager_id);
CREATE INDEX IF NOT EXISTS idx_users_company ON users(company_id);
CREATE INDEX IF NOT EXISTS idx_users_peer_group ON users(peer_group_id);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(active);
CREATE INDEX IF NOT EXISTS idx_peer_groups_created_by ON peer_groups(created_by);
```

Hibernate `ddl-auto=update` does **not** reliably create these — add via Flyway/SQL when hardening.

### What is *not* a problem at our scale (hundreds of staff, tens of k leads)

- Peer group visibility walks (depth of org tree is small)  
- Activity timeline per lead (indexed by lead_id if present)  
- JWT auth per request  

### What *will* hurt first if volume grows

1. Lead search/list without indexes on `owner_id` / phone  
2. Bulk import loading all phones into memory  
3. Repeated `users.findAll()` on every lead list call (scope + names)

---

## Mental model diagram

```
          SUPER_ADMIN (sees all groups & leads)
                │
         Admin A (creator of "Mumbai reps")
           /          \
      Rep1 ●────peer────● Rep2     ← same peer_group_id
           \          /
         (manager chain above members can see group)

      Admin B (other branch)  ✗ cannot see "Mumbai reps"
```

Lead share: Rep1 ↔ Rep2 (and their reports, if any).  
Group UI: Admin A, Reps (if they had Team access), and managers above them; not Admin B.
