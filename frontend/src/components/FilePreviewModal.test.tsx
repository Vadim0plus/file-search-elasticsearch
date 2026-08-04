import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
import { FilePreviewModal } from './FilePreviewModal'

const baseDetail = {
  id: 'abc123',
  path: '/data/report.txt',
  fileName: 'report.txt',
  extension: 'txt',
  contentType: 'text/plain',
  sizeBytes: 100,
  modifiedAt: '2024-01-01T00:00:00Z',
  author: null,
  title: null,
  documentCreatedAt: null,
  content: 'Hello world',
  truncated: false,
}

describe('FilePreviewModal', () => {
  it('renders extracted text content for non-renderable file types', async () => {
    server.use(http.get('/api/files/abc123', () => HttpResponse.json(baseDetail)))
    render(<FilePreviewModal fileId="abc123" onClose={() => {}} />)

    expect(await screen.findByText('Hello world')).toBeInTheDocument()
  })

  it('renders an image tag for image content types', async () => {
    server.use(
      http.get('/api/files/img1', () =>
        HttpResponse.json({ ...baseDetail, id: 'img1', fileName: 'photo.png', contentType: 'image/png' })
      )
    )
    render(<FilePreviewModal fileId="img1" onClose={() => {}} />)

    const img = await screen.findByRole('img')
    expect(img).toHaveAttribute('src', '/api/files/img1/preview')
  })

  it('renders an iframe for PDF content types', async () => {
    server.use(
      http.get('/api/files/pdf1', () =>
        HttpResponse.json({ ...baseDetail, id: 'pdf1', fileName: 'doc.pdf', contentType: 'application/pdf' })
      )
    )
    const { container } = render(<FilePreviewModal fileId="pdf1" onClose={() => {}} />)

    await waitFor(() => expect(container.querySelector('iframe')).not.toBeNull())
    expect(container.querySelector('iframe')).toHaveAttribute('src', '/api/files/pdf1/preview')
  })

  it('shows author/title metadata when present', async () => {
    server.use(
      http.get('/api/files/meta1', () =>
        HttpResponse.json({ ...baseDetail, id: 'meta1', author: 'Иван Иванов', title: 'Отчёт' })
      )
    )
    render(<FilePreviewModal fileId="meta1" onClose={() => {}} />)

    expect(await screen.findByText('Иван Иванов')).toBeInTheDocument()
    expect(screen.getByText('Отчёт')).toBeInTheDocument()
  })

  it('shows a truncation note when content was truncated', async () => {
    server.use(http.get('/api/files/trunc1', () => HttpResponse.json({ ...baseDetail, id: 'trunc1', truncated: true })))
    render(<FilePreviewModal fileId="trunc1" onClose={() => {}} />)

    expect(await screen.findByText(/показана только часть/i)).toBeInTheDocument()
  })

  it('renders file content that looks like HTML as inert text, never as markup', async () => {
    server.use(
      http.get('/api/files/xss1', () => HttpResponse.json({ ...baseDetail, id: 'xss1', content: '<script>alert(1)</script>' }))
    )
    const { container } = render(<FilePreviewModal fileId="xss1" onClose={() => {}} />)

    await screen.findByText(/<script>alert\(1\)<\/script>/)
    expect(container.querySelector('script')).toBeNull()
  })

  it('calls onClose when the overlay is clicked', async () => {
    server.use(http.get('/api/files/abc123', () => HttpResponse.json(baseDetail)))
    const onClose = vi.fn()
    const { container } = render(<FilePreviewModal fileId="abc123" onClose={onClose} />)
    await screen.findByText('Hello world')

    const overlay = container.querySelector('.modal-overlay')
    expect(overlay).not.toBeNull()
    fireEvent.click(overlay as Element)

    expect(onClose).toHaveBeenCalled()
  })
})
