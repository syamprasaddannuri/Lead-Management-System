# Architecture

## Identity & RBAC
- `users`, `roles`, `permissions`, `user_roles`, `role_permissions` (adapted from skill-mist-backend,
  which already ships this model).
- **Role hierarchy:** SUPER_ADMIN > ADMIN > SALES_MANAGER > SALES_REP; MARKETING is standalone.
- Enforce by **permission** (e.g. `lead.read.all`, `lead.read.own`, `lead.assign`, `pipeline.config`,
  `campaign.manage`, `user.invite`, `email.send`) so profiles work like SFDC permission-sets.
- **Super-admin bootstrap:** seeded on first boot from env/secret (`SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`),
  so there's always a root account. SUPER_ADMIN then invites everyone else.
- **Staff onboarding:** SUPER_ADMIN / SALES_MANAGER invites by email → invite link → set password.
  No public signup for staff.

## Leads
- `leads`: name, email, phone, source, program/course, stage, owner (assigned user), score,
  ticket size, next_follow_up, lost_reason, timestamps.
- **Capture:** public `POST /api/public/leads` (no auth) from example.com forms → status NEW, tagged by
  program (agentic-ai / ai-ml) and source. (Already prototyped in skill-mist-backend.)
- **Activities/timeline:** notes, calls, emails, stage changes — one audit trail per lead.
- **Assignment:** manual + round-robin; SLA timer on next_follow_up.

## Pipeline (configurable)
- `pipeline_stages` table (ordered, named) so the team edits stages without code (D365 business-process-flow style).
- Default: New → Contacted → Qualified → Nurturing → Offer/Negotiation → Won (Enrolled) / Lost (reason).
- Marketing funnel in front: Captured → MQL → SQL handoff.

## Email (from the platform)
- **Provider: SendGrid** (GCP-friendly; reuses skill-mist-backend's EmailService and the verified
  `team@example.com` sender). API key in Secret Manager.
  - *Why not "native Google":* GCP has no first-party transactional email API. The Google route is
    Gmail / Workspace API (OAuth + Workspace domain + send limits) — heavier; SendGrid is the standard.
- **Features:** reply to a lead from the lead detail; templated outreach to students/leads; per-lead
  email history; (later) marketing campaigns + open/click tracking via SendGrid.

## Deployment
- **sales.example.com** → the shared VM (`lead-management-vm`, wildcard `*.example.com` already points here),
  behind Caddy (auto-HTTPS): `sales.example.com { reverse_proxy <sales-frontend> }`.
- **API:** start on the same box (compose) or Cloud Run; **DB:** Cloud SQL (or Postgres on the box);
  secrets in Secret Manager. One instance now, splittable later.
