import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'

function jsonResponse(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => payload,
  }
}

describe('App workflow', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  test('loads patients and prediction records on startup', async () => {
    const fetchMock = vi.fn(async (url) => {
      if (url === '/api/users') {
        return jsonResponse(200, [{ id: 1, name: 'Alice Smith' }])
      }
      if (url === '/api/predictions/search') {
        return jsonResponse(200, [{ predictionId: 9, patientName: 'Alice Smith', patientAge: 62 }])
      }
      return jsonResponse(404, { message: 'Not found' })
    })

    global.fetch = fetchMock

    render(<App />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/users'))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/predictions/search'))

    expect(await screen.findByRole('option', { name: '#1 - Alice Smith' })).toBeInTheDocument()
  })

  test('searches patients and runs single prediction', async () => {
    const fetchMock = vi.fn(async (url, options = {}) => {
      if (url === '/api/users') return jsonResponse(200, [{ id: 1, name: 'Alice Smith' }])
      if (url === '/api/predictions/search') return jsonResponse(200, [])
      if (url.startsWith('/api/users/search?name=')) return jsonResponse(200, [{ id: 2, name: 'Bob Stone' }])
      if (url === '/api/predictions/2' && options.method === 'POST') {
        return jsonResponse(200, {
          predictionId: 101,
          patientName: 'Bob Stone',
          survival6mo: 0.8,
          survival12mo: 0.6,
          survival24mo: 0.4,
          riskGroup: 'High Risk',
          riskScore: 1.24,
          plainLanguageSummary: 'Higher risk than baseline.',
        })
      }
      return jsonResponse(404, { message: 'Not found' })
    })

    global.fetch = fetchMock
    const user = userEvent.setup()

    render(<App />)

    const searchInput = await screen.findByPlaceholderText('Search by name')
    await user.type(searchInput, 'stone')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    await screen.findByRole('option', { name: '#2 - Bob Stone' })
    await user.selectOptions(screen.getByRole('combobox'), '2')

    await user.click(screen.getByRole('button', { name: 'Predict Selected Patient' }))

    expect(await screen.findByText('Prediction record created: #101')).toBeInTheDocument()
  })

  test('updates outcome from outcomes tab', async () => {
    const fetchMock = vi.fn(async (url, options = {}) => {
      if (url === '/api/users') return jsonResponse(200, [])
      if (url === '/api/predictions/search') {
        return jsonResponse(200, [
          {
            predictionId: 301,
            patientName: 'Nina Cole',
            patientAge: 55,
            expectedOutcome: 'Risk Group: High Risk',
          },
        ])
      }
      if (url === '/api/predictions/301/outcome' && options.method === 'PATCH') {
        return jsonResponse(200, { predictionId: 301, actualOutcome: 'Alive at 12 months' })
      }
      return jsonResponse(404, { message: 'Not found' })
    })

    global.fetch = fetchMock
    const user = userEvent.setup()

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Outcomes' }))
    await user.click(await screen.findByRole('button', { name: 'Select for Outcome Update' }))

    await user.type(screen.getByPlaceholderText('actualOutcome'), 'Alive at 12 months')
    await user.type(screen.getByPlaceholderText('actualOutcomeNotes'), 'Clinical follow-up stable.')

    await user.click(screen.getByRole('button', { name: 'Save Outcome Data' }))
    expect(await screen.findByText('Actual outcome updated.')).toBeInTheDocument()
  })
})
