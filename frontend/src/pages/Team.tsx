import React, { useEffect, useMemo, useState } from 'react'
import { api, MeView, Company, PeerGroup } from '../api'
import { useAuth, hasAnyRole } from '../auth'

export default function Team() {
  const { user } = useAuth()
  const isSuper = hasAnyRole(user, ['SUPER_ADMIN'])
  const isAdmin = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN'])
  const roleOptions = isSuper
    ? ['ADMIN', 'SALES_MANAGER', 'SALES_REP', 'MARKETING']
    : ['SALES_MANAGER', 'SALES_REP', 'MARKETING']

  const [users, setUsers] = useState<MeView[] | null>(null)
  const [companies, setCompanies] = useState<Company[]>([])
  const [peerGroups, setPeerGroups] = useState<PeerGroup[]>([])
  const [newCompany, setNewCompany] = useState('')
  const [newPeerGroup, setNewPeerGroup] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState<string | null>(null)
  const [form, setForm] = useState({ email: '', firstName: '', lastName: '', role: roleOptions[0], managerId: '' })
  const [busy, setBusy] = useState(false)
  /** Default: hide deactivated staff. */
  const [showRemoved, setShowRemoved] = useState(false)

  const load = () => api.listUsers().then(setUsers).catch(e => setError(e.message))
  const loadCompanies = () => { if (isSuper) api.listCompanies().then(setCompanies).catch(() => {}) }
  const loadPeerGroups = () => { if (isAdmin) api.listPeerGroups().then(setPeerGroups).catch(() => {}) }
  useEffect(() => { load(); loadCompanies(); loadPeerGroups() }, [])

  async function addCompany() {
    if (!newCompany.trim()) return
    setError(null)
    try { await api.createCompany(newCompany.trim()); setNewCompany(''); loadCompanies() }
    catch (e: any) { setError(e.message) }
  }
  async function removeCompany(c: Company) {
    if (!window.confirm(`Delete company "${c.name}"? Its members will be ungrouped.`)) return
    setError(null)
    try { await api.deleteCompany(c.id); loadCompanies(); load() }
    catch (e: any) { setError(e.message) }
  }
  async function assignCompany(userId: string, companyId: string) {
    setError(null)
    try { await api.setUserCompany(userId, companyId); load(); loadCompanies() }
    catch (e: any) { setError(e.message) }
  }

  async function addPeerGroup() {
    if (!newPeerGroup.trim()) return
    setError(null)
    try { await api.createPeerGroup(newPeerGroup.trim()); setNewPeerGroup(''); loadPeerGroups() }
    catch (e: any) { setError(e.message) }
  }
  async function removePeerGroup(g: PeerGroup) {
    if (!window.confirm(`Delete peer group "${g.name}"? Its members will stop sharing leads with each other.`)) return
    setError(null)
    try { await api.deletePeerGroup(g.id); loadPeerGroups(); load() }
    catch (e: any) { setError(e.message) }
  }
  async function assignPeerGroup(userId: string, peerGroupId: string) {
    setError(null); setOk(null)
    try {
      await api.setUserPeerGroup(userId, peerGroupId)
      setOk(peerGroupId ? 'Peer group updated — members of the same group can see each other\'s leads.' : 'Peer group cleared.')
      load(); loadPeerGroups()
    } catch (e: any) { setError(e.message) }
  }

  const set = (k: string, v: string) => setForm(f => ({ ...f, [k]: v }))
  const nameOf = (u: MeView) => [u.firstName, u.lastName].filter(Boolean).join(' ') || u.email

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null); setOk(null); setBusy(true)
    try {
      await api.createStaff({
        email: form.email, firstName: form.firstName, lastName: form.lastName,
        role: form.role,
        managerId: form.managerId || undefined,
      })
      setOk(`Added ${form.email} as ${form.role}. They’ll get a temporary password and must change it on first sign-in.`)
      setForm({ email: '', firstName: '', lastName: '', role: roleOptions[0], managerId: '' })
      load()
    } catch (err: any) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function setMgr(userId: string, managerId: string) {
    setError(null)
    try { await api.setManager(userId, managerId); load() }
    catch (err: any) { setError(err.message) }
  }

  // Descendants of the current user (for the "remove only your reports" rule).
  const myDescendants = (() => {
    const set = new Set<string>()
    if (!users || !user) return set
    const stack = users.filter(u => u.managerId === user.id).map(u => u.id)
    while (stack.length) {
      const id = stack.pop() as string
      if (set.has(id)) continue
      set.add(id)
      users.filter(u => u.managerId === id).forEach(u => stack.push(u.id))
    }
    return set
  })()
  const canRemove = (u: MeView) => u.active && u.id !== user?.id && (isSuper || myDescendants.has(u.id))
  /** Super: anyone. Admin: self + reports. */
  const canAssignPeer = (u: MeView) => u.active && isAdmin && (isSuper || u.id === user?.id || myDescendants.has(u.id))

  async function removeUser(u: MeView) {
    if (!window.confirm(`Remove ${nameOf(u)}? They will be deactivated and can no longer sign in. Their reports move up to their manager.`)) return
    setError(null); setOk(null)
    try { await api.removeUser(u.id); setOk(`Removed ${nameOf(u)}.`); load() }
    catch (err: any) { setError(err.message) }
  }

  const visibleUsers = useMemo(() => {
    if (!users) return []
    return showRemoved ? users : users.filter(u => u.active)
  }, [users, showRemoved])

  const removedCount = users ? users.filter(u => !u.active).length : 0
  const activeCount = users ? users.filter(u => u.active).length : 0

  function renderNode(u: MeView, all: MeView[], depth = 0): JSX.Element {
    const kids = all.filter(c => c.managerId === u.id)
    return (
      <li key={u.id}>
        <div className={`org-node ${u.id === user?.id ? 'me' : ''}`}>
          <div className="org-name">
            {nameOf(u)}
            {u.peerGroupName && <span className="badge" style={{ marginLeft: 6, fontSize: 10 }} title="Peer group">{u.peerGroupName}</span>}
          </div>
          <div className="org-role">{u.roles.join(', ')}</div>
        </div>
        {kids.length > 0 && depth < 25 && <ul>{kids.map(c => renderNode(c, all, depth + 1))}</ul>}
      </li>
    )
  }

  return (
    <div className="page">
      <h1>Team</h1>
      <div className="sub">Add admins, sales and marketing staff. {isSuper ? 'As super admin you can also add admins.' : ''}</div>

      <div className="row" style={{ alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: '1 1 320px', maxWidth: 380 }}>
          <b>Add a team member</b>
          <form onSubmit={onSubmit} style={{ marginTop: 14 }}>
            {ok && <div className="ok">{ok}</div>}
            {error && <div className="err">{error}</div>}
            <div className="field"><label>Email</label>
              <input type="email" value={form.email} onChange={e => set('email', e.target.value)} required /></div>
            <div className="row">
              <div className="field" style={{ flex: 1 }}><label>First name</label>
                <input value={form.firstName} onChange={e => set('firstName', e.target.value)} /></div>
              <div className="field" style={{ flex: 1 }}><label>Last name</label>
                <input value={form.lastName} onChange={e => set('lastName', e.target.value)} /></div>
            </div>
            <div className="field"><label>Role</label>
              <select value={form.role} onChange={e => set('role', e.target.value)}>
                {roleOptions.map(r => <option key={r} value={r}>{r}</option>)}
              </select></div>
            <div className="field"><label>Reports to (optional)</label>
              <select value={form.managerId} onChange={e => set('managerId', e.target.value)}>
                <option value="">— No manager —</option>
                {(users || []).filter(u => u.active).map(u => <option key={u.id} value={u.id}>{nameOf(u)}</option>)}
              </select></div>
            <div className="muted" style={{ fontSize: 12, marginBottom: 12 }}>
              A temporary password is generated automatically. They’ll change it after first sign-in.
            </div>
            <button className="btn" style={{ width: '100%' }} disabled={busy}>{busy ? 'Adding…' : 'Add member'}</button>
          </form>
        </div>

        <div style={{ flex: '2 1 480px' }}>
          {!users && !error && <div className="muted">Loading…</div>}
          {users && (
            <>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 10, flexWrap: 'wrap' }}>
                <div className="muted" style={{ fontSize: 13 }}>
                  {activeCount} active{removedCount > 0 ? ` · ${removedCount} removed` : ''}
                </div>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, userSelect: 'none' }}>
                  <input type="checkbox" checked={showRemoved} onChange={e => setShowRemoved(e.target.checked)} />
                  Show removed members
                </label>
              </div>
              <div className="tablewrap"><table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Roles</th>
                    <th>Reports to</th>
                    {isSuper && <th>Company</th>}
                    {isAdmin && <th title="Put people in the same peer group so they can see each other's leads">Peer group</th>}
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {visibleUsers.length === 0 && (
                    <tr><td colSpan={isSuper ? 8 : isAdmin ? 7 : 6} className="muted" style={{ textAlign: 'center', padding: 20 }}>
                      {showRemoved ? 'No team members.' : 'No active team members. Turn on “Show removed members” to see deactivated staff.'}
                    </td></tr>
                  )}
                  {visibleUsers.map(u => {
                    const isSuperUser = u.roles.includes('SUPER_ADMIN')
                    return (
                    <tr key={u.id} style={u.active ? undefined : { opacity: .55 }}>
                      <td>{nameOf(u)}</td>
                      <td>{u.email}</td>
                      <td>{u.roles.map(r => <span key={r} className="badge" style={{ marginRight: 4 }}>{r}</span>)}</td>
                      <td>
                        {u.active && !isSuperUser && (isSuper || myDescendants.has(u.id)) ? (
                          <select value={u.managerId || ''} onChange={e => setMgr(u.id, e.target.value)} style={{ padding: '4px 8px' }}>
                            <option value="">— No manager —</option>
                            {users.filter(o => o.id !== u.id && o.active).map(o => <option key={o.id} value={o.id}>{nameOf(o)}</option>)}
                          </select>
                        ) : <span className="muted">{u.managerName || '—'}</span>}
                      </td>
                      {isSuper && <td>
                        {u.active ? (
                          <select value={u.companyId || ''} onChange={e => assignCompany(u.id, e.target.value)} style={{ padding: '4px 8px' }}>
                            <option value="">— None —</option>
                            {companies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                          </select>
                        ) : <span className="muted">—</span>}
                      </td>}
                      {isAdmin && <td>
                        {canAssignPeer(u) ? (
                          <select value={u.peerGroupId || ''} onChange={e => assignPeerGroup(u.id, e.target.value)} style={{ padding: '4px 8px' }}>
                            <option value="">— None —</option>
                            {peerGroups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                          </select>
                        ) : <span className="muted">{u.peerGroupName || '—'}</span>}
                      </td>}
                      <td>{u.active ? <span className="badge WON">Active</span> : <span className="badge LOST">Removed</span>}</td>
                      <td style={{ textAlign: 'right' }}>
                        {canRemove(u) && (
                          <button className="btn ghost" style={{ padding: '4px 10px', color: '#b91c1c', borderColor: '#fecaca' }}
                            onClick={() => removeUser(u)}>Remove</button>
                        )}
                      </td>
                    </tr>
                    )
                  })}
                </tbody>
              </table></div>
            </>
          )}
        </div>
      </div>

      {isAdmin && (
        <div className="card" style={{ marginTop: 18 }}>
          <b>Peer groups</b>
          <div className="sub" style={{ marginBottom: 12 }}>
            Explicitly group people who should see each other’s leads (e.g. two sales reps).
            Create a group, then assign members in the <b>Peer group</b> column above.
            Visibility uses the shared org policy: creator&apos;s team sphere (up / down / peers), plus members and managers above them — not other branches.
          </div>
          <div style={{ display: 'flex', gap: 8, marginBottom: 14, maxWidth: 440 }}>
            <input value={newPeerGroup} onChange={e => setNewPeerGroup(e.target.value)} placeholder="New peer group name (e.g. peer_rep)"
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addPeerGroup() } }} />
            <button className="btn" onClick={addPeerGroup} disabled={!newPeerGroup.trim()}>Add</button>
          </div>
          {peerGroups.length === 0 && <div className="muted">No peer groups yet. Create one, then put two or more people in it.</div>}
          {peerGroups.map(g => (
            <div key={g.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderTop: '1px solid var(--line)' }}>
              <div style={{ minWidth: 150 }}>
                <div style={{ fontWeight: 700 }}>{g.name}</div>
                {g.createdByName && <div className="muted" style={{ fontSize: 11 }}>by {g.createdByName}</div>}
              </div>
              <div style={{ flex: 1 }}>
                {g.members.length ? g.members.map(m => <span key={m.id} className="badge" style={{ marginRight: 4 }}>{m.name}</span>)
                  : <span className="muted">No members — assign via the table</span>}
              </div>
              <button className="btn ghost" style={{ padding: '4px 10px', color: '#b91c1c', borderColor: '#fecaca' }} onClick={() => removePeerGroup(g)}>Delete</button>
            </div>
          ))}
        </div>
      )}

      {isSuper && (
        <div className="card" style={{ marginTop: 18 }}>
          <b>Companies</b>
          <div className="sub" style={{ marginBottom: 12 }}>Group admins so they can see each other's leads. Assign people to a company in the table above.</div>
          <div style={{ display: 'flex', gap: 8, marginBottom: 14, maxWidth: 440 }}>
            <input value={newCompany} onChange={e => setNewCompany(e.target.value)} placeholder="New company name"
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addCompany() } }} />
            <button className="btn" onClick={addCompany} disabled={!newCompany.trim()}>Add</button>
          </div>
          {companies.length === 0 && <div className="muted">No companies yet.</div>}
          {companies.map(c => (
            <div key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderTop: '1px solid var(--line)' }}>
              <span style={{ minWidth: 150, fontWeight: 700 }}>{c.name}</span>
              <div style={{ flex: 1 }}>
                {c.members.length ? c.members.map(m => <span key={m.id} className="badge" style={{ marginRight: 4 }}>{m.name}</span>)
                  : <span className="muted">No members</span>}
              </div>
              <button className="btn ghost" style={{ padding: '4px 10px', color: '#b91c1c', borderColor: '#fecaca' }} onClick={() => removeCompany(c)}>Delete</button>
            </div>
          ))}
        </div>
      )}

      {users && users.some(u => u.active) && (() => {
        const active = users.filter(u => u.active)
        const ids = new Set(active.map(u => u.id))
        const roots = active.filter(u => !u.managerId || !ids.has(u.managerId))
        return (
          <div className="card" style={{ marginTop: 18 }}>
            <b>Reporting structure</b>
            <div className="sub" style={{ marginBottom: 0 }}>Who reports to whom. Peer-group badges show explicit lead-sharing groups.</div>
            <div className="org">
              <ul>{roots.map(r => renderNode(r, active))}</ul>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
