import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { AuthProvider, useAuth } from './useAuth'

function Probe() {
  const { username, loading, logout } = useAuth()
  if (loading) {
    return <div>loading</div>
  }
  return (
    <div>
      <div data-testid="username">{username ?? 'anonymous'}</div>
      <button type="button" onClick={() => logout()}>
        logout
      </button>
    </div>
  )
}

describe('useAuth', () => {
  it('reflects an existing session on mount', async () => {
    server.use(http.get('/api/auth/me', () => HttpResponse.json({ username: 'admin' })))
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )

    expect(await screen.findByTestId('username')).toHaveTextContent('admin')
  })

  it('reflects no session when /me returns 401', async () => {
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })))
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )

    expect(await screen.findByTestId('username')).toHaveTextContent('anonymous')
  })

  it('clears the username after logout', async () => {
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json({ username: 'admin' })),
      http.post('/api/auth/logout', () => new HttpResponse(null, { status: 200 }))
    )
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    )

    await screen.findByText('admin')
    await user.click(screen.getByRole('button', { name: /logout/i }))

    await waitFor(() => expect(screen.getByTestId('username')).toHaveTextContent('anonymous'))
  })
})
