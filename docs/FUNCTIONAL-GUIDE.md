# Lead Management System Sales Console — Functional Guide

**For:** Sales, marketing, operations, and leadership (non-engineering)  
**Product name in tech:** Lead Management System / CRM  
**Where to open it:** [https://sales.example.com](https://sales.example.com)

This guide explains **what the system is for**, **who can do what**, and **how to use each screen** in plain language. No technical setup is required to follow it.

---

## 1. What is this product?

The sales console is a **lead and pipeline tool** for the team that sells training programs (for example *Agentic AI Engineer* and *Applied AI & ML*).

It helps you:

- Capture interest from the website and from lists you import  
- Assign leads to the right sales people  
- Move each lead through clear sales stages until they enroll (Won) or drop out (Lost)  
- Keep a full history of notes, emails, and stage changes  
- Email prospects from inside the tool  
- See simple reports on how the funnel is performing  

### Important: “LMS” does not mean classroom software here

Despite the internal project name “LMS,” this is **not** a student learning portal. Students do not log in here to take courses or quizzes. This is the **sales CRM** used by Lead Management System staff. When you see “course” or “program” on a lead, it means **which training the person is interested in**, not course content.

---

## 2. Who uses it? (roles)

Your account is created by an admin. There is **no public “Sign up”** for staff.

| Role | Typical person | What they can do |
|------|----------------|------------------|
| **Super Admin** | Platform owner | Everything: manage companies, all users, all leads, delete leads, full settings |
| **Admin** | Ops / sales leadership | Manage team, see reports, work all leads in scope, assign work |
| **Sales Manager** | Team lead | Work leads in their branch, assign to reps, approve field-change requests, manage email templates |
| **Sales Rep** | Individual seller | Work **their** leads (and their branch rules), add notes, send emails, move stages; limited ability to change contact details |
| **Marketing** | Growth / demand gen | View leads across the funnel (campaign tools are still limited) |

### How “who sees which leads” works (simple version)

- **Super Admin** and **Marketing** can see **all** leads.  
- **Managers and reps** mainly see leads owned by **themselves and the people who report to them**.  
- If Super Admin puts people in the same **Company** group, those people can also see each other’s teams’ leads (useful for partner or multi-branch setups).

If you cannot find a lead, it is often because it is owned by someone outside your branch. Ask your manager or admin.

---

## 3. Getting started

### 3.1 Log in

1. Go to **https://sales.example.com**  
2. Enter the **email** and **password** your admin shared with you  
3. You land on the **Dashboard**

If you forget your password, ask an Admin or Super Admin to reset access (or use **Account** to change password once you can log in).

### 3.2 Change your password

1. Click your **email / avatar** (top right) → **Account**  
2. Enter current password and a new password  
3. Save  

### 3.3 Navigation bar (what each menu means)

| Menu | Who sees it | Purpose |
|------|-------------|---------|
| **Dashboard** | Everyone | Snapshot of lead counts by stage |
| **Leads** | Everyone | Main worklist — find, open, and manage leads |
| **Requests** | Managers and above | Approve or reject reps’ requests to fix lead details |
| **Templates** | Managers and above | Create reusable email templates |
| **Reports** | Admin and Super Admin | Funnel analytics |
| **Team** | Admin and Super Admin | Add staff, set managers, companies |
| **Account** | Everyone | Profile / password |
| **Logout** | Everyone | Sign out |

---

## 4. Understanding a “Lead”

A **lead** is one prospective student (or enquirer). Typical fields:

| Field | Meaning |
|-------|---------|
| **Name** | Contact name |
| **Phone** | Required; used to avoid duplicates |
| **Email** | Optional but needed to email from the platform |
| **Source** | Where they came from (website form, manual entry, import, campaign name, etc.) |
| **Program / Course** | Interest: e.g. Agentic AI Engineer, Applied AI & ML |
| **Message** | What they wrote on the website form (if any) |
| **Status (stage)** | Where they are in the sales pipeline |
| **Owner** | The staff member responsible for following up |
| **Created by** | Who added them (blank for pure website captures) |

### Pipeline stages (in order)

Think of these as steps in a CRM, similar to Salesforce / Dynamics:

| Stage | Meaning (plain English) |
|-------|-------------------------|
| **NEW** | Just arrived; not worked yet |
| **CONTACTED** | You reached out (call / email / WhatsApp) |
| **QUALIFIED** | Real fit — budget, interest, timeline look good |
| **NURTURING** | Interested but not ready; keep warming |
| **NEGOTIATION** | Discussing offer, batch, pricing, seat |
| **WON** | Enrolled / closed-won (sales success) |
| **LOST** | Not proceeding; ideally capture a reason |

Move stages as the conversation progresses so dashboards and reports stay honest.

---

## 5. Day-to-day workflows

### 5.1 Website leads (automatic)

When someone fills an apply / “need help” form on **example.com**, a lead is created automatically:

- Status starts as **NEW**  
- Source and program are tagged from the form  
- Usually **unassigned** until a manager assigns it  

**What sales should do:** open **Leads**, filter status **NEW** (or Unassigned), claim or wait for assignment, then contact and move the stage.

### 5.2 Add one lead manually

Use this when someone calls, DMs, or walks in.

1. Go to **Leads** → **Add lead** (or `/leads/new`)  
2. Enter **Name** and **Phone** (required)  
3. Optionally add email, program, source, message  
4. Save  

You become the **owner** and **creator**. The system blocks exact phone (and email) duplicates.

### 5.3 Bulk import (Excel / CSV)

Use this for event lists, partner lists, or old sheets.

1. Go to **Leads** → **Import**  
2. Upload a spreadsheet (common columns: name, phone, email, program, source)  
3. Review the **preview**: valid rows, duplicates, invalid rows  
4. Confirm import only when the preview looks right  

**Tips:**

- Phone is the main identity — clean phone numbers first  
- Duplicates against existing CRM data are skipped or flagged  
- Extra unknown columns may be stored for reference  
- Sample files live with the engineering team under `docs/sample-leads.csv`

### 5.4 Work a lead (the main sales loop)

1. Open **Leads**  
2. Search by name, email, or phone; filter by stage or owner  
3. Open a lead  
4. On the detail page you can:  
   - **Change stage** along the pipeline bar  
   - **Add notes** (calls, WhatsApp summary, objections)  
   - **Send email** if the lead has an email address  
   - **Update program** if interest changes  
   - (Managers) **Reassign owner**  

Everything important is written on the **timeline** so the next person sees the full story.

### 5.5 Assign leads

**Managers and admins** can:

- Assign **one** lead from its detail page  
- Select **many** leads on the list and assign in bulk  
- Assign **everyone matching a filter** (e.g. all unassigned NEW leads to a rep) without clicking each row  

Reps typically receive work already assigned to them.

### 5.6 Email a lead from the platform

1. Open the lead (must have an email)  
2. Compose a message or pick a **template**  
3. Send  

The email is logged on the timeline.  
If inbound reply routing is configured by the platform team, **when the lead replies**, that reply can also appear on the same timeline.

**Note for reps:** You may be required to use approved templates rather than free-form body text, depending on role rules on the screen.

### 5.7 Correcting lead details (reps vs managers)

| Who | Can edit contact fields? |
|-----|---------------------------|
| **Managers / Admins** | Yes — name, email, phone, source, course |
| **Sales Reps** | Can fill **empty** fields freely. To change a field that already has a value, they must raise a **change request** |

**Change request flow:**

1. Rep requests a change (e.g. wrong phone) with the new value and optional note  
2. Manager opens **Requests** (badge shows pending count)  
3. Manager **Approves** (applies the change) or **Rejects**  
4. Timeline records the decision  

This protects data quality while still letting reps fix mistakes with oversight.

---

## 6. Screens explained

### 6.1 Dashboard

Quick counts of leads by status in **your visibility scope**. Use it for a morning pulse: How many NEW? How many in negotiation? Any stuck pipeline?

### 6.2 Leads list

Your main workbench:

- Search box  
- Filters: stage, owner, unassigned  
- Pagination for large databases  
- Links to add / import  
- Bulk actions for managers (assign)  

### 6.3 Lead detail

Everything about one person:

- Contact card  
- Stage control  
- Owner  
- Timeline (notes, emails, stage and assignment history)  
- Email composer  
- Change requests for that lead  

### 6.4 Requests

Manager queue of pending field-change approvals. Clear this regularly so reps are not blocked.

### 6.5 Templates

Reusable emails (subject + body), optionally tied to a stage (e.g. first contact, follow-up, offer). Keeps outreach consistent and faster for reps.

### 6.6 Reports (Admin+)

High-level funnel health, for example:

- Total / open / won / lost  
- Recent activity (last 7 / 30 days)  
- Conversion rate  
- Breakdowns by stage, source, program, owner  

Use this in weekly sales reviews.

### 6.7 Team (Admin+)

- Create staff accounts and assign roles  
- Set **manager** (reporting line — drives who sees whose leads)  
- Super Admin: create **companies** and attach users for shared visibility  
- Deactivate people who leave (their reports can be re-parented up the tree)

**Staff onboarding tip:** When you create a user, they receive access credentials (often a temporary password by email if mail is configured). Share the login URL: https://sales.example.com

---

## 7. Recommended operating playbooks

### 7.1 Daily sales rep checklist

1. Open Dashboard — note your open pipeline  
2. Leads → filter **your name** + stages you own today  
3. Prioritize **NEW** and overdue follow-ups  
4. Log every call/WhatsApp as a **note**  
5. Move stage when reality changes  
6. Send templated emails where useful  
7. Raise change requests for bad contact data  

### 7.2 Daily / weekly manager checklist

1. Assign unassigned NEW leads promptly  
2. Review stuck stages (e.g. long in NURTURING)  
3. Clear **Requests** queue  
4. Spot-check timelines for quality of notes  
5. Keep email templates current  
6. (Admin) Review **Reports** for conversion and source quality  

### 7.3 Marketing / demand gen

1. Ensure website forms still create clean leads (phone required)  
2. Use clear **source** names on imports (campaign name, event name)  
3. Review Reports by source/program with sales leadership  
4. Campaign automation inside this tool is still limited — treat this as the system of record for **leads and sales outcome**, not full marketing automation  

### 7.4 What “good hygiene” looks like

- Every active lead has an **owner**  
- Stages reflect real conversations (no vanity WON)  
- Phone/email kept accurate via the change-request process  
- Notes written so a colleague can take over without a handover call  
- Lost reasons noted when moving to LOST  

---

## 8. Common questions (FAQ)

**Q: Can a student log in to take classes here?**  
No. This is staff-only sales software.

**Q: Why can’t I see a lead my colleague has?**  
Visibility follows the reporting tree (and company grouping). Ask your manager to assign the lead or adjust the org chart in **Team**.

**Q: Why did import skip some rows?**  
Usually duplicate phone or email already in the system, or missing required fields (especially phone).

**Q: Email failed to send.**  
The lead may have no email, or the platform email service may not be configured. Contact Super Admin / engineering.

**Q: I need a new program name (new course the company is selling).**  
Today’s website mapping mainly knows **Agentic AI** and **Applied AI & ML**. Free-text / import can carry other names; ask engineering if you need first-class website mapping for a new program.

**Q: Can I delete a lead?**  
Only Super Admin. Prefer **LOST** with a reason for normal sales outcomes.

**Q: Is data shared outside the company?**  
The console is for internal staff. Leads come from company web properties and lists you import. Follow company privacy practices when emailing prospects.

---

## 9. Glossary

| Term | Meaning |
|------|---------|
| **Lead** | A prospect record in the CRM |
| **Owner** | Staff responsible for the lead |
| **Stage / Status** | Pipeline step (NEW … WON/LOST) |
| **Activity / Timeline** | History of notes, emails, stage and assignment changes |
| **Template** | Saved email subject + body for reuse |
| **Change request** | Rep’s proposal to edit a locked contact field |
| **Company (in Team)** | Grouping so multiple branches share lead visibility |
| **WON** | Sales success / enrolled (business outcome) |
| **Scope** | The set of leads your role is allowed to see |

---

## 10. Where to get help

| Need | Who |
|------|-----|
| Access / password / new user | Admin or Super Admin via **Team** |
| Wrong permissions or missing menu | Admin (role assignment) |
| Website form not creating leads | Engineering |
| Email / reply threading issues | Engineering / Super Admin |
| How should our pipeline process work? | Sales leadership (process); this guide (how the tool supports it) |

---

## 11. Related documents (for technical partners)

| Document | Content |
|----------|---------|
| [HLD-LLD.md](./HLD-LLD.md) | Technical high-level and low-level design |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Short architecture notes |
| [README.md](../README.md) | Repo overview for developers |

---

*This guide describes the product as it works today on sales.example.com. Feature additions may extend menus and roles; when that happens, update this document so non-engineering teams stay aligned.*
