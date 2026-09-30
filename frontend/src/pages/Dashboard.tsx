import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, LeadStats, FollowUp, EmailStats } from '../api'
import { useAuth, hasAnyRole } from '../auth'

function when(s: string) {
  try { return new Date(s).toLocaleString() } catch { return s }
}

function dueLabel(dueAt: string): { text: string; cls: string } {
  const due = new Date(dueAt).getTime()
  const now = Date.now()
  const day = 24 * 60 * 60 * 1000
  if (due < now) return { text: 'Overdue', cls: 'LOST' }
  if (due - now < day) return { text: 'Due soon', cls: 'NEGOTIATION' }
  return { text: 'Upcoming', cls: 'NURTURING' }
}

const EMAIL_PERIODS: { label: string; days: number | null }[] = [
  { label: '7 days', days: 7 },
  { label: '30 days', days: 30 },
  { label: 'All time', days: null },
]

export default function Dashboard() {
  const { user } = useAuth()
  const isSuper = hasAnyRole(user, ['SUPER_ADMIN'])
  const [stats, setStats] = useState<LeadStats | null>(null)
  const [followUps, setFollowUps] = useState<FollowUp[] | null>(null)
  const [emailStats, setEmailStats] = useState<EmailStats | null>(null)
  const [emailDays, setEmailDays] = useState<number | null>(7)
  const [emailBusy, setEmailBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = () => {
    api.leadStats().then(setStats).catch(e => setError(e.message))
    api.pendingFollowUps().then(setFollowUps).catch(e => setError(e.message))
  }
  useEffect(() => { load() }, [])

  useEffect(() => {
    if (!isSuper) return
    setEmailBusy(true)
    api.emailStats(emailDays)
      .then(setEmailStats)
      .catch(e => setError(e.message))
      .finally(() => setEmailBusy(false))
  }, [isSuper, emailDays])

  async function complete(id: string) {
    setBusyId(id); setError(null)
    try {
      await api.completeFollowUp(id)
      setFollowUps(prev => (prev || []).filter(f => f.id !== id))
    } catch (e: any) {
      setError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  const total = stats?.total ?? 0
  const isNew = stats?.byStatus?.NEW ?? 0
  const won = stats?.byStatus?.WON ?? 0
  const pendingFu = followUps?.length ?? 0
  const greeting = user?.firstName || user?.email?.split('@')[0] || 'there'
  const overdue = (followUps || []).filter(f => new Date(f.dueAt).getTime() < Date.now()).length

  return (
    <div className="page">
      <h1>Welcome, {greeting}</h1>
      <div className="sub">Here's your lead snapshot and follow-ups.</div>
      {error && <div className="err">{error}</div>}
      <div className="row" style={{ marginBottom: 22 }}>
        <div className="stat"><b>{total}</b><span>Total leads</span></div>
        <div className="stat"><b>{isNew}</b><span>New / untouched</span></div>
        <div className="stat"><b>{won}</b><span>Won (enrolled)</span></div>
        <div className="stat"><b>{pendingFu}</b><span>Open follow-ups{overdue > 0 ? ` · ${overdue} overdue` : ''}</span></div>
      </div>

      <div className="card" style={{ marginBottom: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
          <b>Follow-ups</b>
          {pendingFu > 0 && <span className="badge NEGOTIATION">{pendingFu}</span>}
        </div>
        <div className="sub" style={{ marginTop: 0, marginBottom: 12 }}>
          Scheduled call-backs and next steps for leads you can see. Complete them when done — they stay on the lead timeline.
        </div>
        {followUps === null && <div className="muted">Loading follow-ups…</div>}
        {followUps && followUps.length === 0 && (
          <div className="muted">No open follow-ups. Open a lead and schedule one with a message and time.</div>
        )}
        {followUps && followUps.length > 0 && (
          <div className="tablewrap">
            <table>
              <thead>
                <tr>
                  <th>Due</th>
                  <th>Lead</th>
                  <th>Message</th>
                  <th>Set by</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {followUps.map(f => {
                  const label = dueLabel(f.dueAt)
                  return (
                    <tr key={f.id}>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <div>{when(f.dueAt)}</div>
                        <span className={`badge ${label.cls}`} style={{ marginTop: 4 }}>{label.text}</span>
                      </td>
                      <td>
                        <Link to={`/leads/${f.leadId}`} style={{ color: 'var(--indigo)', fontWeight: 600 }}>
                          {f.leadName}
                        </Link>
                        <div className="muted" style={{ fontSize: 12 }}>
                          {f.leadPhone || '—'}
                          {f.leadStatus ? ` · ${f.leadStatus}` : ''}
                        </div>
                      </td>
                      <td style={{ maxWidth: 320 }}>
                        <div style={{ whiteSpace: 'pre-wrap' }}>{f.message}</div>
                      </td>
                      <td className="muted" style={{ fontSize: 13 }}>{f.createdBy || '—'}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <Link to={`/leads/${f.leadId}`} className="btn ghost" style={{ padding: '4px 10px', marginRight: 6 }}>
                          Open lead
                        </Link>
                        <button
                          className="btn"
                          style={{ padding: '4px 10px' }}
                          disabled={busyId === f.id}
                          onClick={() => complete(f.id)}
                        >
                          {busyId === f.id ? '…' : 'Complete'}
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isSuper && (
        <div className="card" style={{ marginBottom: 18 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <b>Email send stats</b>
            <span className="badge NEW" style={{ fontSize: 11 }}>Root admin only</span>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {EMAIL_PERIODS.map(p => (
                <button
                  key={p.label}
                  type="button"
                  className={`btn ${emailDays === p.days ? '' : 'ghost'}`}
                  style={{ padding: '4px 12px', fontSize: 13 }}
                  onClick={() => setEmailDays(p.days)}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
          <div className="sub" style={{ marginTop: 0, marginBottom: 14 }}>
            How many emails the system sent, and who sent them (staff names for lead emails;
            system buckets like Login triggers for invites / password resets).
            {emailStats ? <> Showing: <b>{emailStats.periodLabel}</b>.</> : null}
          </div>
          {emailBusy && !emailStats && <div className="muted">Loading email stats…</div>}
          {emailStats && (
            <>
              <div className="row" style={{ marginBottom: 14 }}>
                <div className="stat" style={{ minWidth: 140 }}>
                  <b>{emailStats.total}</b>
                  <span>Total emails sent</span>
                </div>
                {emailStats.bySender.slice(0, 6).map(s => (
                  <div className="stat" key={s.key} style={{ minWidth: 140 }}>
                    <b>{s.count}</b>
                    <span>{s.key}</span>
                  </div>
                ))}
              </div>
              {emailStats.bySender.length === 0 && (
                <div className="muted">No emails recorded in this period yet.</div>
              )}
              {emailStats.bySender.length > 0 && (
                <div className="tablewrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Sender / trigger</th>
                        <th style={{ textAlign: 'right' }}>Emails</th>
                      </tr>
                    </thead>
                    <tbody>
                      {emailStats.bySender.map(s => (
                        <tr key={s.key}>
                          <td style={{ fontWeight: 600 }}>{s.key}</td>
                          <td style={{ textAlign: 'right' }}>{s.count}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {emailStats.byCategory.length > 0 && (
                <div className="muted" style={{ marginTop: 12, fontSize: 13 }}>
                  By type:{' '}
                  {emailStats.byCategory.map((c, i) => (
                    <span key={c.key}>
                      {i > 0 ? ' · ' : ''}
                      <b>{c.key}</b> {c.count}
                    </span>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      )}

      <div className="card">
        <b>Quick links</b>
        <p className="muted" style={{ marginBottom: 0 }}>
          Go to <Link style={{ color: 'var(--indigo)', fontWeight: 600 }} to="/leads">Leads</Link> to work the pipeline
          {user && user.roles.some(r => ['SUPER_ADMIN', 'ADMIN'].includes(r)) && (
            <>, or <Link style={{ color: 'var(--indigo)', fontWeight: 600 }} to="/team">Team</Link> to add staff</>
          )}.
        </p>
      </div>
    </div>
  )
}
