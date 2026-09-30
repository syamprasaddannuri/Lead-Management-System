import React, { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

export default function Account() {
  const { user, refreshUser, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const forced = !!(location.state as any)?.forcePassword || !!user?.mustChangePassword

  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState(false)

  const canSave = current && next.length >= 8 && next === confirm && !busy

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null); setOk(false)
    if (next !== confirm) { setError('New passwords do not match.'); return }
    if (next.length < 8) { setError('New password must be at least 8 characters.'); return }
    if (next === '123456') { setError('Please choose a password other than the temporary default.'); return }
    setBusy(true)
    try {
      await api.changePassword(current, next)
      setOk(true); setCurrent(''); setNext(''); setConfirm('')
      await refreshUser()
      if (forced) navigate('/', { replace: true })
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <div className="page">
      <h1>Account</h1>
      <div className="sub">Signed in as {user?.email}</div>

      {forced && (
        <div className="card" style={{ marginBottom: 16, borderLeft: '4px solid var(--indigo)' }}>
          <b>Update your password</b>
          <p className="muted" style={{ marginBottom: 0 }}>
            You signed in with a temporary password. Choose a new password to continue using the sales console.
          </p>
        </div>
      )}

      <form className="card" onSubmit={submit} style={{ maxWidth: 440 }}>
        <b>{forced ? 'Set a permanent password' : 'Change password'}</b>
        {ok && <div className="ok" style={{ marginTop: 12 }}>Password updated.</div>}
        {error && <div className="err" style={{ marginTop: 12 }}>{error}</div>}
        <label style={{ marginTop: 14 }}>Current / temporary password</label>
        <input type="password" value={current} onChange={e => setCurrent(e.target.value)} autoComplete="current-password" />
        <label style={{ marginTop: 12 }}>New password</label>
        <input type="password" value={next} onChange={e => setNext(e.target.value)} autoComplete="new-password" placeholder="At least 8 characters" />
        <label style={{ marginTop: 12 }}>Confirm new password</label>
        <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)} autoComplete="new-password" />
        <button className="btn" style={{ marginTop: 16 }} disabled={!canSave}>{busy ? 'Saving…' : 'Update password'}</button>
        {forced && (
          <button type="button" className="btn ghost" style={{ marginTop: 10, marginLeft: 8 }} onClick={logout}>
            Sign out
          </button>
        )}
      </form>
    </div>
  )
}
