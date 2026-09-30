# Lead Management System / CRM

A focused lead-management + CRM platform for sales and marketing teams.
Served at **sales.example.com**. Base reference: the `skill-mist-backend` repo (auth, RBAC,
lead model, and SendGrid email are adapted from there).

## What it does
- **Super admin → adds admins / sales / marketing.** A root user manages all staff and roles;
  there is no public signup for staff (invite-based onboarding).
- **Lead capture.** Public website forms (example.com apply / "need help") create **leads**,
  tagged by program and source.
- **Sales pipeline.** Leads move through CRM stages (D365 / Salesforce style), with assignment,
  follow-ups, activities, and dashboards.
- **Email outreach from the platform.** Sales/marketing reply to leads and email students directly
  from the app (templates + history). Delivery via **SendGrid** (GCP-friendly; sender `team@example.com`).

## Roles
| Role | Can |
|---|---|
| **SUPER_ADMIN** (root) | Everything: manage all users/roles, pipeline config, all leads, settings |
| **ADMIN** | Manage staff + leads, dashboards |
| **SALES_MANAGER** | All leads, assignment/round-robin, pipeline config, manage reps |
| **SALES_REP** | Work only their assigned leads |
| **MARKETING** | Sources/campaigns, top-of-funnel, email campaigns, attribution dashboards |

## Stack
- **Backend:** Java 17+ · Spring Boot · Spring Security (JWT) · Spring Data JPA · Flyway · PostgreSQL
- **Frontend:** React + TypeScript (Vite or CRA), served at sales.example.com
- **Email:** SendGrid (transactional + templates)
- **Cloud:** GCP — Cloud Run (API) or the existing VM, Cloud SQL (Postgres), Secret Manager

## Repo layout
```
backend/    Spring Boot API (auth, RBAC, leads, email)
frontend/   sales.example.com CRM UI
deploy/     Caddy + compose / Cloud Run deploy
docs/       ARCHITECTURE.md and notes
```

## Roadmap
1. **Foundation** — users, RBAC, JWT auth, **super-admin bootstrap**, staff invite/onboarding.
2. **Leads** — lead model + public capture endpoint, list/detail, follow-ups, assignment.
3. **Pipeline** — configurable CRM stages, activities/timeline, dashboards.
4. **Email** — SendGrid integration: send replies + templated outreach from the platform, with history.
5. **Marketing** — campaigns/sources/UTM + attribution.
6. **Deploy** — sales.example.com on the shared box (Caddy) → Cloud SQL + SendGrid.

See:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — short architecture notes  
- [docs/HLD-LLD.md](docs/HLD-LLD.md) — full high-level & low-level design (current state)  
- [docs/FUNCTIONAL-GUIDE.md](docs/FUNCTIONAL-GUIDE.md) — how to use the product (non-engineering)
