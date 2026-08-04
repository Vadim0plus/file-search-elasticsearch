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

// With an empty query the backend returns a browsable listing (most recently modified files
// first) instead of nothing, so there's no "start typing" gate here - only loading/error/empty
// states, plus wording that adapts to whether the user is actively searching or just browsing.
export function SearchResults({ results, total, loading, error, hasQuery, onPreview }: SearchResultsProps) {
  if (error) {
    return (
      <div className="search-status search-status-error" role="alert">
        Ошибка: {error}
      </div>
    )
  }
  if (loading && results.length === 0) {
    return <div className="search-status">{hasQuery ? 'Идёт поиск...' : 'Загрузка...'}</div>
  }
  if (results.length === 0) {
    return <div className="search-status">{hasQuery ? 'Ничего не найдено.' : 'В индексе пока нет файлов.'}</div>
  }
  return (
    <>
      <p className="result-count">{hasQuery ? `Найдено файлов: ${total}` : `Всего файлов: ${total}`}</p>
      <ul className="search-results">
        {results.map((hit) => (
          <ResultItem key={hit.id} hit={hit} onPreview={onPreview} />
        ))}
      </ul>
    </>
  )
}
