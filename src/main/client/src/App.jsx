import React, { useEffect, useMemo, useState } from 'react'
import './App.css'

function pct(value) {
  if (typeof value !== 'number') return 'N/A'
  return `${Math.round(value * 100)}%`
}

function formatDate(value) {
  if (!value) return 'Unknown'
  try {
    return new Date(value).toLocaleDateString()
  } catch {
    return value
  }
}

function App() {
  const [activeTab, setActiveTab] = useState('intake')
  const [file, setFile] = useState(null)
  const [patients, setPatients] = useState([])
  const [patientSearch, setPatientSearch] = useState('')
  const [selectedPatientId, setSelectedPatientId] = useState('')
  const [prediction, setPrediction] = useState(null)
  const [history, setHistory] = useState([])
  const [selectedRecordId, setSelectedRecordId] = useState('')

  const [outcomeForm, setOutcomeForm] = useState({
    actualOutcome: '',
    actualOutcomeDate: '',
    actualOutcomeNotes: '',
    overallSurvivalMonths: '',
    deceased: '',
    fractionGenomeAltered: '',
    mutationCount: '',
    tmbNonsynonymous: '',
    yearOfDiagnosis: '',
  })

  const [busy, setBusy] = useState({
    upload: false,
    predict: false,
    predictAll: false,
    searchHistory: false,
    saveOutcome: false,
    sync: false,
  })

  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  const selectedPatient = useMemo(
    () => patients.find((p) => String(p.id) === String(selectedPatientId)) || null,
    [patients, selectedPatientId],
  )

  function setErrorMsg(message) {
    setInfo('')
    setError(message)
  }

  function setInfoMsg(message) {
    setError('')
    setInfo(message)
  }

  async function parseError(res, fallback) {
    try {
      const payload = await res.json()
      if (payload && typeof payload.message === 'string') {
        const missing = Array.isArray(payload.missingFields) ? payload.missingFields : []
        if (missing.length) return `${payload.message} Missing: ${missing.join(', ')}`
        return payload.message
      }
    } catch {
      // ignore parse failure
    }
    return fallback
  }

  async function loadPatients(name = '') {
    try {
      const url = name.trim() ? `/api/users/search?name=${encodeURIComponent(name.trim())}` : '/api/users'
      const res = await fetch(url)
      if (!res.ok) throw new Error(`Could not load patients (${res.status})`)
      const data = await res.json()
      setPatients(Array.isArray(data) ? data : [])
    } catch (err) {
      setErrorMsg(err.message || 'Failed to load patients')
    }
  }

  async function loadHistory() {
    setBusy((b) => ({ ...b, searchHistory: true }))
    try {
      const res = await fetch('/api/predictions/search')
      if (!res.ok) throw new Error(`Could not load prediction records (${res.status})`)
      const data = await res.json()
      setHistory(Array.isArray(data) ? data : [])
    } catch (err) {
      setErrorMsg(err.message || 'Failed to load prediction records')
    } finally {
      setBusy((b) => ({ ...b, searchHistory: false }))
    }
  }

  useEffect(() => {
    loadPatients()
    loadHistory()
  }, [])

  async function handleUpload(event) {
    event.preventDefault()
    if (!file) return setErrorMsg('Choose a clinical file first.')

    setBusy((b) => ({ ...b, upload: true }))
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await fetch('/api/users/upload', { method: 'POST', body: formData })
      if (!res.ok) throw new Error(await parseError(res, `Upload failed (${res.status})`))
      const patient = await res.json()
      setSelectedPatientId(String(patient.id))
      setFile(null)
      await loadPatients(patientSearch)
      setInfoMsg(`Patient created: #${patient.id}`)
    } catch (err) {
      setErrorMsg(err.message || 'Upload failed')
    } finally {
      setBusy((b) => ({ ...b, upload: false }))
    }
  }

  async function handlePredict() {
    if (!selectedPatientId) return setErrorMsg('Select a patient first.')
    setBusy((b) => ({ ...b, predict: true }))
    try {
      const res = await fetch(`/api/predictions/${selectedPatientId}`, { method: 'POST' })
      if (!res.ok) throw new Error(await parseError(res, `Prediction failed (${res.status})`))
      const record = await res.json()
      setPrediction(record)
      setSelectedRecordId(String(record.predictionId))
      await loadHistory()
      setInfoMsg(`Prediction record created: #${record.predictionId}`)
    } catch (err) {
      setErrorMsg(err.message || 'Prediction failed')
    } finally {
      setBusy((b) => ({ ...b, predict: false }))
    }
  }

  async function handlePredictAll() {
    setBusy((b) => ({ ...b, predictAll: true }))
    try {
      const query = patientSearch.trim() ? `?name=${encodeURIComponent(patientSearch.trim())}` : ''
      const res = await fetch(`/api/predictions/predict-all${query}`, { method: 'POST' })
      if (!res.ok) throw new Error(await parseError(res, `Bulk prediction failed (${res.status})`))
      const records = await res.json()
      await loadHistory()
      setInfoMsg(`Bulk prediction complete: ${records.length} record(s) created`)
    } catch (err) {
      setErrorMsg(err.message || 'Bulk prediction failed')
    } finally {
      setBusy((b) => ({ ...b, predictAll: false }))
    }
  }

  async function handleSaveOutcome(event) {
    event.preventDefault()
    if (!selectedRecordId) return setErrorMsg('Select a prediction record first.')
    if (!outcomeForm.actualOutcome.trim()) return setErrorMsg('actualOutcome is required.')

    setBusy((b) => ({ ...b, saveOutcome: true }))
    try {
      const payload = {
        actualOutcome: outcomeForm.actualOutcome,
        actualOutcomeDate: outcomeForm.actualOutcomeDate || null,
        actualOutcomeNotes: outcomeForm.actualOutcomeNotes || null,
        overallSurvivalMonths: outcomeForm.overallSurvivalMonths ? Number(outcomeForm.overallSurvivalMonths) : null,
        deceased: outcomeForm.deceased === '' ? null : outcomeForm.deceased === 'true',
        fractionGenomeAltered: outcomeForm.fractionGenomeAltered
          ? Number(outcomeForm.fractionGenomeAltered)
          : null,
        mutationCount: outcomeForm.mutationCount ? Number(outcomeForm.mutationCount) : null,
        tmbNonsynonymous: outcomeForm.tmbNonsynonymous ? Number(outcomeForm.tmbNonsynonymous) : null,
        yearOfDiagnosis: outcomeForm.yearOfDiagnosis ? Number(outcomeForm.yearOfDiagnosis) : null,
      }

      const res = await fetch(`/api/predictions/${selectedRecordId}/outcome`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (!res.ok) throw new Error(await parseError(res, `Outcome update failed (${res.status})`))
      await loadHistory()
      setInfoMsg('Actual outcome updated.')
    } catch (err) {
      setErrorMsg(err.message || 'Outcome update failed')
    } finally {
      setBusy((b) => ({ ...b, saveOutcome: false }))
    }
  }

  async function handleSyncRetrain() {
    setBusy((b) => ({ ...b, sync: true }))
    try {
      const res = await fetch('/api/predictions/sync-training', { method: 'POST' })
      if (!res.ok) throw new Error(await parseError(res, `Retrain sync failed (${res.status})`))
      const data = await res.json()
      setInfoMsg(`${data.status} (${data.sentRecords}/${data.totalLabeledRecords})`)
    } catch (err) {
      setErrorMsg(err.message || 'Retrain sync failed')
    } finally {
      setBusy((b) => ({ ...b, sync: false }))
    }
  }

  const curveSource =
    prediction?.survivalCurvePng && prediction.survivalCurvePng.startsWith('data:')
      ? prediction.survivalCurvePng
      : prediction?.survivalCurvePng
        ? `data:image/png;base64,${prediction.survivalCurvePng}`
        : ''

  return (
    <div className="workspace">
      <aside className="rail">
        <div className="brand-block">
          <p className="label">Clinical Ops</p>
          <h1>AML Command Center</h1>
          <p>
            Designed for clinician focus: collect signal, score risk, and update outcomes with minimal friction.
          </p>
        </div>

        <nav className="stage-list" aria-label="workflow">
          <button
            type="button"
            className={activeTab === 'intake' ? 'stage active-tab' : 'stage ghost'}
            onClick={() => setActiveTab('intake')}
          >
            Intake + Prediction
          </button>
          <button
            type="button"
            className={activeTab === 'outcomes' ? 'stage active-tab' : 'stage ghost'}
            onClick={() => setActiveTab('outcomes')}
          >
            Outcomes + Retrain
          </button>
        </nav>

        <div className="status-card">
          <h2>Live State</h2>
          <dl>
            <div><dt>Patients</dt><dd>{patients.length}</dd></div>
            <div><dt>Records</dt><dd>{history.length}</dd></div>
            <div><dt>Selected Record</dt><dd>{selectedRecordId || 'None'}</dd></div>
          </dl>
        </div>
      </aside>

      <main className="canvas">
        {activeTab === 'intake' && (
          <section className="flow-grid">
            <article className="card">
              <header>
                <h2>Step 1: Intake Upload</h2>
                <p>Upload source clinical document and create patient profile.</p>
              </header>
              <form className="stack" onSubmit={handleUpload}>
                <label className="dropzone" htmlFor="intake-file">
                  <span>{file ? file.name : 'Choose clinical document'}</span>
                  <input id="intake-file" type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} />
                </label>
                <button type="submit" disabled={busy.upload}>
                  {busy.upload ? 'Uploading...' : 'Upload + Save Patient'}
                </button>
              </form>
            </article>

            <article className="card">
              <header>
                <h2>Step 2: Select and Score</h2>
                <p>Search by name, choose patient, then run single or bulk prediction.</p>
              </header>

              <div className="inline-row">
                <input
                  type="text"
                  placeholder="Search by name"
                  value={patientSearch}
                  onChange={(e) => setPatientSearch(e.target.value)}
                />
                <button type="button" className="ghost" onClick={() => loadPatients(patientSearch)}>Search</button>
              </div>

              <div className="inline-row">
                <select value={selectedPatientId} onChange={(e) => setSelectedPatientId(e.target.value)}>
                  <option value="">Select patient</option>
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>#{p.id} - {p.name || 'Unnamed'}</option>
                  ))}
                </select>
              </div>

              {selectedPatient && (
                <dl className="snapshot">
                  <div><dt>Name</dt><dd>{selectedPatient.name || 'Unknown'}</dd></div>
                  <div><dt>Age</dt><dd>{selectedPatient.age ?? 'Unknown'}</dd></div>
                  <div><dt>Gender</dt><dd>{selectedPatient.gender || 'Unknown'}</dd></div>
                  <div><dt>ECOG</dt><dd>{selectedPatient.ecogPerformanceStatus ?? 'Unknown'}</dd></div>
                </dl>
              )}

              <div className="inline-row">
                <button type="button" onClick={handlePredict} disabled={busy.predict || !selectedPatientId}>
                  {busy.predict ? 'Predicting...' : 'Predict Selected Patient'}
                </button>
                <button type="button" className="ghost" onClick={handlePredictAll} disabled={busy.predictAll}>
                  {busy.predictAll ? 'Predicting All...' : 'Predict All (Filtered)'}
                </button>
              </div>

              {prediction && (
                <>
                  <div className="metric-row">
                    <div><span>6mo</span><strong>{pct(prediction.survival6mo)}</strong></div>
                    <div><span>12mo</span><strong>{pct(prediction.survival12mo)}</strong></div>
                    <div><span>24mo</span><strong>{pct(prediction.survival24mo)}</strong></div>
                    <div><span>Risk</span><strong>{prediction.riskGroup || 'N/A'}</strong></div>
                  </div>
                  <p className="micro-ok">Latest record: #{prediction.predictionId}</p>
                </>
              )}

              {curveSource && (
                <figure className="curve">
                  <figcaption>Survival Curve</figcaption>
                  <img src={curveSource} alt="Patient survival curve" />
                </figure>
              )}
            </article>
          </section>
        )}

        {activeTab === 'outcomes' && (
          <section className="flow-grid single-col">
            <article className="card">
              <header className="split-head">
                <div>
                  <h2>Step 3: Locate Prediction Record</h2>
                  <p>Select the exact run before entering actual outcomes.</p>
                </div>
                <button type="button" className="ghost" onClick={loadHistory} disabled={busy.searchHistory}>
                  {busy.searchHistory ? 'Refreshing...' : 'Refresh Records'}
                </button>
              </header>

              <div className="record-list">
                {history.map((r) => (
                  <article key={r.predictionId} className={String(r.predictionId) === selectedRecordId ? 'record selected' : 'record'}>
                    <h3>#{r.predictionId} - {r.patientName || 'Unnamed'} ({r.patientAge ?? 'N/A'})</h3>
                    <p>Expected: {r.expectedOutcome || 'N/A'}</p>
                    <p>Actual: {r.actualOutcome || 'Not updated'} {r.actualOutcomeDate ? `(${formatDate(r.actualOutcomeDate)})` : ''}</p>
                    <button type="button" className="ghost" onClick={() => setSelectedRecordId(String(r.predictionId))}>
                      Select for Outcome Update
                    </button>
                  </article>
                ))}
                {history.length === 0 && <p>No records found.</p>}
              </div>
            </article>

            <article className="card">
              <header>
                <h2>Step 4: Outcome Capture and Retraining Sync</h2>
                <p>Selected prediction record: {selectedRecordId || 'none'}</p>
              </header>

              <form className="field-grid" onSubmit={handleSaveOutcome}>
                <input type="text" placeholder="actualOutcome" value={outcomeForm.actualOutcome} onChange={(e) => setOutcomeForm((p) => ({ ...p, actualOutcome: e.target.value }))} />
                <input type="date" value={outcomeForm.actualOutcomeDate} onChange={(e) => setOutcomeForm((p) => ({ ...p, actualOutcomeDate: e.target.value }))} />
                <input type="number" step="0.1" placeholder="overallSurvivalMonths" value={outcomeForm.overallSurvivalMonths} onChange={(e) => setOutcomeForm((p) => ({ ...p, overallSurvivalMonths: e.target.value }))} />
                <select value={outcomeForm.deceased} onChange={(e) => setOutcomeForm((p) => ({ ...p, deceased: e.target.value }))}>
                  <option value="">deceased?</option>
                  <option value="true">true</option>
                  <option value="false">false</option>
                </select>
                <input type="number" step="0.01" placeholder="fractionGenomeAltered" value={outcomeForm.fractionGenomeAltered} onChange={(e) => setOutcomeForm((p) => ({ ...p, fractionGenomeAltered: e.target.value }))} />
                <input type="number" placeholder="mutationCount" value={outcomeForm.mutationCount} onChange={(e) => setOutcomeForm((p) => ({ ...p, mutationCount: e.target.value }))} />
                <input type="number" step="0.1" placeholder="tmbNonsynonymous" value={outcomeForm.tmbNonsynonymous} onChange={(e) => setOutcomeForm((p) => ({ ...p, tmbNonsynonymous: e.target.value }))} />
                <input type="number" placeholder="yearOfDiagnosis" value={outcomeForm.yearOfDiagnosis} onChange={(e) => setOutcomeForm((p) => ({ ...p, yearOfDiagnosis: e.target.value }))} />
                <textarea placeholder="actualOutcomeNotes" value={outcomeForm.actualOutcomeNotes} onChange={(e) => setOutcomeForm((p) => ({ ...p, actualOutcomeNotes: e.target.value }))} />
                <button type="submit" disabled={busy.saveOutcome}>{busy.saveOutcome ? 'Saving...' : 'Save Outcome Data'}</button>
              </form>

              <div className="inline-row">
                <button type="button" onClick={handleSyncRetrain} disabled={busy.sync}>
                  {busy.sync ? 'Sending...' : 'Send Outcome-Labeled Data to Retrain'}
                </button>
              </div>
            </article>
          </section>
        )}

        {(error || info) && (
          <section className="flash-zone">
            {error && <div className="flash bad">{error}</div>}
            {info && <div className="flash ok">{info}</div>}
          </section>
        )}
      </main>
    </div>
  )
}

export default App
