import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api'

export default function NewLead() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [program, setProgram] = useState('')
  const [source, setSource] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dupLeadId, setDupLeadId] = useState<string | null>(null)

  const canSave = name.trim() && phone.trim() && !busy

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSave) return
    setBusy(true); setError(null); setDupLeadId(null)
    try {
      const lead = await api.createLead({
        name: name.trim(), phone: phone.trim(),
        email: email.trim() || undefined,
        program: program || undefined,
        source: source.trim() || undefined,
        message: message.trim() || undefined,
      })
      navigate(`/leads/${lead.id}`)
    } catch (e: any) {
      setError(e.message)
      setDupLeadId(e?.data?.leadId || null)
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <Link to="/leads" className="muted" style={{ fontSize: 14 }}>← Back to leads</Link>
      <h1 style={{ marginTop: 6 }}>Add a lead</h1>
      <div className="sub">Capture a single lead. It will be assigned to you. Name and phone are required.</div>
      {error && <div className="err">{error}{dupLeadId && <> · <Link to={`/leads/${dupLeadId}`} style={{ fontWeight: 700, textDecoration: 'underline' }}>View existing lead</Link></>}</div>}

      <form className="card" onSubmit={submit} style={{ maxWidth: 520 }}>
        <label>Name <span style={{ color: '#e11d48' }}>*</span></label>
        <input value={name} onChange={e => setName(e.target.value)} placeholder="Full name" autoFocus />

        <label style={{ marginTop: 12 }}>Phone <span style={{ color: '#e11d48' }}>*</span></label>
        <input value={phone} onChange={e => setPhone(e.target.value)} placeholder="Mobile number" />

        <label style={{ marginTop: 12 }}>Email</label>
        <input value={email} onChange={e => setEmail(e.target.value)} placeholder="Optional" type="email" />

        <label style={{ marginTop: 12 }}>Program / Course</label>
        <select value={program} onChange={e => setProgram(e.target.value)}>
          <option value="">Not sure yet</option>
          <option value="agentic-ai">Agentic AI Engineer</option>
          <option value="ai-ml">Applied AI &amp; ML</option>
        </select>

        <label style={{ marginTop: 12 }}>Source</label>
        <input value={source} onChange={e => setSource(e.target.value)} placeholder="e.g. Referral, Event, LinkedIn" />

        <label style={{ marginTop: 12 }}>Note</label>
        <textarea rows={3} value={message} onChange={e => setMessage(e.target.value)} placeholder="Anything useful about this lead (optional)" />

        <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
          <button className="btn" disabled={!canSave}>{busy ? 'Saving…' : 'Add lead'}</button>
          <Link to="/leads" className="btn ghost">Cancel</Link>
        </div>
      </form>
    </div>
  )
}
