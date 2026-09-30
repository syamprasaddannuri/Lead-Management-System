import React, { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api, Lead, Assignee } from '../api'
import { useAuth, hasAnyRole } from '../auth'

function name(l: Lead) { return [l.firstName, l.lastName].filter(Boolean).join(' ') || '—' }
function when(s: string) { try { return new Date(s).toLocaleDateString() } catch { return s } }

const PAGE_SIZES = [10, 25, 50]
const STATUS_OPTIONS = ['NEW', 'CONTACTED', 'QUALIFIED', 'NURTURING', 'NEGOTIATION', 'WON', 'LOST']
const SORT_OPTIONS = [
  { v: 'created-desc', label: 'Newest first' },
  { v: 'created-asc', label: 'Oldest first' },
  { v: 'name-asc', label: 'Name A–Z' },
  { v: 'stage-asc', label: 'Stage' },
]

export default function Leads() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const isSuper = hasAnyRole(user, ['SUPER_ADMIN'])
  const canAssign = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER'])
  const [leads, setLeads] = useState<Lead[] | null>(null)
  const [assignees, setAssignees] = useState<Assignee[]>([])
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [selectAllMatching, setSelectAllMatching] = useState(false)
  const [assignTo, setAssignTo] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(25)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  // filters / search / sort (all applied server-side)
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [status, setStatus] = useState('')
  const [owner, setOwner] = useState('')
  const [sort, setSort] = useState('created-desc')
  const hasFilters = !!(debouncedQ || status || owner)

  useEffect(() => { const t = setTimeout(() => { setDebouncedQ(q.trim()); setPage(0) }, 300); return () => clearTimeout(t) }, [q])

  function load() {
    const [sf, sd] = sort.split('-')
    api.listLeadsPaged(page, size, { q: debouncedQ, status, owner, sort: sf, dir: sd }).then(res => {
      setLeads(res.content); setTotal(res.total); setTotalPages(res.totalPages)
    }).catch(e => setError(e.message))
  }
  useEffect(() => { load() }, [page, size, debouncedQ, status, owner, sort])
  useEffect(() => { if (canAssign) api.assignees().then(setAssignees).catch(() => {}) }, [])

  async function remove(e: React.MouseEvent, l: Lead) {
    e.stopPropagation()
    if (!window.confirm(`Delete lead "${name(l)}"? This cannot be undone.`)) return
    try {
      await api.deleteLead(l.id)
      setSelected(prev => { const n = new Set(prev); n.delete(l.id); return n })
      // If the current page becomes empty after delete, step back a page.
      const remaining = (leads || []).filter(x => x.id !== l.id).length
      if (remaining === 0 && page > 0) setPage(page - 1)
      else load()
    } catch (err: any) { setError(err.message) }
  }

  function toggle(id: string) {
    setSelectAllMatching(false)
    setSelected(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })
  }
  function toggleAll() {
    if (!leads) return
    setSelectAllMatching(false)
    setSelected(prev => prev.size === leads.length ? new Set() : new Set(leads.map(l => l.id)))
  }

  function clearSelection() {
    setSelected(new Set()); setSelectAllMatching(false)
  }

  async function doAssign() {
    if (!selectAllMatching && selected.size === 0) return
    setBusy(true); setError(null)
    try {
      if (selectAllMatching) await api.assignLeadsByFilter({ q: debouncedQ, status, owner }, assignTo)
      else await api.assignLeads([...selected], assignTo)
      clearSelection(); setAssignTo('')
      load()
    } catch (e: any) { setError(e.message) } finally { setBusy(false) }
  }

  function changeSize(n: number) {
    setSize(n); setPage(0)
  }

  const start = total === 0 ? 0 : page * size + 1
  const end = Math.min(total, page * size + (leads?.length || 0))

  return (
    <div className="page">
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <h1 style={{ margin: 0 }}>Leads</h1>
        <Link to="/leads/new" className="btn" style={{ marginLeft: 'auto' }}>+ Add lead</Link>
        <Link to="/leads/import" className="btn ghost">Bulk import</Link>
      </div>
      <div className="sub">Click a lead to work it: change stage, assign an owner, log activity.</div>
      {error && <div className="err">{error}</div>}

      {canAssign && selected.size > 0 && (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '12px 14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <b>{selectAllMatching ? `All ${total} matching selected` : `${selected.size} selected`}</b>
            <span className="muted">Assign to</span>
            <select value={assignTo} onChange={e => setAssignTo(e.target.value)} style={{ padding: '4px 8px' }}>
              <option value="">Unassign</option>
              {assignees.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
            <button className="btn" onClick={doAssign} disabled={busy}>{busy ? 'Assigning…' : 'Apply'}</button>
            <button className="btn ghost" onClick={clearSelection}>Clear</button>
          </div>
          {!selectAllMatching && selected.size === leads?.length && total > selected.size && (
            <div className="muted" style={{ fontSize: 13 }}>
              Only the {selected.size} leads on this page are selected.{' '}
              <a href="#" onClick={e => { e.preventDefault(); setSelectAllMatching(true) }}>
                Select all {total} leads matching the current filters
              </a>
            </div>
          )}
        </div>
      )}

      <div className="filterbar">
        <input className="filtersearch" placeholder="Search name, email or phone…" value={q} onChange={e => setQ(e.target.value)} />
        <select value={status} onChange={e => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All stages</option>
          {STATUS_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        {canAssign && (
          <select value={owner} onChange={e => { setOwner(e.target.value); setPage(0) }}>
            <option value="">All owners</option>
            <option value="unassigned">Unassigned</option>
            {assignees.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        )}
        <select value={sort} onChange={e => { setSort(e.target.value); setPage(0) }}>
          {SORT_OPTIONS.map(o => <option key={o.v} value={o.v}>{o.label}</option>)}
        </select>
        {hasFilters && <button className="btn ghost" onClick={() => { setQ(''); setStatus(''); setOwner(''); setPage(0) }}>Clear</button>}
      </div>

      {!leads && !error && <div className="muted">Loading…</div>}
      {leads && leads.length === 0 && <div className="card muted">{hasFilters ? 'No leads match your filters.' : 'No leads yet.'}</div>}
      {leads && leads.length > 0 && (
        <>
          <div className="tablewrap"><table>
            <thead>
              <tr>
                {canAssign && <th style={{ width: 28 }}>
                  <input type="checkbox" checked={selected.size === leads.length} onChange={toggleAll} />
                </th>}
                <th>Name</th><th>Phone</th><th>Program</th><th>Source</th><th>Owner</th><th>Added by</th><th>Stage</th><th>Created</th>
                {isSuper && <th></th>}
              </tr>
            </thead>
            <tbody>
              {leads.map(l => (
                <tr key={l.id} className="clickrow" onClick={() => navigate(`/leads/${l.id}`)}>
                  {canAssign && <td onClick={e => e.stopPropagation()}>
                    <input type="checkbox" checked={selected.has(l.id)} onChange={() => toggle(l.id)} />
                  </td>}
                  <td><b>{name(l)}</b></td>
                  <td>{l.phone}</td>
                  <td>{l.courseName || '—'}</td>
                  <td>{l.source || '—'}</td>
                  <td>{l.ownerName || <span className="muted">Unassigned</span>}</td>
                  <td>{l.createdByName || <span className="muted">—</span>}</td>
                  <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                  <td className="muted">{when(l.createdAt)}</td>
                  {isSuper && <td><button className="del" title="Delete lead" onClick={e => remove(e, l)}>✕</button></td>}
                </tr>
              ))}
            </tbody>
          </table></div>

          <div className="pager">
            <span className="muted">
              {start}–{end} of {total}
            </span>
            <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className="muted">Rows</span>
              <select
                value={size}
                onChange={e => changeSize(Number(e.target.value))}
                style={{ width: 'auto', padding: '4px 8px' }}
              >
                {PAGE_SIZES.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
              <button
                className="btn ghost"
                style={{ padding: '4px 10px' }}
                disabled={page <= 0}
                onClick={() => setPage(page - 1)}
              >‹ Prev</button>
              <span className="muted" style={{ minWidth: 90, textAlign: 'center' }}>
                Page {page + 1} of {Math.max(1, totalPages)}
              </span>
              <button
                className="btn ghost"
                style={{ padding: '4px 10px' }}
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
              >Next ›</button>
            </span>
          </div>
        </>
      )}
    </div>
  )
}
