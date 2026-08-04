import { useEffect, useRef, useState } from 'react'
import { searchFiles } from '../api/client'
import type { SearchFilters, SearchResponse } from '../api/types'

export interface UseLiveSearchResult {
  data: SearchResponse | null
  loading: boolean
  error: string | null
}

/**
 * Fires a search request whenever the (already debounced) query, filters or page change.
 * A monotonic request id guards against an older, slower request resolving after a newer
 * one and clobbering the latest results - the AbortController alone would usually prevent
 * this, but only if the underlying fetch actually honors cancellation.
 */
export function useLiveSearch(
  query: string,
  filters: Partial<SearchFilters>,
  page: number,
  size: number
): UseLiveSearchResult {
  const [data, setData] = useState<SearchResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const latestRequestId = useRef(0)
  const filtersKey = JSON.stringify(filters)

  useEffect(() => {
    const requestId = ++latestRequestId.current
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    searchFiles({ query, filters: JSON.parse(filtersKey), page, size, signal: controller.signal })
      .then((response) => {
        if (latestRequestId.current === requestId) {
          setData(response)
          setLoading(false)
        }
      })
      .catch((err: unknown) => {
        if (latestRequestId.current !== requestId) {
          return
        }
        if (err instanceof DOMException && err.name === 'AbortError') {
          return
        }
        setError(err instanceof Error ? err.message : 'Ошибка поиска')
        setLoading(false)
      })

    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, filtersKey, page, size])

  return { data, loading, error }
}
