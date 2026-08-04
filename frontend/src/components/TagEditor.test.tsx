import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { TagEditor } from './TagEditor'

describe('TagEditor', () => {
  it('renders existing tags and badges the ones AI suggested', () => {
    render(<TagEditor fileId="abc123" tags={['договор', 'важное']} aiTags={['договор']} />)

    expect(screen.getByText('договор')).toBeInTheDocument()
    expect(screen.getByText('важное')).toBeInTheDocument()
    expect(screen.getByText('AI')).toBeInTheDocument()
  })

  it('adds a manually-typed tag and shows it without an AI badge', async () => {
    server.use(
      http.post('/api/files/abc123/tags', async ({ request }) => {
        const body = (await request.json()) as { tag: string }
        return HttpResponse.json({ tags: ['важное', body.tag], aiTags: [] })
      })
    )
    const user = userEvent.setup()
    render(<TagEditor fileId="abc123" tags={['важное']} aiTags={[]} />)

    await user.type(screen.getByPlaceholderText('Добавить метку вручную…'), 'счёт')
    await user.click(screen.getByRole('button', { name: 'Добавить' }))

    expect(await screen.findByText('счёт')).toBeInTheDocument()
  })

  it('removes a tag when its remove button is clicked', async () => {
    server.use(http.delete('/api/files/abc123/tags/:tag', () => HttpResponse.json({ tags: [], aiTags: [] })))
    const user = userEvent.setup()
    render(<TagEditor fileId="abc123" tags={['важное']} aiTags={[]} />)

    await user.click(screen.getByRole('button', { name: 'Удалить метку важное' }))

    expect(screen.queryByText('важное')).not.toBeInTheDocument()
  })

  it('generates tags via AI and merges them into the tag list', async () => {
    server.use(
      http.post('/api/files/abc123/tags/generate', () =>
        HttpResponse.json({ tags: ['договор', 'аренда'], aiTags: ['договор', 'аренда'] })
      )
    )
    const user = userEvent.setup()
    render(<TagEditor fileId="abc123" tags={[]} aiTags={[]} />)

    await user.click(screen.getByRole('button', { name: /сгенерировать/i }))

    expect(await screen.findByText('договор')).toBeInTheDocument()
    expect(screen.getByText('аренда')).toBeInTheDocument()
  })

  it('shows an error message when AI generation is unavailable', async () => {
    server.use(
      http.post('/api/files/abc123/tags/generate', () =>
        HttpResponse.json({ message: 'AI-теги не настроены' }, { status: 503 })
      )
    )
    const user = userEvent.setup()
    render(<TagEditor fileId="abc123" tags={[]} aiTags={[]} />)

    await user.click(screen.getByRole('button', { name: /сгенерировать/i }))

    expect(await screen.findByText('AI-теги не настроены')).toBeInTheDocument()
  })
})
