import React, { useEffect, useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { api, Lead, Activity, Assignee, ChangeRequest, EmailTemplate, FollowUp } from '../api'
import { useAuth, hasAnyRole } from '../auth'
import { fillTemplate } from '../leadEmailTemplates'

const STAGES = ['NEW', 'CONTACTED', 'QUALIFIED', 'NURTURING', 'NEGOTIATION', 'WON', 'LOST']

function when(s: string) { try { return new Date(s).toLocaleString() } catch { return s } }
function fullName(l: Lead) { return [l.firstName, l.lastName].filter(Boolean).join(' ') || '' }

interface EditForm { name: string; phone: string; email: string; source: string; course: string }
function formFromLead(l: Lead): EditForm {
  return { name: fullName(l), phone: l.phone || '', email: l.email || '', source: l.source || '', course: l.courseId || '' }
}
// Current value of an editable field on the lead (used for the rep "blank only" rule).
function currentVal(l: Lead, key: keyof EditForm): string {
  if (key === 'name') return fullName(l)
  if (key === 'course') return l.courseName || ''
  return (l as any)[key] || ''
}

export default function LeadDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const canWrite = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER', 'SALES_REP'])
  const canAssign = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER'])
  const isManager = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER'])
  const isSuper = hasAnyRole(user, ['SUPER_ADMIN'])

  const [lead, setLead] = useState<Lead | null>(null)
  const [acts, setActs] = useState<Activity[]>([])
  const [assignees, setAssignees] = useState<Assignee[]>([])
  const [requests, setRequests] = useState<ChangeRequest[]>([])
  const [followUps, setFollowUps] = useState<FollowUp[]>([])
  const [fuMessage, setFuMessage] = useState('')
  const [fuDue, setFuDue] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showEmail, setShowEmail] = useState(false)
  const [subj, setSubj] = useState('')
  const [emailBody, setEmailBody] = useState('')
  const [emailOk, setEmailOk] = useState<string | null>(null)
  const [tmpl, setTmpl] = useState('')
  const [templates, setTemplates] = useState<EmailTemplate[]>([])

  // Managers may compose freely; reps/marketing can only send an approved template (content locked).
  const canCompose = isManager

  // Editing contact fields
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<EditForm>({ name: '', phone: '', email: '', source: '', course: '' })
  // Raise-a-change-request inline form
  const [reqField, setReqField] = useState<keyof EditForm | null>(null)
  const [reqValue, setReqValue] = useState('')
  const [reqNote, setReqNote] = useState('')

  function applyTemplate(id: string) {
    setTmpl(id)
    const t = id ? templates.find(x => x.id === id) : null
    if (t && lead) { setSubj(fillTemplate(t.subject, lead)); setEmailBody(fillTemplate(t.body, lead)) }
    else { setSubj(''); setEmailBody('') }
  }

  function openEmail() {
    setEmailOk(null)
    const byStage = lead ? templates.find(t => t.stage === lead.status) : undefined
    applyTemplate(byStage ? byStage.id : (canCompose ? '' : (templates[0]?.id || '')))
    setShowEmail(true)
  }

  const load = () => {
    api.getLead(id).then(setLead).catch(e => setError(e.message))
    api.leadActivities(id).then(setActs).catch(() => {})
    api.leadChangeRequests(id).then(setRequests).catch(() => {})
    api.leadFollowUps(id).then(setFollowUps).catch(() => {})
  }
  useEffect(() => {
    load()
    if (canAssign) api.assignees().then(setAssignees).catch(() => {})
    if (canWrite) api.emailTemplates().then(setTemplates).catch(() => {})
  }, [id])

  async function setStage(stage: string) {
    if (!lead || stage === lead.status || busy) return
    setBusy(true); setError(null)
    let lostReason: string | undefined
    if (stage === 'LOST') lostReason = window.prompt('Lost reason (optional):') || undefined
    try {
      await api.updateLead(id, { status: stage, lostReason })
      load()
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  async function assign(ownerId: string) {
    setBusy(true); setError(null)
    try { await api.updateLead(id, { ownerId }); load() }
    catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  function startEdit() {
    if (!lead) return
    setForm(formFromLead(lead)); setReqField(null); setError(null); setEditing(true)
  }

  async function saveEdit() {
    if (!lead) return
    const payload: Record<string, string> = {}
    const keys: (keyof EditForm)[] = ['name', 'phone', 'email', 'source', 'course']
    for (const k of keys) {
      const locked = !isManager && currentVal(lead, k).trim() !== ''
      if (locked) continue
      if (form[k] !== currentVal(lead, k)) payload[k] = form[k]
    }
    if (Object.keys(payload).length === 0) { setEditing(false); return }
    setBusy(true); setError(null)
    try { await api.editLeadDetails(id, payload); setEditing(false); load() }
    catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  function openRequest(key: keyof EditForm) {
    setReqField(key); setReqValue(lead ? currentVal(lead, key) : ''); setReqNote('')
  }

  async function submitRequest() {
    if (!reqField || !reqValue.trim()) return
    setBusy(true); setError(null)
    try {
      await api.raiseChangeRequest(id, reqField, reqValue.trim(), reqNote.trim() || undefined)
      setReqField(null); setEditing(false); load()
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  async function decide(rid: string, approve: boolean) {
    let note: string | undefined
    if (!approve) note = window.prompt('Reason for rejecting (optional):') || undefined
    setBusy(true); setError(null)
    try { await api.decideChangeRequest(rid, approve, note); load() }
    catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  async function removeLead() {
    if (!window.confirm(`Delete this lead and its activity? This cannot be undone.`)) return
    setBusy(true); setError(null)
    try { await api.deleteLead(id); navigate('/leads') }
    catch (e: any) { setError(e.message); setBusy(false) }
  }

  async function sendEmail(e: React.FormEvent) {
    e.preventDefault()
    if (!subj.trim() || !emailBody.trim()) return
    setBusy(true); setError(null); setEmailOk(null)
    try {
      await api.emailLead(id, subj.trim(), emailBody.trim())
      setSubj(''); setEmailBody(''); setShowEmail(false)
      setEmailOk(`Email sent to ${lead?.email}`)
      api.leadActivities(id).then(setActs)
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  async function submitNote(e: React.FormEvent) {
    e.preventDefault()
    if (!note.trim()) return
    setBusy(true); setError(null)
    try { await api.addNote(id, note.trim()); setNote(''); api.leadActivities(id).then(setActs) }
    catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  async function submitFollowUp(e: React.FormEvent) {
    e.preventDefault()
    if (!fuMessage.trim() || !fuDue) return
    setBusy(true); setError(null)
    try {
      // datetime-local → LocalDateTime string backend accepts
      await api.createFollowUp(id, fuMessage.trim(), fuDue)
      setFuMessage(''); setFuDue('')
      load()
    } catch (err: any) { setError(err.message) } finally { setBusy(false) }
  }

  async function completeFu(fid: string) {
    setBusy(true); setError(null)
    try { await api.completeFollowUp(fid); load() }
    catch (err: any) { setError(err.message) } finally { setBusy(false) }
  }

  if (!lead) return <div className="page">{error ? <div className="err">{error}</div> : <div className="muted">Loading…</div>}</div>

  const curIdx = STAGES.indexOf(lead.status)
  let extraObj: Record<string, string> = {}
  try { extraObj = lead.extra ? JSON.parse(lead.extra) : {} } catch { /* ignore */ }
  const pendingReqs = requests.filter(r => r.status === 'PENDING')
  const decidedReqs = requests.filter(r => r.status !== 'PENDING')

  // Render an editable field row (handles the rep "blank only" lock + request action).
  function editRow(key: keyof EditForm, label: string) {
    const locked = !isManager && currentVal(lead!, key).trim() !== ''
    if (key === 'course') {
      return (
        <div className="kv" key={key}><span>{label}</span><div>
          <select value={form.course} disabled={locked} onChange={e => setForm({ ...form, course: e.target.value })} style={{ padding: '4px 8px' }}>
            <option value="">Not sure yet</option>
            <option value="agentic-ai">Agentic AI Engineer</option>
            <option value="ai-ml">Applied AI &amp; ML</option>
          </select>
          {locked && <a className="muted" style={{ marginLeft: 8, color: 'var(--indigo)', cursor: 'pointer', fontSize: 13 }} onClick={() => openRequest(key)}>Request change</a>}
        </div></div>
      )
    }
    return (
      <div className="kv" key={key}><span>{label}</span><div>
        <input value={form[key]} disabled={locked} onChange={e => setForm({ ...form, [key]: e.target.value })}
          placeholder={locked ? '' : `Add ${label.toLowerCase()}`} style={{ padding: '4px 8px', maxWidth: 200 }} />
        {locked && <a className="muted" style={{ marginLeft: 8, color: 'var(--indigo)', cursor: 'pointer', fontSize: 13 }} onClick={() => openRequest(key)}>Request change</a>}
      </div></div>
    )
  }

  return (
    <div className="page">
      <Link to="/leads" className="muted" style={{ fontSize: 14 }}>← Back to leads</Link>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '6px 0 4px' }}>
        <h1 style={{ margin: 0 }}>{fullName(lead) || '—'}</h1>
        <span className={`badge ${lead.status}`}>{lead.status}</span>
        {isSuper && <button className="btn ghost" style={{ marginLeft: 'auto', color: '#b91c1c', borderColor: '#fecaca' }} onClick={removeLead} disabled={busy}>Delete lead</button>}
      </div>
      <div className="sub">{lead.courseName || 'No program'} · {lead.source || '—'}</div>
      {error && <div className="err">{error}</div>}

      {/* Pipeline stage bar (D365 business process flow) */}
      <div className="stagebar">
        {STAGES.map((s, i) => {
          const cls = i < curIdx ? 'done' : i === curIdx ? 'current' : 'todo'
          return (
            <button key={s} className={`stagestep ${cls}`} disabled={!canWrite || busy} onClick={() => setStage(s)} title={canWrite ? 'Set stage' : ''}>
              <span className="dot">{i < curIdx ? '✓' : i + 1}</span>{s}
            </button>
          )
        })}
      </div>

      <div className="row" style={{ alignItems: 'flex-start', marginTop: 22 }}>
        {/* Left: contact + owner + requests */}
        <div style={{ flex: '1 1 300px', maxWidth: 360 }}>
          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <b>Contact</b>
              {canWrite && !editing && <button className="btn ghost" style={{ marginLeft: 'auto', padding: '4px 10px' }} onClick={startEdit}>Edit</button>}
            </div>
            {!editing ? (
              <>
                <div className="kv"><span>Name</span><div>{fullName(lead) || '—'}</div></div>
                <div className="kv"><span>Phone</span><div>{lead.phone}</div></div>
                <div className="kv"><span>Email</span><div>{lead.email || <span className="muted">Not set</span>}</div></div>
                <div className="kv"><span>Course</span><div>{lead.courseName || 'Not sure yet'}</div></div>
                <div className="kv"><span>Source</span><div>{lead.source || '—'}</div></div>
                <div className="kv"><span>Created</span><div>{when(lead.createdAt)}</div></div>
                {lead.createdByName && <div className="kv"><span>Added by</span><div>{lead.createdByName}</div></div>}
                {lead.message && <div className="kv"><span>Message</span><div>{lead.message}</div></div>}
                {Object.entries(extraObj).map(([k, v]) => (
                  <div className="kv" key={k}><span>{k}</span><div>{v}</div></div>
                ))}
              </>
            ) : (
              <>
                {!isManager && <div className="muted" style={{ fontSize: 13, margin: '8px 0' }}>You can fill in blank fields. For fields that already have a value, use “Request change”.</div>}
                {editRow('name', 'Name')}
                {editRow('phone', 'Phone')}
                {editRow('email', 'Email')}
                {editRow('course', 'Course')}
                {editRow('source', 'Source')}
                {reqField && (
                  <div className="card" style={{ marginTop: 10, background: '#f8fafc' }}>
                    <b style={{ fontSize: 14 }}>Request change · {reqField}</b>
                    <input value={reqValue} onChange={e => setReqValue(e.target.value)} placeholder="New value" style={{ marginTop: 8 }} />
                    <input value={reqNote} onChange={e => setReqNote(e.target.value)} placeholder="Reason (optional)" style={{ marginTop: 8 }} />
                    <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                      <button className="btn" onClick={submitRequest} disabled={busy || !reqValue.trim()}>Send request</button>
                      <button className="btn ghost" onClick={() => setReqField(null)}>Cancel</button>
                    </div>
                  </div>
                )}
                <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
                  <button className="btn" onClick={saveEdit} disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
                  <button className="btn ghost" onClick={() => { setEditing(false); setReqField(null) }}>Cancel</button>
                </div>
              </>
            )}
          </div>

          <div className="card" style={{ marginTop: 16 }}>
            <b>Owner</b>
            {canAssign ? (
              <select style={{ marginTop: 10 }} value={lead.ownerId || ''} onChange={e => assign(e.target.value)} disabled={busy}>
                <option value="">Unassigned</option>
                {assignees.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            ) : (
              <div style={{ marginTop: 8 }} className="muted">{lead.ownerName || 'Unassigned'}</div>
            )}
          </div>

          <div className="card" style={{ marginTop: 16 }}>
            <b>Follow-ups</b>
            <div className="muted" style={{ fontSize: 13, marginTop: 4, marginBottom: 10 }}>
              Schedule a next step — it appears on the <Link to="/" style={{ color: 'var(--indigo)' }}>Dashboard</Link> for everyone who can see this lead.
            </div>
            {canWrite && (
              <form onSubmit={submitFollowUp} style={{ marginBottom: 14 }}>
                <textarea rows={2} value={fuMessage} onChange={e => setFuMessage(e.target.value)}
                  placeholder="What to do (call back, send brochure, confirm batch…)" style={{ marginBottom: 8 }} />
                <label className="muted" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>Follow-up time</label>
                <input type="datetime-local" value={fuDue} onChange={e => setFuDue(e.target.value)} style={{ marginBottom: 8 }} />
                <button className="btn" style={{ width: '100%' }} disabled={busy || !fuMessage.trim() || !fuDue}>
                  {busy ? 'Saving…' : 'Schedule follow-up'}
                </button>
              </form>
            )}
            {followUps.length === 0 && <div className="muted" style={{ fontSize: 13 }}>No follow-ups yet.</div>}
            {followUps.map(f => (
              <div key={f.id} style={{ padding: '8px 0', borderTop: '1px solid var(--line)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <span className={`badge ${f.status === 'PENDING' ? 'NEGOTIATION' : 'WON'}`}>{f.status}</span>
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{when(f.dueAt)}</span>
                </div>
                <div style={{ marginTop: 4, whiteSpace: 'pre-wrap', fontSize: 14 }}>{f.message}</div>
                <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>
                  by {f.createdBy || '—'}
                  {f.status === 'DONE' && f.completedBy ? ` · done by ${f.completedBy}` : ''}
                </div>
                {f.status === 'PENDING' && canWrite && (
                  <button className="btn ghost" style={{ padding: '4px 10px', marginTop: 6 }}
                    disabled={busy} onClick={() => completeFu(f.id)}>Mark complete</button>
                )}
              </div>
            ))}
          </div>

          {requests.length > 0 && (
            <div className="card" style={{ marginTop: 16 }}>
              <b>Change requests</b>
              {pendingReqs.map(r => (
                <div key={r.id} className="kv" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                  <div><b>{r.fieldLabel}</b>: <span className="muted">{r.currentValue || '(blank)'}</span> → {r.requestedValue}</div>
                  <div className="muted" style={{ fontSize: 12 }}>by {r.requestedBy}{r.note ? ` · ${r.note}` : ''}</div>
                  {isManager ? (
                    <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
                      <button className="btn" style={{ padding: '4px 10px' }} onClick={() => decide(r.id, true)} disabled={busy}>Approve</button>
                      <button className="btn ghost" style={{ padding: '4px 10px' }} onClick={() => decide(r.id, false)} disabled={busy}>Reject</button>
                    </div>
                  ) : <span className="bk DUPLICATE" style={{ marginTop: 4 }}>Pending</span>}
                </div>
              ))}
              {decidedReqs.map(r => (
                <div key={r.id} className="kv" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                  <div><b>{r.fieldLabel}</b> → {r.requestedValue} <span className={`bk ${r.status === 'APPROVED' ? 'NEW' : 'INVALID'}`}>{r.status}</span></div>
                  <div className="muted" style={{ fontSize: 12 }}>by {r.requestedBy}{r.decidedBy ? ` · decided by ${r.decidedBy}` : ''}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right: activity timeline */}
        <div style={{ flex: '2 1 460px' }}>
          <div className="card">
            <b>Activity</b>
            {emailOk && <div className="ok" style={{ marginTop: 10 }}>{emailOk}</div>}
            {canWrite && (
              <div style={{ margin: '12px 0' }}>
                {lead.email ? (
                  <>
                    {!showEmail && <button className="btn ghost" onClick={openEmail}>✉ Email this lead</button>}
                    {showEmail && (
                      <form onSubmit={sendEmail}>
                        <select value={tmpl} onChange={e => applyTemplate(e.target.value)} style={{ marginBottom: 8 }}>
                          {canCompose && <option value="">Blank email</option>}
                          {!canCompose && templates.length === 0 && <option value="">No templates available</option>}
                          {!canCompose && templates.length > 0 && tmpl === '' && <option value="">Select a template…</option>}
                          {templates.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                        </select>
                        {!canCompose && (
                          <div className="muted" style={{ fontSize: 13, marginBottom: 8 }}>
                            Emails are official communication — choose an approved template. A manager can add or edit templates.
                          </div>
                        )}
                        <input placeholder="Subject" value={subj} readOnly={!canCompose}
                          onChange={e => setSubj(e.target.value)} style={{ marginBottom: 8, background: canCompose ? undefined : '#f8fafc' }} />
                        <textarea rows={5} placeholder={`Message to ${lead.email}`} value={emailBody} readOnly={!canCompose}
                          onChange={e => setEmailBody(e.target.value)} style={{ background: canCompose ? undefined : '#f8fafc' }} />
                        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                          <button className="btn" disabled={busy || !subj.trim() || !emailBody.trim() || (!canCompose && !tmpl)}>{busy ? 'Sending…' : 'Send email'}</button>
                          <button type="button" className="btn ghost" onClick={() => setShowEmail(false)}>Cancel</button>
                        </div>
                      </form>
                    )}
                  </>
                ) : (
                  <span className="muted" style={{ fontSize: 13 }}>No email on file. Add one via <b>Edit</b> to email this lead.</span>
                )}
              </div>
            )}
            {canWrite && (
              <form onSubmit={submitNote} style={{ margin: '12px 0 18px' }}>
                <textarea rows={2} value={note} onChange={e => setNote(e.target.value)} placeholder="Add a note (call summary, next step…)" />
                <button className="btn" style={{ marginTop: 8 }} disabled={busy || !note.trim()}>Add note</button>
              </form>
            )}
            <div className="timeline">
              {acts.length === 0 && <div className="muted">No activity yet.</div>}
              {acts.map(a => (
                <div className="tl" key={a.id}>
                  <div className={`tlicon ${a.type}`}>{a.type === 'NOTE' ? '📝' : a.type === 'EMAIL' ? '📧' : a.type === 'EMAIL_IN' ? '📥' : a.type === 'STAGE_CHANGE' ? '↗' : a.type === 'ASSIGNMENT' ? '👤' : a.type === 'FOLLOW_UP' ? '⏰' : '•'}</div>
                  <div className="tlbody">
                    <div style={{ whiteSpace: 'pre-wrap' }}>{a.body}</div>
                    <div className="muted" style={{ fontSize: 12 }}>{a.author || 'system'} · {when(a.createdAt)}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
