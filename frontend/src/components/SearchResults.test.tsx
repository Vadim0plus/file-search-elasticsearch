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
  tags: [],
}

describe('SearchResults', () => {
  it('shows the total result count above the list while actively searching', () => {
    render(<SearchResults results={[hit]} total={7} loading={false} error={null} hasQuery onPreview={vi.fn()} />)

    expect(screen.getByText('Найдено файлов: 7')).toBeInTheDocument()
  })

  it('lists files by default with browse wording when there is no query yet', () => {
    render(<SearchResults results={[hit]} total={1} loading={false} error={null} hasQuery={false} onPreview={vi.fn()} />)

    expect(screen.getByText('Всего файлов: 1')).toBeInTheDocument()
    expect(screen.getByText('report.txt')).toBeInTheDocument()
  })

  it('shows an empty-index message instead of the counter when browsing with nothing indexed', () => {
    render(<SearchResults results={[]} total={0} loading={false} error={null} hasQuery={false} onPreview={vi.fn()} />)

    expect(screen.getByText('В индексе пока нет файлов.')).toBeInTheDocument()
  })

  it('shows an error message instead of the counter when the search failed', () => {
    render(<SearchResults results={[]} total={0} loading={false} error="boom" hasQuery onPreview={vi.fn()} />)

    expect(screen.getByRole('alert')).toHaveTextContent('boom')
    expect(screen.queryByText(/Найдено файлов/)).not.toBeInTheDocument()
  })
})
