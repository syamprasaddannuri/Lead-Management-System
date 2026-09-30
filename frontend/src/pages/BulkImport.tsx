import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import * as XLSX from 'xlsx'
import { api, BulkInputRow, BulkResult, BulkRowResult, normalizePhone } from '../api'

const SAMPLE = `Full Name,Email Address,Mobile,Course,City,Company
Aarav Sharma,aarav.sharma@example.com,9810000001,agentic-ai,Hyderabad,Acme Corp
Priya Nair,priya.nair@example.com,9810000002,ai-ml,Bengaluru,Globex
Rohan Mehta,rohan.mehta@example.com,9810000003,,Mumbai,Initech
Meera Iyer,meera.iyer@example.com,9810000004,ai-ml,Pune,Hooli`

// Rows are checked / imported in batches so the progress bar can advance and requests stay small.
const CHUNK = 500
const PREVIEW_LIMIT = 200 // cap rows rendered in the preview table

// Our target fields. name/phone are required; email/program/source optional.
const FIELDS: { key: keyof Mapping; label: string; required: boolean; guess: RegExp }[] = [
  { key: 'name', label: 'Name', required: true, guess: /name/i },
  { key: 'phone', label: 'Phone', required: true, guess: /phone|mobile|contact|number/i },
  { key: 'email', label: 'Email', required: false, guess: /e-?mail/i },
  { key: 'program', label: 'Program / Course', required: false, guess: /program|course/i },
  { key: 'source', label: 'Source', required: false, guess: /source|channel/i },
]
interface Mapping { name: number; email: number; phone: number; program: number; source: number }
interface Progress { label: string; done: number; total: number }

function courseLabel(program: string): string {
  const p = (program || '').trim().toLowerCase()
  if (p === 'agentic-ai') return 'Agentic AI Engineer'
  if (p === 'ai-ml') return 'Applied AI & ML'
  return program ? program.trim() : ''
}

// Parse delimited text (CSV or tab-separated TXT). Handles quoted fields.
function parseDelimited(text: string, delim: string): string[][] {
  const rows: string[][] = []
  let field = '', row: string[] = [], inQ = false, i = 0
  while (i < text.length) {
    const c = text[i]
    if (inQ) {
      if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++ } else inQ = false }
      else field += c
    } else {
      if (c === '"') inQ = true
      else if (c === delim) { row.push(field); field = '' }
      else if (c === '\n') { row.push(field); rows.push(row); row = []; field = '' }
      else if (c !== '\r') field += c
    }
    i++
  }
  if (field.length || row.length) { row.push(field); rows.push(row) }
  return rows.filter(r => r.some(c => c.trim().length))
}

export default function BulkImport() {
  const [fileName, setFileName] = useState('')
  const [headers, setHeaders] = useState<string[]>([])
  const [dataRows, setDataRows] = useState<string[][]>([])
  const [mapping, setMapping] = useState<Mapping>({ name: -1, email: -1, phone: -1, program: -1, source: -1 })
  const [preview, setPreview] = useState<BulkResult | null>(null)
  const [done, setDone] = useState<BulkResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<Progress | null>(null)
  const [error, setError] = useState<string | null>(null)

  function loadGrid(grid: string[][], name: string) {
    setError(null); setPreview(null); setDone(null)
    if (grid.length < 2) { setError('Need a header row plus at least one data row.'); return }
    const hdr = grid[0].map(h => (h || '').trim())
    const m: Mapping = { name: -1, email: -1, phone: -1, program: -1, source: -1 }
    FIELDS.forEach(f => { m[f.key] = hdr.findIndex(h => f.guess.test(h)) })
    setHeaders(hdr); setDataRows(grid.slice(1)); setMapping(m); setFileName(name)
  }

  function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    const name = file.name.toLowerCase()
    const reader = new FileReader()
    if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
      reader.onload = () => {
        try {
          const wb = XLSX.read(reader.result, { type: 'array' })
          const ws = wb.Sheets[wb.SheetNames[0]]
          const aoa = XLSX.utils.sheet_to_json<string[]>(ws, { header: 1, defval: '', raw: false })
          loadGrid(aoa.map(r => (r || []).map(c => String(c ?? ''))), file.name)
        } catch { setError('Could not read that Excel file.') }
      }
      reader.readAsArrayBuffer(file)
    } else {
      reader.onload = () => {
        const text = String(reader.result || '')
        const first = text.split(/\r?\n/)[0] || ''
        const delim = first.split('\t').length > first.split(',').length ? '\t' : ','
        loadGrid(parseDelimited(text, delim), file.name)
      }
      reader.readAsText(file)
    }
    e.target.value = '' // allow re-selecting the same file
  }

  function downloadSample() {
    const url = URL.createObjectURL(new Blob([SAMPLE + '\n'], { type: 'text/csv' }))
    const a = document.createElement('a'); a.href = url; a.download = 'sample-leads.csv'; a.click()
    URL.revokeObjectURL(url)
  }

  const mappedIdx = new Set(Object.values(mapping).filter(i => i >= 0))
  const extraCols = headers.filter((_, idx) => !mappedIdx.has(idx))

  function buildRows(): BulkInputRow[] {
    const get = (cells: string[], i: number) => i >= 0 ? (cells[i] || '').trim() : ''
    return dataRows.map(cells => {
      const extra: Record<string, string> = {}
      headers.forEach((h, idx) => { if (!mappedIdx.has(idx)) { const v = (cells[idx] || '').trim(); if (v) extra[h] = v } })
      return {
        name: get(cells, mapping.name), email: get(cells, mapping.email), phone: get(cells, mapping.phone),
        program: get(cells, mapping.program), source: get(cells, mapping.source), extra,
      }
    })
  }

  // Stage 2 — processing: structure + within-file dedup in the browser, CRM dedup on the server (chunked).
  async function doProcess() {
    if (mapping.name < 0 || mapping.phone < 0) {
      setError('Please map the required fields: Name and Phone.'); return
    }
    setError(null); setPreview(null); setBusy(true)
    try {
      const rows = buildRows()
      const results: BulkRowResult[] = []
      const seenPhones = new Set<string>(), seenEmails = new Set<string>()
      const candPhones = new Set<string>(), candEmails = new Set<string>()

      rows.forEach((r, i) => {
        const name = (r.name || '').trim(), email = (r.email || '').trim(), phone = (r.phone || '').trim()
        const emailLower = email.toLowerCase(), hasEmail = !!emailLower
        const phoneNorm = normalizePhone(phone)
        let status = 'NEW', message = ''
        if (!name) { status = 'INVALID'; message = 'Name is required' }
        else if (!phone) { status = 'INVALID'; message = 'Phone is required' }
        else if (hasEmail && !(email.includes('@') && email.includes('.'))) { status = 'INVALID'; message = 'Invalid email' }
        else if (seenPhones.has(phoneNorm) || (hasEmail && seenEmails.has(emailLower))) { status = 'DUPLICATE'; message = 'Repeated in file' }
        else {
          seenPhones.add(phoneNorm); if (hasEmail) seenEmails.add(emailLower)
          candPhones.add(phoneNorm); if (hasEmail) candEmails.add(emailLower)
        }
        results.push({ row: i + 1, name, email, phone, courseName: courseLabel(r.program || ''), source: r.source || '', status, message })
      })

      // Server CRM-duplicate check, in chunks, driving the progress bar.
      const phoneList = [...candPhones], emailList = [...candEmails]
      const chunks = Math.max(Math.ceil(phoneList.length / CHUNK), Math.ceil(emailList.length / CHUNK))
      const existingPhones = new Set<string>(), existingEmails = new Set<string>()
      if (chunks > 0) {
        setProgress({ label: 'Checking for duplicates', done: 0, total: chunks })
        for (let c = 0; c < chunks; c++) {
          const ph = phoneList.slice(c * CHUNK, c * CHUNK + CHUNK)
          const em = emailList.slice(c * CHUNK, c * CHUNK + CHUNK)
          const res = await api.checkDuplicates(ph, em)
          res.phones.forEach(p => existingPhones.add((p || '').trim()))
          res.emails.forEach(e => existingEmails.add((e || '').trim().toLowerCase()))
          setProgress({ label: 'Checking for duplicates', done: c + 1, total: chunks })
        }
      }

      results.forEach(r => {
        if (r.status !== 'NEW') return
        const hasEmail = !!r.email
        if (existingPhones.has(normalizePhone(r.phone)) || (hasEmail && existingEmails.has(r.email.toLowerCase()))) {
          r.status = 'DUPLICATE'; r.message = 'Already in CRM'
        }
      })

      const toImport = results.filter(r => r.status === 'NEW').length
      const duplicates = results.filter(r => r.status === 'DUPLICATE').length
      const invalid = results.filter(r => r.status === 'INVALID').length
      setPreview({ total: results.length, toImport, duplicates, invalid, created: 0, rows: results })
    } catch (e: any) { setError(e.message) } finally { setBusy(false); setProgress(null) }
  }

  // Stage 4 — commit: send only the NEW rows, in chunks, with a progress bar.
  async function doImport() {
    if (!preview || preview.toImport === 0) return
    setBusy(true); setError(null)
    try {
      const allRows = buildRows()
      const newRows = preview.rows.filter(r => r.status === 'NEW').map(r => allRows[r.row - 1])
      const chunks = Math.ceil(newRows.length / CHUNK)
      let created = 0, duplicates = 0, invalid = 0
      setProgress({ label: 'Importing', done: 0, total: Math.max(1, chunks) })
      for (let c = 0; c < chunks; c++) {
        const chunk = newRows.slice(c * CHUNK, c * CHUNK + CHUNK)
        const res = await api.bulkLeads(true, chunk)
        created += res.created; duplicates += res.duplicates; invalid += res.invalid
        setProgress({ label: 'Importing', done: c + 1, total: chunks })
      }
      setDone({ total: newRows.length, toImport: 0, duplicates, invalid, created, rows: [] })
      setPreview(null)
    } catch (e: any) { setError(e.message) } finally { setBusy(false); setProgress(null) }
  }

  function reset() {
    setHeaders([]); setDataRows([]); setFileName(''); setPreview(null); setDone(null); setError(null); setProgress(null)
  }

  const pct = progress ? Math.round((progress.done / progress.total) * 100) : 0

  return (
    <div className="page">
      <Link to="/leads" className="muted" style={{ fontSize: 14 }}>← Back to leads</Link>
      <h1 style={{ marginTop: 6 }}>Bulk import leads</h1>
      <div className="sub">Upload a CSV, map its columns to our fields, preview, then import. Required: Name and Phone. Imported leads are assigned to you. Unmapped columns are kept on the lead.</div>
      {error && <div className="err">{error}</div>}

      {done ? (
        <div className="card">
          <div className="ok">Imported <b>{done.created}</b> lead{done.created === 1 ? '' : 's'}.
            {done.duplicates > 0 && <> Skipped {done.duplicates} duplicate(s).</>}
            {done.invalid > 0 && <> Skipped {done.invalid} invalid row(s).</>}</div>
          <div style={{ marginTop: 14 }}>
            <Link to="/leads" className="btn">View leads</Link>
            <button className="btn ghost" style={{ marginLeft: 8 }} onClick={reset}>Import another file</button>
          </div>
        </div>
      ) : headers.length === 0 ? (
        <div className="card">
          <label>Choose a file — CSV, Excel (.xlsx/.xls) or TXT</label>
          <input type="file" accept=".csv,.xlsx,.xls,.txt,text/csv,text/plain,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel" onChange={onFile} />
          <div className="muted" style={{ fontSize: 13, marginTop: 10 }}>
            No file handy? <a style={{ color: 'var(--indigo)', fontWeight: 600, cursor: 'pointer' }} onClick={downloadSample}>Download a sample CSV</a> and upload it.
          </div>
        </div>
      ) : (
        <>
          <div className="card" style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <b>Map columns</b>
              <span className="muted" style={{ marginLeft: 8, fontSize: 13 }}>from <b>{fileName}</b> · {dataRows.length} row(s)</span>
              <button className="btn ghost" style={{ marginLeft: 'auto' }} onClick={reset} disabled={busy}>Change file</button>
            </div>
            <div style={{ marginTop: 14, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {FIELDS.map(f => (
                <div key={f.key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{ width: 140, fontSize: 14, fontWeight: 600 }}>{f.label}{f.required && <span style={{ color: '#e11d48' }}> *</span>}</div>
                  <select value={mapping[f.key]} onChange={e => setMapping({ ...mapping, [f.key]: Number(e.target.value) })} disabled={busy}>
                    <option value={-1}>— Not mapped —</option>
                    {headers.map((h, idx) => <option key={idx} value={idx}>{h}</option>)}
                  </select>
                </div>
              ))}
            </div>
            {extraCols.length > 0 && (
              <div className="muted" style={{ fontSize: 13, marginTop: 12 }}>
                Stored on the lead as extra data: <b>{extraCols.join(', ')}</b>
              </div>
            )}
            {!preview && (
              <div style={{ marginTop: 14 }}>
                <button className="btn" onClick={doProcess} disabled={busy}>{busy ? 'Processing…' : 'Process & preview'}</button>
              </div>
            )}
          </div>

          {progress && (
            <div className="card" style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, marginBottom: 8 }}>
                <b>{progress.label}…</b>
                <span className="muted">{pct}%</span>
              </div>
              <div className="bartrack"><div className="barfill" style={{ width: `${pct}%` }} /></div>
            </div>
          )}

          {preview && (
            <>
              <div className="row" style={{ marginBottom: 14 }}>
                <div className="stat"><b>{preview.total}</b><span>Rows</span></div>
                <div className="stat"><b style={{ color: '#166534' }}>{preview.toImport}</b><span>Will import</span></div>
                <div className="stat"><b style={{ color: '#854d0e' }}>{preview.duplicates}</b><span>Duplicates</span></div>
                <div className="stat"><b style={{ color: '#991b1b' }}>{preview.invalid}</b><span>Invalid</span></div>
              </div>
              <div className="tablewrap"><table>
                <thead><tr><th>#</th><th>Name</th><th>Email</th><th>Phone</th><th>Program</th><th>Status</th></tr></thead>
                <tbody>
                  {preview.rows.slice(0, PREVIEW_LIMIT).map(r => (
                    <tr key={r.row}>
                      <td className="muted">{r.row}</td>
                      <td>{r.name || '—'}</td><td>{r.email || '—'}</td><td>{r.phone || '—'}</td><td>{r.courseName || '—'}</td>
                      <td><span className={`bk ${r.status}`}>{r.status}</span>{r.message && <span className="muted" style={{ marginLeft: 6, fontSize: 12 }}>{r.message}</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table></div>
              {preview.rows.length > PREVIEW_LIMIT && (
                <div className="muted" style={{ fontSize: 13, marginTop: 8 }}>Showing the first {PREVIEW_LIMIT} of {preview.rows.length} rows. All rows will be imported per the counts above.</div>
              )}
              <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
                <button className="btn" onClick={doImport} disabled={busy || preview.toImport === 0}>
                  {busy ? 'Importing…' : `Import ${preview.toImport} lead${preview.toImport === 1 ? '' : 's'}`}
                </button>
                <button className="btn ghost" onClick={() => setPreview(null)} disabled={busy}>Re-map columns</button>
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
