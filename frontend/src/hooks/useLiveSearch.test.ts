import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as client from '../api/client'
import type { SearchResponse } from '../api/types'
import { useLiveSearch } from './useLiveSearch'

vi.mock('../api/client', () => ({
  searchFiles: vi.fn(),
}))

const searchFilesMock = vi.mocked(client.searchFiles)

afterEach(() => {
  vi.clearAllMocks()
})

function makeResponse(total: number, label: string): SearchResponse {
  return {
    total,
    page: 0,
    size: 20,
    results: [
      {
        id: label,
        path: `/data/${label}`,
        fileName: label,
        extension: 'txt',
        sizeBytes: 10,
        modifiedAt: '2024-01-01T00:00:00Z',
        highlights: [],
        downloadUrl: `/api/files/${label}/download`,
      },
    ],
  }
}

describe('useLiveSearch', () => {
  it('fetches and exposes results for the given query', async () => {
    searchFilesMock.mockResolvedValue(makeResponse(1, 'hello'))

    const { result } = renderHook(() => useLiveSearch('hello', {}, 0, 20))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.data?.total).toBe(1)
    expect(result.current.error).toBeNull()
  })

  it('does not let a slow, stale request clobber a newer one that already resolved', async () => {
    let resolveFirst: (value: SearchResponse) => void = () => {}
    const firstPromise = new Promise<SearchResponse>((resolve) => {
      resolveFirst = resolve
    })
    searchFilesMock.mockImplementationOnce(() => firstPromise)
    searchFilesMock.mockImplementationOnce(() => Promise.resolve(makeResponse(2, 'second')))

    const { result, rerender } = renderHook(({ query }) => useLiveSearch(query, {}, 0, 20), {
      initialProps: { query: 'first' },
    })

    rerender({ query: 'second' })
    await waitFor(() => expect(result.current.data?.total).toBe(2))

    // the slow first request finally resolves after the second one already won the race
    resolveFirst(makeResponse(1, 'first'))
    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(result.current.data?.total).toBe(2)
  })

  it('surfaces a request error', async () => {
    searchFilesMock.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useLiveSearch('x', {}, 0, 20))

    await waitFor(() => expect(result.current.error).toBe('boom'))
  })
})
