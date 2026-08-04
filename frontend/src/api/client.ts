import type { ApiError, IndexRoot, SearchFilters, SearchResponse } from './types'

const BASE_URL = '/api'

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `Запрос завершился с ошибкой (статус ${response.status})`
    try {
      const body = (await response.json()) as ApiError
      if (body.message) {
        message = body.message
      }
    } catch {
      // response body wasn't JSON - keep the generic status message
    }
    throw new Error(message)
  }
  return response.json() as Promise<T>
}

export interface SearchParams {
  query: string
  filters: Partial<SearchFilters>
  page?: number
  size?: number
  signal?: AbortSignal
}

export async function searchFiles({ query, filters, page = 0, size = 20, signal }: SearchParams): Promise<SearchResponse> {
  const params = new URLSearchParams()
  if (query) {
    params.set('q', query)
  }
  filters.extensions?.forEach((ext) => params.append('extension', ext))
  if (filters.path) {
    params.set('path', filters.path)
  }
  if (filters.from) {
    params.set('from', filters.from)
  }
  if (filters.to) {
    params.set('to', filters.to)
  }
  params.set('page', String(page))
  params.set('size', String(size))

  const response = await fetch(`${BASE_URL}/search?${params.toString()}`, { signal })
  return parseJsonOrThrow<SearchResponse>(response)
}

export async function listRoots(): Promise<IndexRoot[]> {
  const response = await fetch(`${BASE_URL}/roots`)
  return parseJsonOrThrow<IndexRoot[]>(response)
}

export async function addRoot(path: string): Promise<IndexRoot> {
  const response = await fetch(`${BASE_URL}/roots`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
  return parseJsonOrThrow<IndexRoot>(response)
}

export async function reindexRoot(id: string): Promise<IndexRoot> {
  const response = await fetch(`${BASE_URL}/roots/${id}/reindex`, { method: 'POST' })
  return parseJsonOrThrow<IndexRoot>(response)
}

export async function removeRoot(id: string): Promise<void> {
  const response = await fetch(`${BASE_URL}/roots/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw new Error(`Не удалось удалить директорию (статус ${response.status})`)
  }
}
