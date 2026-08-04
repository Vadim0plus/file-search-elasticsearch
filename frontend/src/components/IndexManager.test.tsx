import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { IndexManager } from './IndexManager'

const scanningRoot = {
  id: 'r1',
  path: '/data',
  status: 'SCANNING',
  totalFiles: 100,
  processedFiles: 42,
  docCount: 40,
  lastError: null,
  createdAt: '2024-01-01T00:00:00Z',
}

describe('IndexManager', () => {
  it('lists tracked roots with live scan progress', async () => {
    server.use(http.get('/api/roots', () => HttpResponse.json([scanningRoot])))

    render(<IndexManager />)

    expect(await screen.findByText('/data')).toBeInTheDocument()
    expect(screen.getByText('42 / 100')).toBeInTheDocument()
    expect(screen.getByText('Сканирование')).toBeInTheDocument()
  })

  it('adds a new root through the API and shows it once the list refreshes', async () => {
    let created = false
    server.use(
      http.get('/api/roots', () =>
        HttpResponse.json(
          created
            ? [{ ...scanningRoot, id: 'r2', path: '/new/path', status: 'IDLE', processedFiles: 0, totalFiles: 0 }]
            : []
        )
      ),
      http.post('/api/roots', async ({ request }) => {
        created = true
        const body = (await request.json()) as { path: string }
        return HttpResponse.json({
          id: 'r2',
          path: body.path,
          status: 'IDLE',
          totalFiles: 0,
          processedFiles: 0,
          docCount: 0,
          lastError: null,
          createdAt: '2024-01-01T00:00:00Z',
        })
      })
    )

    const user = userEvent.setup()
    render(<IndexManager />)

    await waitFor(() => expect(screen.getByText('Пока нет отслеживаемых директорий.')).toBeInTheDocument())

    await user.type(screen.getByLabelText('Путь до директории'), '/new/path')
    await user.click(screen.getByRole('button', { name: /добавить директорию/i }))

    expect(await screen.findByText('/new/path')).toBeInTheDocument()
  })

  it('shows the server error message when adding a root fails', async () => {
    server.use(
      http.get('/api/roots', () => HttpResponse.json([])),
      http.post('/api/roots', () => HttpResponse.json({ message: 'Путь не существует' }, { status: 400 }))
    )

    const user = userEvent.setup()
    render(<IndexManager />)

    await user.type(screen.getByLabelText('Путь до директории'), '/nope')
    await user.click(screen.getByRole('button', { name: /добавить директорию/i }))

    expect(await screen.findByText('Путь не существует')).toBeInTheDocument()
  })
})
