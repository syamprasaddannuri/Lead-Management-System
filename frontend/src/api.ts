const BASE = (import.meta.env.VITE_API_BASE as string) || 'http://localhost:8089/api'

export interface MeView {
  id: string
  email: string
  firstName: string | null
  lastName: string | null
  active: boolean
  mustChangePassword?: boolean
  roles: string[]
  managerId?: string | null
  managerName?: string | null
  companyId?: string | null
  companyName?: string | null
  peerGroupId?: string | null
  peerGroupName?: string | null
  createdAt: string
}

export interface CompanyMember { id: string; name: string; roles: string[] }
export interface Company { id: string; name: string; members: CompanyMember[] }
export interface PeerGroup {
  id: string
  name: string
  createdById?: string | null
  createdByName?: string | null
  members: CompanyMember[]
}

export interface Lead {
  id: string
  firstName: string | null
  lastName: string | null
  email: string | null
  phone: string
  source: string | null
  courseId: string | null
  courseName: string | null
  message: string | null
  extra: string | null
  status: string
  ownerId: string | null
  ownerName: string | null
  createdById: string | null
  createdByName: string | null
  createdAt: string
}

export interface Activity {
  id: string
  type: string
  body: string | null
  author: string | null
  createdAt: string
}

export interface FollowUp {
  id: string
  leadId: string
  leadName: string
  leadPhone: string | null
  leadStatus: string | null
  message: string
  dueAt: string
  status: string
  createdBy: string | null
  completedBy: string | null
  completedAt: string | null
  createdAt: string
}

export interface ChangeRequest {
  id: string
  leadId: string
  leadName: string
  field: string
  fieldLabel: string
  currentValue: string | null
  requestedValue: string | null
  status: string
  requestedBy: string | null
  decidedBy: string | null
  note: string | null
  createdAt: string
  decidedAt: string | null
}

export interface Assignee {
  id: string
  name: string
  email: string
}

export interface EmailTemplate {
  id: string
  name: string
  subject: string
  body: string
  stage: string | null
  active: boolean
  createdBy: string | null
  createdAt: string
  /** Viewer may edit/delete this template (org write policy). */
  canEdit?: boolean
}

export interface BulkInputRow { name: string; email?: string; phone: string; program?: string; source?: string; extra?: Record<string, string> }
export interface NewLeadInput { name: string; phone: string; email?: string; program?: string; source?: string; message?: string }
export interface BulkRowResult {
  row: number; name: string; email: string; phone: string
  courseName: string | null; source: string | null; status: string; message: string
}
export interface BulkResult {
  total: number; toImport: number; duplicates: number; invalid: number; created: number; rows: BulkRowResult[]
}

export interface LeadsPage {
  content: Lead[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface LeadStats { total: number; byStatus: Record<string, number> }
export interface Bucket { key: string; count: number }
export interface Report {
  total: number
  open: number
  won: number
  lost: number
  days: number | null
  periodLabel: string
  conversionRate: number
  byStatus: Bucket[]
  bySource: Bucket[]
  byProgram: Bucket[]
  byOwner: Bucket[]
}
/** SUPER_ADMIN only — outbound email volume by who sent / which system trigger. */
export interface EmailStats {
  total: number
  days: number | null
  periodLabel: string
  bySender: Bucket[]
  byCategory: Bucket[]
}

export interface LoginResponse {
  token: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
  mustChangePassword?: boolean
}

export class ApiError extends Error {
  status: number
  data: any
  constructor(status: number, message: string, data?: any) {
    super(message)
    this.status = status
    this.data = data
  }
}

export function getToken(): string | null { return localStorage.getItem('token') }
export function setToken(t: string) { localStorage.setItem('token', t) }
export function clearToken() { localStorage.removeItem('token') }

async function request<T = any>(
  path: string,
  opts: { method?: string; body?: any; auth?: boolean } = {}
): Promise<T> {
  const { method = 'GET', body, auth = true } = opts
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const t = getToken()
    if (t) headers['Authorization'] = `Bearer ${t}`
  }
  let res: Response
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch (e: any) {
    throw new ApiError(0, e?.message || 'Network error')
  }
  if (res.status === 401) {
    clearToken()
    if (!window.location.pathname.endsWith('/login')) window.location.href = '/login'
    throw new ApiError(401, 'Unauthorized')
  }
  const text = await res.text()
  let data: any = null
  if (text) { try { data = JSON.parse(text) } catch { data = text } }
  if (!res.ok) {
    throw new ApiError(res.status, (data && (data.error || data.message)) || res.statusText || 'Request failed', data)
  }
  return data as T
}

/** Digits-only, last-10 form of a phone — mirrors the backend, for client-side dedup. */
export function normalizePhone(phone: string): string {
  const digits = (phone || '').replace(/\D/g, '')
  return digits.length > 10 ? digits.slice(-10) : digits
}

export const api = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  forgotPassword: (email: string) =>
    request<{ status: string; message: string }>('/auth/forgot-password', {
      method: 'POST', body: { email }, auth: false,
    }),
  me: () => request<MeView>('/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ status: string }>('/me/password', { method: 'POST', body: { currentPassword, newPassword } }),
  listLeads: () => request<Lead[]>('/leads'),
  leadStats: () => request<LeadStats>('/leads/stats'),
  listLeadsPaged: (page: number, size: number,
    opts?: { q?: string; status?: string; owner?: string; sort?: string; dir?: string }) => {
    const p = new URLSearchParams({ page: String(page), size: String(size) })
    if (opts?.q) p.set('q', opts.q)
    if (opts?.status) p.set('status', opts.status)
    if (opts?.owner) p.set('owner', opts.owner)
    if (opts?.sort) p.set('sort', opts.sort)
    if (opts?.dir) p.set('dir', opts.dir)
    return request<LeadsPage>(`/leads/page?${p.toString()}`)
  },
  leadReport: (days?: number | null) => {
    const q = days != null && days > 0 ? `?days=${days}` : ''
    return request<Report>(`/leads/report${q}`)
  },
  /** Root admin only: emails sent (by person and by system trigger). */
  emailStats: (days?: number | null) => {
    const q = days != null && days > 0 ? `?days=${days}` : ''
    return request<EmailStats>(`/admin/email-stats${q}`)
  },
  bulkLeads: (commit: boolean, rows: BulkInputRow[]) =>
    request<BulkResult>('/leads/bulk', { method: 'POST', body: { commit, rows } }),
  checkDuplicates: (phones: string[], emails: string[]) =>
    request<{ phones: string[]; emails: string[] }>('/leads/check-duplicates', { method: 'POST', body: { phones, emails } }),
  createLead: (payload: NewLeadInput) => request<Lead>('/leads', { method: 'POST', body: payload }),
  assignLeads: (leadIds: string[], ownerId: string) =>
    request<{ assigned: number }>('/leads/assign', { method: 'POST', body: { leadIds, ownerId } }),
  assignLeadsByFilter: (filters: { q?: string; status?: string; owner?: string }, ownerId: string) =>
    request<{ assigned: number }>('/leads/assign-by-filter', {
      method: 'POST',
      body: { q: filters.q, status: filters.status, owner: filters.owner, ownerId },
    }),
  getLead: (id: string) => request<Lead>(`/leads/${id}`),
  updateLead: (id: string, payload: { status?: string; ownerId?: string; lostReason?: string; program?: string }) =>
    request<Lead>(`/leads/${id}`, { method: 'PATCH', body: payload }),
  editLeadDetails: (id: string, payload: { name?: string; email?: string; phone?: string; source?: string; course?: string }) =>
    request<Lead>(`/leads/${id}/details`, { method: 'PATCH', body: payload }),
  leadChangeRequests: (id: string) => request<ChangeRequest[]>(`/leads/${id}/change-requests`),
  raiseChangeRequest: (id: string, field: string, requestedValue: string, note?: string) =>
    request<ChangeRequest>(`/leads/${id}/change-requests`, { method: 'POST', body: { field, requestedValue, note } }),
  pendingChangeRequests: () => request<ChangeRequest[]>('/change-requests'),
  pendingChangeRequestCount: () => request<{ pending: number }>('/change-requests/count'),
  decideChangeRequest: (rid: string, approve: boolean, note?: string) =>
    request<ChangeRequest>(`/change-requests/${rid}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: { note } }),
  deleteLead: (id: string) => request<void>(`/leads/${id}`, { method: 'DELETE' }),
  leadActivities: (id: string) => request<Activity[]>(`/leads/${id}/activities`),
  addNote: (id: string, body: string) => request<Activity>(`/leads/${id}/notes`, { method: 'POST', body: { body } }),
  emailLead: (id: string, subject: string, body: string) =>
    request<Activity>(`/leads/${id}/email`, { method: 'POST', body: { subject, body } }),
  createFollowUp: (id: string, message: string, dueAt: string) =>
    request<FollowUp>(`/leads/${id}/follow-ups`, { method: 'POST', body: { message, dueAt } }),
  leadFollowUps: (id: string) => request<FollowUp[]>(`/leads/${id}/follow-ups`),
  pendingFollowUps: () => request<FollowUp[]>('/follow-ups/pending'),
  completeFollowUp: (id: string) =>
    request<FollowUp>(`/follow-ups/${id}/complete`, { method: 'POST' }),
  assignees: () => request<Assignee[]>('/leads/assignees'),
  emailTemplates: () => request<EmailTemplate[]>('/email-templates'),
  manageTemplates: () => request<EmailTemplate[]>('/email-templates/manage'),
  createTemplate: (payload: { name: string; subject: string; body: string; stage?: string; active?: boolean }) =>
    request<EmailTemplate>('/email-templates', { method: 'POST', body: payload }),
  updateTemplate: (id: string, payload: { name: string; subject: string; body: string; stage?: string; active?: boolean }) =>
    request<EmailTemplate>(`/email-templates/${id}`, { method: 'PUT', body: payload }),
  deleteTemplate: (id: string) => request<void>(`/email-templates/${id}`, { method: 'DELETE' }),
  listUsers: () => request<MeView[]>('/admin/users'),
  createStaff: (payload: { email: string; firstName?: string; lastName?: string; password?: string; role: string; managerId?: string }) =>
    request<MeView>('/admin/users', { method: 'POST', body: payload }),
  setManager: (id: string, managerId: string) =>
    request<MeView>(`/admin/users/${id}/manager`, { method: 'PATCH', body: { managerId } }),
  removeUser: (id: string) => request<void>(`/admin/users/${id}`, { method: 'DELETE' }),
  listCompanies: () => request<Company[]>('/admin/companies'),
  createCompany: (name: string) => request<Company>('/admin/companies', { method: 'POST', body: { name } }),
  deleteCompany: (id: string) => request<void>(`/admin/companies/${id}`, { method: 'DELETE' }),
  setUserCompany: (id: string, companyId: string) =>
    request<MeView>(`/admin/users/${id}/company`, { method: 'PATCH', body: { companyId } }),
  listPeerGroups: () => request<PeerGroup[]>('/admin/peer-groups'),
  createPeerGroup: (name: string) => request<PeerGroup>('/admin/peer-groups', { method: 'POST', body: { name } }),
  deletePeerGroup: (id: string) => request<void>(`/admin/peer-groups/${id}`, { method: 'DELETE' }),
  setUserPeerGroup: (id: string, peerGroupId: string) =>
    request<MeView>(`/admin/users/${id}/peer-group`, { method: 'PATCH', body: { peerGroupId } }),
}
