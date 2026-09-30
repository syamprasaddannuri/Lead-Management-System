import React, { useEffect, useState } from 'react'
import { api, EmailTemplate } from '../api'

const STAGES = ['', 'NEW', 'CONTACTED', 'QUALIFIED', 'NURTURING', 'NEGOTIATION', 'WON', 'LOST']
const EMPTY = { name: '', subject: '', body: '', stage: '', active: true }

export default function Templates() {
  const [list, setList] = useState<EmailTemplate[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [editId, setEditId] = useState<string | null>(null) // null = not editing, '' = creating new
  const [form, setForm] = useState({ ...EMPTY })

  function load() { api.manageTemplates().then(setList).catch(e => setError(e.message)) }
  useEffect(() => { load() }, [])

  function startNew() { setForm({ ...EMPTY }); setEditId(''); setError(null) }
  function startEdit(t: EmailTemplate) {
    setForm({ name: t.name, subject: t.subject, body: t.body, stage: t.stage || '', active: t.active })
    setEditId(t.id); setError(null)
  }
  function cancel() { setEditId(null) }

  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim() || !form.subject.trim() || !form.body.trim()) return
    setBusy(true); setError(null)
    const payload = { name: form.name.trim(), subject: form.subject.trim(), body: form.body.trim(), stage: form.stage || undefined, active: form.active }
    try {
      if (editId) await api.updateTemplate(editId, payload)
      else await api.createTemplate(payload)
      setEditId(null); load()
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  async function remove(t: EmailTemplate) {
    if (!window.confirm(`Delete template "${t.name}"?`)) return
    setBusy(true); setError(null)
    try { await api.deleteTemplate(t.id); load() }
    catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <div className="page">
      <div style={{ display: 'flex', alignItems: 'center' }}>
        <h1 style={{ margin: 0 }}>Email templates</h1>
        {editId === null && <button className="btn" style={{ marginLeft: 'auto' }} onClick={startNew}>+ New template</button>}
      </div>
      <div className="sub">
        Approved emails for leads. You see templates created by you, your managers (up), your reports (down), and your peers — plus system defaults.
        Use <code>{'{firstName}'}</code> and <code>{'{program}'}</code> as merge fields.
      </div>
      {error && <div className="err">{error}</div>}

      {editId !== null && (
        <form className="card" onSubmit={save} style={{ marginBottom: 18 }}>
          <b>{editId ? 'Edit template' : 'New template'}</b>
          <label style={{ marginTop: 10 }}>Name</label>
          <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="e.g. Qualified — next steps" autoFocus />
          <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
            <div style={{ flex: 1 }}>
              <label>Stage (optional)</label>
              <select value={form.stage} onChange={e => setForm({ ...form, stage: e.target.value })}>
                {STAGES.map(s => <option key={s} value={s}>{s || 'Any stage'}</option>)}
              </select>
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 22 }}>
              <input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Active
            </label>
          </div>
          <label style={{ marginTop: 12 }}>Subject</label>
          <input value={form.subject} onChange={e => setForm({ ...form, subject: e.target.value })} placeholder="Next steps for {program}" />
          <label style={{ marginTop: 12 }}>Body</label>
          <textarea rows={8} value={form.body} onChange={e => setForm({ ...form, body: e.target.value })} placeholder={'Hi {firstName},\n\n…'} />
          <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <button className="btn" disabled={busy || !form.name.trim() || !form.subject.trim() || !form.body.trim()}>{busy ? 'Saving…' : 'Save template'}</button>
            <button type="button" className="btn ghost" onClick={cancel}>Cancel</button>
          </div>
        </form>
      )}

      {!list && !error && <div className="muted">Loading…</div>}
      {list && list.length === 0 && editId === null && <div className="card muted">No templates yet.</div>}
      {list && list.length > 0 && (
        <div className="tablewrap"><table>
          <thead><tr><th>Name</th><th>Stage</th><th>Subject</th><th>By</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {list.map(t => (
              <tr key={t.id}>
                <td><b>{t.name}</b></td>
                <td className="muted">{t.stage || 'Any'}</td>
                <td className="muted">{t.subject}</td>
                <td className="muted" style={{ fontSize: 13 }}>{t.createdBy === 'system' ? 'System' : (t.createdBy || '—')}</td>
                <td><span className={`bk ${t.active ? 'NEW' : 'INVALID'}`}>{t.active ? 'Active' : 'Inactive'}</span></td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  {t.canEdit !== false && (
                    <>
                      <button className="btn ghost" style={{ padding: '4px 10px' }} onClick={() => startEdit(t)}>Edit</button>
                      <button className="btn ghost" style={{ padding: '4px 10px', marginLeft: 6, color: '#b91c1c', borderColor: '#fecaca' }} onClick={() => remove(t)}>Delete</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </div>
  )
}
