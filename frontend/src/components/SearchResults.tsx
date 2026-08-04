import type { SearchHit } from '../api/types'
import { ResultItem } from './ResultItem'

interface SearchResultsProps {
  results: SearchHit[]
  total: number
  loading: boolean
  error: string | null
  hasQuery: boolean
  onPreview: (id: string) => void
}

export function SearchResults({ results, total, loading, error, hasQuery, onPreview }: SearchResultsProps) {
  if (error) {
    return (
      <div className="search-status search-status-error" role="alert">
        Ошибка: {error}
      </div>
    )
  }
  if (loading && results.length === 0) {
    return <div className="search-status">Идёт поиск...</div>
  }
  if (!hasQuery) {
    return <div className="search-status">Начните вводить текст для поиска по проиндексированным файлам.</div>
  }
  if (results.length === 0) {
    return <div className="search-status">Ничего не найдено.</div>
  }
  return (
    <>
      <p className="result-count">Найдено файлов: {total}</p>
      <ul className="search-results">
        {results.map((hit) => (
          <ResultItem key={hit.id} hit={hit} onPreview={onPreview} />
        ))}
      </ul>
    </>
  )
}
