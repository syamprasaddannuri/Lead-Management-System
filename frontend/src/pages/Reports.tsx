import React, { useEffect, useState } from 'react'
import { api, Report, Bucket } from '../api'

const PALETTE = ['#4F46E5', '#06B6D4', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#ec4899', '#64748b']

const PERIODS: { label: string; days: number | null; hint: string }[] = [
  { label: '1 day', days: 1, hint: 'Leads created in the last 24 hours' },
  { label: '7 days', days: 7, hint: 'Last week' },
  { label: '30 days', days: 30, hint: 'Last month' },
  { label: '90 days', days: 90, hint: 'Last quarter' },
  { label: 'All time', days: null, hint: 'Full pipeline history' },
]

// Inline SVG donut chart with a legend — no chart-library dependency.
function Donut({ title, data, limit = 6 }: { title: string; data: Bucket[]; limit?: number }) {
  const total = data.reduce((s, d) => s + d.count, 0)
  const top = data.slice(0, limit)
  const restCount = data.slice(limit).reduce((s, d) => s + d.count, 0)
  const slices = restCount > 0 ? [...top, { key: 'Other', count: restCount }] : top
  const r = 54, C = 2 * Math.PI * r
  let acc = 0
  return (
    <div className="card">
      <b>{title}</b>
      {total === 0 ? <div className="muted" style={{ marginTop: 12 }}>No data in this period</div> : (
        <div className="donutwrap">
          <svg width="140" height="140" viewBox="0 0 140 140" className="donut">
            <g transform="rotate(-90 70 70)">
              <circle cx="70" cy="70" r={r} fill="none" stroke="#eef1f8" strokeWidth="18" />
              {slices.map((s, i) => {
                const dash = (s.count / total) * C
                const el = (
                  <circle key={s.key} cx="70" cy="70" r={r} fill="none" stroke={PALETTE[i % PALETTE.length]}
                    strokeWidth="18" strokeDasharray={`${dash} ${C - dash}`} strokeDashoffset={-acc} />
                )
                acc += dash
                return el
              })}
            </g>
            <text x="70" y="67" textAnchor="middle" className="donut-total">{total}</text>
            <text x="70" y="85" textAnchor="middle" className="donut-lbl">total</text>
          </svg>
          <div className="donut-legend">
            {slices.map((s, i) => (
              <div className="legrow" key={s.key}>
                <span className="legdot" style={{ background: PALETTE[i % PALETTE.length] }} />
                <span className="legname" title={s.key}>{s.key}</span>
                <span className="legval">{s.count}</span>
                <span className="legpct">{Math.round((s.count / total) * 100)}%</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function StageBars({ data }: { data: Bucket[] }) {
  const max = Math.max(1, ...data.map(d => d.count))
  return (
    <div className="card">
      <b>Pipeline stages</b>
      <div className="muted" style={{ fontSize: 13, marginTop: 4, marginBottom: 12 }}>
        How many leads (created in this period) are in each stage now
      </div>
      {data.every(d => d.count === 0) ? (
        <div className="muted">No leads in this period</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {data.map((s, i) => (
            <div key={s.key}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 4 }}>
                <span style={{ fontWeight: 600 }}>{s.key}</span>
                <span className="muted">{s.count}</span>
              </div>
              <div style={{ height: 10, background: '#eef1f8', borderRadius: 6, overflow: 'hidden' }}>
                <div style={{
                  width: `${(s.count / max) * 100}%`,
                  height: '100%',
                  background: PALETTE[i % PALETTE.length],
                  borderRadius: 6,
                  minWidth: s.count > 0 ? 4 : 0,
                  transition: 'width .25s ease',
                }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function Reports() {
  const [days, setDays] = useState<number | null>(7)
  const [r, setR] = useState<Report | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function generate(periodDays: number | null = days) {
    setBusy(true); setError(null)
    try {
      const report = await api.leadReport(periodDays)
      setR(report)
    } catch (e: any) {
      setError(e.message)
      setR(null)
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => { generate(7) }, [])

  const statusCount = (key: string) => r?.byStatus.find(b => b.key === key)?.count ?? 0

  return (
    <div className="page">
      <h1>Reports</h1>
      <div className="sub">Generate lead performance for a time window. Counts are based on leads created in the selected period.</div>
      {error && <div className="err">{error}</div>}

      <div className="card" style={{ marginBottom: 18 }}>
        <b>Report period</b>
        <div className="muted" style={{ fontSize: 13, marginTop: 4, marginBottom: 12 }}>
          Choose a range, then generate. You’ll see won, contacted, and other stage counts for that window.
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 14 }}>
          {PERIODS.map(p => {
            const active = days === p.days
            return (
              <button
                key={p.label}
                type="button"
                className={`btn ${active ? '' : 'ghost'}`}
                style={{ padding: '8px 14px' }}
                onClick={() => setDays(p.days)}
                title={p.hint}
              >
                {p.label}
              </button>
            )
          })}
        </div>
        <button className="btn" disabled={busy} onClick={() => generate(days)}>
          {busy ? 'Generating…' : 'Generate report'}
        </button>
        {r && (
          <span className="muted" style={{ marginLeft: 12, fontSize: 13 }}>
            Showing: <b>{r.periodLabel}</b>
          </span>
        )}
      </div>

      {busy && !r && <div className="muted">Loading…</div>}

      {r && (
        <>
          <div className="row" style={{ marginBottom: 18 }}>
            <div className="stat"><b>{r.total}</b><span>Leads in period</span></div>
            <div className="stat"><b>{r.open}</b><span>Open</span></div>
            <div className="stat"><b>{r.won}</b><span>Won (enrolled)</span></div>
            <div className="stat"><b>{r.lost}</b><span>Lost</span></div>
            <div className="stat"><b>{Math.round(r.conversionRate * 100)}%</b><span>Conversion</span></div>
          </div>

          <div className="row" style={{ marginBottom: 18 }}>
            {['NEW', 'CONTACTED', 'QUALIFIED', 'NURTURING', 'NEGOTIATION', 'WON', 'LOST'].map(s => (
              <div className="stat" key={s} style={{ minWidth: 110 }}>
                <b>{statusCount(s)}</b>
                <span>{s.charAt(0) + s.slice(1).toLowerCase()}</span>
              </div>
            ))}
          </div>

          <div className="rankgrid" style={{ marginBottom: 16 }}>
            <StageBars data={r.byStatus} />
            <Donut title="By stage" data={r.byStatus.filter(b => b.count > 0)} limit={7} />
          </div>
          <div className="rankgrid">
            <Donut title="By source" data={r.bySource} />
            <Donut title="By program" data={r.byProgram} />
            <Donut title="By owner" data={r.byOwner} />
          </div>
        </>
      )}
    </div>
  )
}
