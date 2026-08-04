import type { SearchHit } from '../api/types'
import { ResultItem } from './ResultItem'

interface SearchResultsProps {
  results: SearchHit[]
  loading: boolean
  error: string | null
  hasQuery: boolean
}

export function SearchResults({ results, loading, error, hasQuery }: SearchResultsProps) {
  if (error) {
    return (
      <div className="search-status search-status-error" role="alert">
        Error: {error}
      </div>
    )
  }
  if (loading && results.length === 0) {
    return <div className="search-status">Searching...</div>
  }
  if (!hasQuery) {
    return <div className="search-status">Start typing to search indexed files.</div>
  }
  if (results.length === 0) {
    return <div className="search-status">No results found.</div>
  }
  return (
    <ul className="search-results">
      {results.map((hit) => (
        <ResultItem key={hit.id} hit={hit} />
      ))}
    </ul>
  )
}
