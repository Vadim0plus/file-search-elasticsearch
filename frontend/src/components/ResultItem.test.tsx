import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SearchHit } from '../api/types'
import { ResultItem } from './ResultItem'

const baseHit: SearchHit = {
  id: 'abc123',
  path: '/data/report.txt',
  fileName: 'report.txt',
  extension: 'txt',
  sizeBytes: 2048,
  modifiedAt: '2024-01-01T00:00:00Z',
  highlights: [
    [
      { text: 'before ', matched: false },
      { text: 'match', matched: true },
      { text: ' after', matched: false },
    ],
  ],
  downloadUrl: '/api/files/abc123/download',
}

describe('ResultItem', () => {
  it('wraps matched fragments in <mark> and leaves the rest as plain text', () => {
    render(<ResultItem hit={baseHit} />)

    const mark = screen.getByText('match')
    expect(mark.tagName).toBe('MARK')
    expect(screen.getByText('before', { exact: false })).toBeInTheDocument()
  })

  it('links the download control at the hit-provided URL with the file name', () => {
    render(<ResultItem hit={baseHit} />)

    const link = screen.getByRole('link', { name: /download/i })
    expect(link).toHaveAttribute('href', '/api/files/abc123/download')
    expect(link).toHaveAttribute('download', 'report.txt')
  })

  it('renders file content that looks like HTML as inert text, never as markup', () => {
    const maliciousHit: SearchHit = {
      ...baseHit,
      highlights: [
        [
          { text: '<script>alert(1)</script> ', matched: false },
          { text: 'danger', matched: true },
        ],
      ],
    }

    const { container } = render(<ResultItem hit={maliciousHit} />)

    expect(container.querySelector('script')).toBeNull()
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument()
  })
})
