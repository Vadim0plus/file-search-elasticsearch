import type { ApiError, FileDetail, IndexRoot, SearchFilters, SearchResponse } from './types'

const BASE_URL = '/api'

function apiFetch(path: string, options: RequestInit = {}, signal?: AbortSignal): Promise<Response> {
  return fetch(`${BASE_URL}${path}`, { ...options, credentials: 'include', signal })
}

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

  const response = await apiFetch(`/search?${params.toString()}`, {}, signal)
  return parseJsonOrThrow<SearchResponse>(response)
}

export async function getFileDetail(id: string): Promise<FileDetail> {
  const response = await apiFetch(`/files/${id}`)
  return parseJsonOrThrow<FileDetail>(response)
}

export async function listRoots(): Promise<IndexRoot[]> {
  const response = await apiFetch('/roots')
  return parseJsonOrThrow<IndexRoot[]>(response)
}

export async function addRoot(path: string): Promise<IndexRoot> {
  const response = await apiFetch('/roots', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
  return parseJsonOrThrow<IndexRoot>(response)
}

export async function reindexRoot(id: string): Promise<IndexRoot> {
  const response = await apiFetch(`/roots/${id}/reindex`, { method: 'POST' })
  return parseJsonOrThrow<IndexRoot>(response)
}

export async function removeRoot(id: string): Promise<void> {
  const response = await apiFetch(`/roots/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw new Error(`Не удалось удалить директорию (статус ${response.status})`)
  }
}

export async function uploadFile(rootId: string, file: File): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await apiFetch(`/roots/${rootId}/upload`, { method: 'POST', body: formData })
  if (!response.ok) {
    let message = `Не удалось загрузить файл (статус ${response.status})`
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
}

export async function login(username: string, password: string): Promise<void> {
  const response = await apiFetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }).toString(),
  })
  if (!response.ok) {
    throw new Error('Неверный логин или пароль')
  }
}

export async function logout(): Promise<void> {
  await apiFetch('/auth/logout', { method: 'POST' })
}

export async function me(): Promise<{ username: string } | null> {
  const response = await apiFetch('/auth/me')
  if (!response.ok) {
    return null
  }
  return (await response.json()) as { username: string }
}
