# Lead Management System / CRM — High-Level & Low-Level Design

**Product:** Lead Management System sales CRM (lead management)  
**Live URL:** https://sales.example.com  
**Document status:** Current-state design (as implemented in the repo)  
**Audience:** Engineers, architects, product partners adding features  

> **Naming note:** The repository is called “LMS,” but this is **not** a classic Learning Management System. There are no courses-as-content, student portals, quizzes, or certificates. It is a **Lead Management System / CRM** for Lead Management System’s sales and marketing teams. “Course” on a lead means **program of interest** (e.g. Agentic AI Engineer), not instructional content.

---

## 1. Purpose & scope

### 1.1 What the system does

Lead Management System/CRM helps sales and marketing staff:

1. **Capture leads** from the public website (example.com forms) and from manual/bulk import.
2. **Work a sales pipeline** (stages from New → Won/Lost).
3. **Assign ownership** and control who sees which leads via org hierarchy and company grouping.
4. **Communicate** with leads by email from the platform, with optional reply threading back onto the lead timeline.
5. **Govern data quality** (reps fill blanks; managers approve field changes).
6. **Report** on funnel health (counts, conversion, sources, owners).

### 1.2 What it deliberately does not do (today)

| Classic LMS / future CRM | Status |
|--------------------------|--------|
| Course content, modules, lessons | Not present |
| Student login / learning progress | Not present |
| Quizzes, assignments, certificates | Not present |
| Payments / Stripe | Not present |
| Marketing campaigns / UTM product | Roadmap only |
| Configurable pipeline stages UI | Hardcoded stages |
| Round-robin auto-assignment / SLA timers | Not present |
| Lead scoring / ticket size / next follow-up fields | Docs only |

---

## 2. High-Level Design (HLD)

### 2.1 Context diagram

```
                    ┌──────────────────────┐
                    │   example.com forms   │
                    │  (public website)    │
                    └──────────┬───────────┘
                               │ POST /api/public/leads
                               ▼
┌────────────┐    HTTPS    ┌───────────────────────────────────┐
│ Sales staff│────────────▶│  sales.example.com (Caddy)         │
│  browser   │             │   /api/* → Spring Boot API        │
└────────────┘             │   /*     → React SPA (nginx)      │
                           └───────────────┬───────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
            Cloud SQL Postgres      SMTP (Amazon SES)     SendGrid Inbound
            (leadmanagement)            outbound email        Parse (replies)
```

### 2.2 System components

| Component | Responsibility | Tech |
|-----------|----------------|------|
| **Frontend SPA** | Staff UI: login, dashboard, leads, team, reports, templates | React 18, TypeScript, Vite, React Router |
| **Backend API** | Auth, RBAC, lead lifecycle, email, reports | Java 17, Spring Boot 3.2, Spring Security, JPA |
| **Database** | Persistent state | PostgreSQL (GCP Cloud SQL) |
| **Edge proxy** | TLS, path routing | Caddy 2 |
| **SQL proxy** | Secure DB connectivity from VM | Cloud SQL Auth Proxy |
| **Email outbound** | Transactional mail to leads/staff | Spring Mail → SMTP (SES) |
| **Email inbound** | Lead replies → timeline | SendGrid Inbound Parse webhook |

### 2.3 Repository organization

Not a JS monorepo (no pnpm/Turbo workspaces). Side-by-side apps:

```
LMS/
├── backend/          # Maven Spring Boot API
│   └── src/main/java/com/leadmanagement/lms/
│       ├── config/       # Security, JWT, Bootstrap seed
│       ├── identity/     # Users, roles, companies
│       ├── lead/         # Leads, activities, change requests
│       ├── email/        # SMTP, templates, inbound
│       └── common/       # Exceptions, CurrentUser
├── frontend/         # React SPA (Vite)
│   └── src/
│       ├── pages/        # Feature screens
│       ├── components/   # Layout, logo
│       ├── api.ts        # Typed HTTP client
│       └── auth.tsx      # JWT session context
├── deploy/           # docker-compose, Caddyfile, env
└── docs/             # Architecture, design, samples
```

### 2.4 Personas & roles (HLD)

| Role | Who | Primary job |
|------|-----|-------------|
| **SUPER_ADMIN** | Platform root | Everything: companies, elevated users, delete leads, all data |
| **ADMIN** | Ops / sales leadership | Team, reports, leads, assignment |
| **SALES_MANAGER** | Team lead | Pipeline in branch, assign, approve field changes, templates |
| **SALES_REP** | Closer | Work assigned leads; notes, email, stage; limited field edit |
| **MARKETING** | Growth | See all leads (campaign tooling not built yet) |
| **Prospect** | External | No login; appears as a Lead via form/import |

**Role hierarchy (Spring):**  
`SUPER_ADMIN > ADMIN > SALES_MANAGER > SALES_REP`  
`MARKETING` is independent.

### 2.5 Core domain (HLD)

```
Company ──< User >── Role >── Permission
              │
              │ manager_id (reporting tree)
              │
Lead ── owner_id ──> User
  │
  ├── Activity (timeline)
  └── LeadChangeRequest (field edit approval)

EmailTemplate (reusable outreach copy)
```

**Pipeline stages (sales process):**

```
NEW → CONTACTED → QUALIFIED → NURTURING → NEGOTIATION → WON
                                                      ↘ LOST
```

**WON** means “enrolled / closed-won” as a **sales outcome**, not an LMS enrollment record.

### 2.6 Visibility model (critical business rule)

| Actor | Leads they see |
|-------|----------------|
| SUPER_ADMIN, MARKETING | All leads |
| Everyone else | Leads owned by users in their **reporting subtree** (self + reports below) |
| Same **Company** (optional) | Additionally, subtrees of **peers** in that company |

This is **org hierarchy + optional company sharing**, not multi-tenant SaaS isolation.

### 2.7 Deployment topology (HLD)

Single VM (`lead-management-vm`) Docker Compose stack:

```
Internet
   │
   ▼
Caddy :443 (sales.example.com)
   ├─ /api/* → backend:8080
   └─ /*     → frontend:80 (nginx SPA)
                    │
backend ──► cloud-sql-proxy:5432 ──► Cloud SQL (leadmanagement)
```

Deploy command (from repo root on the VM):

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

### 2.8 Cross-cutting quality attributes

| Concern | Approach |
|---------|----------|
| Auth | Stateless JWT (12h), BCrypt passwords |
| Authorization | `@PreAuthorize` roles (+ hierarchy); permissions seeded but not enforced per-request |
| Audit | Lead activity timeline for creates, stages, assign, notes, email |
| Dedup | Normalized phone (last 10 digits) + case-insensitive email |
| Scalability (current) | Single API instance; DB-side paging/filter for leads; Hibernate batch for bulk import |
| Observability | Actuator `/actuator/health` |
| Schema | Hibernate `ddl-auto: update` (Flyway planned, not implemented) |

---

## 3. Low-Level Design (LLD)

### 3.1 Backend module design

#### 3.1.1 Package layout

| Package | Key types | Responsibility |
|---------|-----------|----------------|
| `config` | `SecurityConfig`, `JwtAuthFilter`, `JwtService`, `Bootstrap` | Filter chain, JWT, first-boot seed |
| `identity` | `User`, `Role`, `Permission`, `Company`, controllers/services | Login, `/me`, staff, companies, hierarchy |
| `lead` | `Lead`, `Activity`, `LeadChangeRequest`, `LeadService` | Capture, pipeline, search, bulk, CR workflow |
| `email` | `EmailService`, `EmailTemplate*`, `Inbound*`, `ReplyToken` | Outbound SMTP, templates, inbound webhook |
| `common` | `ApiException`, `CurrentUser`, `GlobalExceptionHandler` | Errors, auth principal helper |

**Layering (per request):**

```
HTTP → Controller (@PreAuthorize)
         → Service (@Transactional, business rules)
           → Repository (Spring Data JPA)
             → PostgreSQL
```

No separate DTO jar, mapper framework, or domain-event bus. Controllers use Java **records** as request/response views.

#### 3.1.2 Identity & security (LLD)

**Login flow:**

```
POST /api/auth/login { email, password }
  → find user by email (ignore case)
  → reject if inactive or bad password
  → JWT: subject=email, claim roles[], HS256, expiry from app.jwt.expiry-millis
  → client stores token in localStorage
```

**Request auth:**

```
Authorization: Bearer <jwt>
  → JwtAuthFilter parses token
  → SecurityContext: ROLE_<name>
  → @PreAuthorize / filter chain
  → CurrentUser loads User entity by email
```

**Bootstrap (idempotent):** Seeds permission catalog, five roles with permission sets, and SUPER_ADMIN from `SUPERADMIN_EMAIL` / `SUPERADMIN_PASSWORD` if missing.

**Public endpoints (no JWT):**

- `POST /api/auth/login`
- `POST /api/public/leads`
- `POST /api/inbound/**`
- `GET /actuator/health`, `/error`

#### 3.1.3 Lead service rules (LLD)

| Operation | Rules |
|-----------|--------|
| Public capture | Phone required; program maps to course + source; status `NEW`; no owner; activity `CREATED` |
| Manual create | Name + phone required; phone/email dedup; owner & createdBy = actor; status `NEW` |
| Bulk import | Preview (`commit=false`) then commit; within-file + DB dedup; owner = actor |
| List/search | JPA Specification: optional owner scope, `q` (name/email/phone), status, owner/unassigned; DB paging/sort |
| Stage update | Must be in `STAGES`; logs `STAGE_CHANGE`; optional lost reason as `NOTE` |
| Assign | Single / bulk IDs / assign-by-filter (same filters as list); logs `ASSIGNMENT` |
| Edit details | Managers+: any field; Reps: only blank fields; else raise change request |
| Change request | PENDING → APPROVED (applies field) / REJECTED; manager+ decides |
| Email lead | Requires lead email; SMTP send with optional Reply-To token; activity `EMAIL` |
| Delete | SUPER_ADMIN only; cascades activities + change requests |

**Hardcoded programs:**

| Slug | Course name | Website source |
|------|-------------|----------------|
| `agentic-ai` | Agentic AI Engineer | Website - Agentic AI |
| `ai-ml` | Applied AI & ML | Website - Applied AI & ML |
| (other / empty) | free text or null | Website - Need Help / manual source |

#### 3.1.4 Visibility resolution (LLD)

```
visibilityScope(user):
  if SUPER_ADMIN or MARKETING → null   // means "all"
  else → UserService.leadScopeUserIds(user):
           start with subtree(user)   // self + recursive reports
           if user.companyId set:
             for each peer in same company:
               add subtree(peer)
           return that set of owner UUIDs
```

Lead queries with non-null scope filter `ownerId IN scope`.

#### 3.1.5 Email (LLD)

**Outbound:** `EmailService` via `JavaMailSender`. If `spring.mail.host` blank → “Email is not configured”.

**Reply threading (optional):** When `inbound.reply-domain` + `inbound.hmac-secret` set:

```
Reply-To: lead.<uuidhex>.<hmac16>@reply-domain
  → lead replies
  → SendGrid Inbound Parse
  → POST /api/inbound/sendgrid?key=<webhook-secret>
  → verify key (constant-time)
  → ReplyToken verifies HMAC → leadId
  → Activity EMAIL_IN (+ notify owner)
```

**Templates:** CRUD for managers+; active templates available to all sales roles when composing.

### 3.2 API catalog (LLD summary)

| Area | Methods / paths |
|------|-----------------|
| Auth | `POST /api/auth/login` |
| Me | `GET /api/me`, `POST /api/me/password` |
| Public | `POST /api/public/leads` |
| Leads | `GET/POST /api/leads`, `/leads/page`, `/leads/bulk`, `/leads/stats`, `/leads/report`, `/leads/stages`, `/leads/assignees`, `/leads/assign`, `/leads/assign-by-filter`, `/leads/{id}`, `/leads/{id}/details`, notes, email, change-requests |
| Change requests | `GET /api/change-requests`, `/count`, `POST .../approve|reject` |
| Admin users | `GET/POST /api/admin/users`, manager/company patches, delete |
| Companies | `GET/POST/DELETE /api/admin/companies` |
| Templates | `GET /api/email-templates`, manage CRUD |
| Inbound | `POST /api/inbound/sendgrid` |
| Ops | `POST /api/admin/test-email`, health |

### 3.3 Data model (LLD)

#### Tables (logical)

| Table | PK | Notable columns |
|-------|-----|-----------------|
| `users` | UUID | email, password_hash, names, active, manager_id, company_id |
| `roles` | Long | name |
| `permissions` | Long | name |
| `user_roles` | join | user_id, role_id |
| `role_permissions` | join | role_id, permission_id |
| `companies` | UUID | name |
| `leads` | UUID | contact, phone_normalized, source, course_*, message, extra, status, owner_id, created_by, timestamps |
| `lead_activities` | UUID | lead_id, type, body, author, created_at |
| `lead_change_requests` | UUID | lead_id, field, current/requested value, status, requested_by, decided_by, note, timestamps |
| `email_templates` | UUID | name, subject, body, stage, active, created_by |

**Activity types:** `CREATED | NOTE | STAGE_CHANGE | ASSIGNMENT | EMAIL | EMAIL_IN`  
**Change request status:** `PENDING | APPROVED | REJECTED`  
**Lead status:** `NEW | CONTACTED | QUALIFIED | NURTURING | NEGOTIATION | WON | LOST`

#### Schema management

- Production/dev: Hibernate `spring.jpa.hibernate.ddl-auto: update`
- Flyway not in dependencies; introduce before multi-env production hardening
- Bulk import: `hibernate.jdbc.batch_size=100`, ordered inserts/updates

### 3.4 Frontend design (LLD)

#### Structure

```
frontend/src/
  main.tsx, App.tsx, auth.tsx, api.ts, styles.css
  components/Layout.tsx, LmsLogo.tsx
  pages/  Login, Dashboard, Leads, NewLead, BulkImport, LeadDetail,
          Requests, Templates, Reports, Team, Account
```

#### Routing & guards

| Route | Client role gate |
|-------|------------------|
| `/login` | Public |
| `/`, `/account`, `/leads`, `/leads/new`, `/leads/import`, `/leads/:id` | Any authenticated |
| `/requests`, `/templates` | SUPER_ADMIN, ADMIN, SALES_MANAGER |
| `/reports`, `/team` | SUPER_ADMIN, ADMIN |

Server remains source of truth for authorization.

#### Client patterns

- **State:** React Context (`AuthProvider`) + local `useState` only (no Redux/React Query)
- **API:** single `api.ts` fetch wrapper; JWT from `localStorage`; 401 → logout/redirect
- **Bulk import:** `xlsx` parses file in browser; API receives JSON rows
- **UI:** Custom CSS (no component library)

### 3.5 Key sequence diagrams

#### 3.5.1 Website lead capture

```
Website form → POST /api/public/leads
  LeadService.capture → save Lead (NEW) + Activity CREATED
  → { id, status: "received" }
```

#### 3.5.2 Rep works a lead

```
Login → JWT
GET /leads/page (scoped)
GET /leads/{id} + activities
PATCH /leads/{id} { status } → STAGE_CHANGE
POST /leads/{id}/notes
POST /leads/{id}/email → SMTP + Activity EMAIL
```

#### 3.5.3 Field change governance

```
Rep: PATCH /leads/{id}/details (blank OK)
Rep: non-blank → POST change-request
Manager: GET /change-requests → approve
  → field applied + timeline note
```

#### 3.5.4 Bulk import

```
Browser xlsx → check-duplicates (optional)
POST /leads/bulk commit=false (preview)
POST /leads/bulk commit=true → saveAll + CREATED activities
```

### 3.6 Configuration surface

| Variable | Purpose |
|----------|---------|
| `DB_HOST/PORT/NAME/USER/PASSWORD` | Postgres |
| `JWT_SECRET`, `JWT_EXPIRY_MS` | Token signing / lifetime |
| `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD` | Root seed |
| `MAIL_HOST/PORT/USERNAME/PASSWORD` | SMTP |
| `MAIL_FROM`, `MAIL_FROM_NAME` | From header |
| `INBOUND_REPLY_DOMAIN`, `INBOUND_HMAC_SECRET`, `INBOUND_WEBHOOK_SECRET` | Reply threading |
| `APP_FRONTEND_URL` | Links in emails |
| `VITE_API_BASE` | Frontend API base (`/api` in prod) |

### 3.7 Security design notes & known gaps

| Strength | Gap / future hardening |
|----------|------------------------|
| JWT + BCrypt + method security | Permission table unused at enforcement (roles only) |
| Hierarchical visibility | Detail `GET /leads/{id}` may not re-check ownership if UUID is known |
| Webhook secret + HMAC Reply-To | No rate limit on public capture / login |
| Soft-delete users (`active=false`) | CORS `*` patterns; no refresh-token revocation |
| Super-only hard delete leads | Default secrets in `application.yml` for local only — must override in prod |
| | No automated tests / CI pipeline in-repo |
| | Flyway not yet adopted |

### 3.8 Testing & CI (current state)

- Backend: `spring-boot-starter-test` dependency only; **no `src/test` suite**
- Frontend: no Vitest/Jest/Playwright config
- Deploy: manual Docker Compose on VM (no GitHub Actions in repo)

---

## 4. Extension guidance (for new features)

When adding features, prefer:

1. **Package-by-feature** under `com.leadmanagement.lms.<domain>` with Controller → Service → Repository.
2. **Role gates** via `@PreAuthorize` aligned with frontend `RequireAuth` / Layout nav.
3. **Visibility:** if the feature is lead-related, reuse `visibilityScope()` / `leadScopeUserIds`.
4. **Audit:** write `Activity` rows for meaningful lead mutations.
5. **Frontend:** new page under `pages/`, route in `App.tsx`, methods in `api.ts`.
6. **Schema:** if production multi-env, introduce Flyway before large schema changes.
7. **Do not assume classic LMS patterns** exist (enrollment, content CDN, student auth) unless you intentionally build them.

### Suggested future modules (from roadmap / gaps)

| Feature | Suggested package / notes |
|---------|---------------------------|
| Configurable pipeline stages | `pipeline` + `pipeline_stages` table; replace `LeadService.STAGES` |
| Round-robin / SLA | assignment service + `next_follow_up` on lead |
| Marketing campaigns | `campaign` domain; wire `CAMPAIGN_MANAGE` |
| Lead score / ticket size | columns on `leads` + report metrics |
| Real learner LMS | **new product surface** — separate student auth, content, progress; do not overload CRM lead model without a clear domain split |

---

## 5. Related documents

| Doc | Audience |
|-----|----------|
| [README.md](../README.md) | Quick start, roles, stack |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Original short architecture notes |
| [FUNCTIONAL-GUIDE.md](./FUNCTIONAL-GUIDE.md) | How to use the product (non-engineers) |

---

*Generated from multi-agent repository analysis of the current codebase state.*
