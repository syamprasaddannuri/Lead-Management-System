import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ChangeRequest } from '../api'

function when(s: string) { try { return new Date(s).toLocaleString() } catch { return s } }

export default function Requests() {
  const [reqs, setReqs] = useState<ChangeRequest[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function load() { api.pendingChangeRequests().then(setReqs).catch(e => setError(e.message)) }
  useEffect(() => { load() }, [])

  async function decide(r: ChangeRequest, approve: boolean) {
    let note: string | undefined
    if (!approve) note = window.prompt('Reason for rejecting (optional):') || undefined
    setBusy(true); setError(null)
    try {
      await api.decideChangeRequest(r.id, approve, note)
      setReqs(prev => (prev || []).filter(x => x.id !== r.id))
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <div className="page">
      <h1 style={{ margin: 0 }}>Change requests</h1>
      <div className="sub">Field changes raised by sales reps, waiting for your approval.</div>
      {error && <div className="err">{error}</div>}
      {!reqs && !error && <div className="muted">Loading…</div>}
      {reqs && reqs.length === 0 && <div className="card muted">No pending requests.</div>}
      {reqs && reqs.length > 0 && (
        <div className="tablewrap"><table>
          <thead>
            <tr><th>Lead</th><th>Field</th><th>Current</th><th>Requested</th><th>By</th><th>When</th><th></th></tr>
          </thead>
          <tbody>
            {reqs.map(r => (
              <tr key={r.id}>
                <td><Link to={`/leads/${r.leadId}`}><b>{r.leadName}</b></Link></td>
                <td>{r.fieldLabel}</td>
                <td className="muted">{r.currentValue || '(blank)'}</td>
                <td><b>{r.requestedValue}</b>{r.note && <div className="muted" style={{ fontSize: 12 }}>{r.note}</div>}</td>
                <td className="muted">{r.requestedBy}</td>
                <td className="muted">{when(r.createdAt)}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="btn" style={{ padding: '4px 10px' }} onClick={() => decide(r, true)} disabled={busy}>Approve</button>
                  <button className="btn ghost" style={{ padding: '4px 10px', marginLeft: 6 }} onClick={() => decide(r, false)} disabled={busy}>Reject</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </div>
  )
}
