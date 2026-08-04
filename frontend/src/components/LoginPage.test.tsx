import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../hooks/useAuth'
import { server } from '../test/server'
import { LoginPage } from './LoginPage'

function renderLoginPage() {
  return render(
    <AuthProvider>
      <LoginPage />
    </AuthProvider>
  )
}

describe('LoginPage', () => {
  it('submits credentials and clears any error on success', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
      http.post('/api/auth/login', () => new HttpResponse(null, { status: 200 }))
    )
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText('Логин'), 'admin')
    await user.type(screen.getByLabelText('Пароль'), 'admin')
    await user.click(screen.getByRole('button', { name: /войти/i }))

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('shows an error message on invalid credentials', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
      http.post('/api/auth/login', () => new HttpResponse(null, { status: 401 }))
    )
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText('Логин'), 'admin')
    await user.type(screen.getByLabelText('Пароль'), 'wrong')
    await user.click(screen.getByRole('button', { name: /войти/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/неверный логин или пароль/i)
  })
})
