import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SearchHit } from '../api/types'
import { SearchResults } from './SearchResults'

const hit: SearchHit = {
  id: 'abc123',
  path: '/data/report.txt',
  fileName: 'report.txt',
  extension: 'txt',
  sizeBytes: 1024,
  modifiedAt: '2024-01-01T00:00:00Z',
  highlights: [],
  downloadUrl: '/api/files/abc123/download',
}

describe('SearchResults', () => {
  it('shows the total result count above the list', () => {
    render(<SearchResults results={[hit]} total={7} loading={false} error={null} hasQuery onPreview={vi.fn()} />)

    expect(screen.getByText('Найдено файлов: 7')).toBeInTheDocument()
  })

  it('does not show the counter while there is no query yet', () => {
    render(<SearchResults results={[]} total={0} loading={false} error={null} hasQuery={false} onPreview={vi.fn()} />)

    expect(screen.queryByText(/Найдено файлов/)).not.toBeInTheDocument()
  })

  it('shows an error message instead of the counter when the search failed', () => {
    render(<SearchResults results={[]} total={0} loading={false} error="boom" hasQuery onPreview={vi.fn()} />)

    expect(screen.getByRole('alert')).toHaveTextContent('boom')
    expect(screen.queryByText(/Найдено файлов/)).not.toBeInTheDocument()
  })
})
