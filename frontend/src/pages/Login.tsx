import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { api, ApiError } from '../api'
import LmsLogo from '../components/LmsLogo'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState<'login' | 'forgot'>('login')

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null); setOk(null); setBusy(true)
    try {
      if (mode === 'forgot') {
        const res = await api.forgotPassword(email.trim())
        setOk(res.message || 'If that account exists, a temporary password was issued.')
        setMode('login')
        setPassword('')
      } else {
        const me = await login(email, password)
        if (me.mustChangePassword) navigate('/account', { replace: true, state: { forcePassword: true } })
        else navigate('/', { replace: true })
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : mode === 'forgot' ? 'Request failed' : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="box" onSubmit={onSubmit}>
        <div className="logo"><LmsLogo height={30} /></div>
        <h1 style={{ fontSize: 20, margin: '0 0 4px' }}>
          {mode === 'login' ? 'Sales console' : 'Forgot password'}
        </h1>
        <p className="muted" style={{ fontSize: 14, marginTop: 0, marginBottom: 20 }}>
          {mode === 'login'
            ? 'Sign in to manage leads.'
            : 'Enter your work email. If an account exists, a temporary password will be issued so you can sign in and set a new one.'}
        </p>
        {error && <div className="err">{error}</div>}
        {ok && <div className="ok">{ok}</div>}
        <div className="field">
          <label>Email</label>
          <input type="email" value={email} onChange={e => setEmail(e.target.value)} required autoFocus />
        </div>
        {mode === 'login' && (
          <div className="field">
            <label>Password</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} required />
          </div>
        )}
        <button className="btn" style={{ width: '100%' }} disabled={busy}>
          {busy ? (mode === 'login' ? 'Signing in…' : 'Sending…') : (mode === 'login' ? 'Sign in' : 'Reset password')}
        </button>
        <div style={{ marginTop: 14, textAlign: 'center' }}>
          {mode === 'login' ? (
            <button type="button" className="btn ghost" style={{ border: 'none', color: 'var(--indigo)' }}
              onClick={() => { setMode('forgot'); setError(null); setOk(null) }}>
              Forgot password?
            </button>
          ) : (
            <button type="button" className="btn ghost" style={{ border: 'none', color: 'var(--indigo)' }}
              onClick={() => { setMode('login'); setError(null); setOk(null) }}>
              Back to sign in
            </button>
          )}
        </div>
      </form>
    </div>
  )
}
