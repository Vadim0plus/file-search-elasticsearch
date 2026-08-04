import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { addRoot, searchFiles } from './client'

describe('api client', () => {
  it('builds search query params from the query, filters and pagination', async () => {
    let capturedUrl: string | null = null
    server.use(
      http.get('/api/search', ({ request }) => {
        capturedUrl = request.url
        return HttpResponse.json({ total: 0, page: 1, size: 10, results: [] })
      })
    )

    const result = await searchFiles({
      query: 'hello',
      filters: { extensions: ['pdf', 'txt'], path: '/data' },
      page: 1,
      size: 10,
    })

    expect(result.total).toBe(0)
    const url = new URL(capturedUrl!)
    expect(url.searchParams.get('q')).toBe('hello')
    expect(url.searchParams.getAll('extension')).toEqual(['pdf', 'txt'])
    expect(url.searchParams.get('path')).toBe('/data')
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('size')).toBe('10')
  })

  it('surfaces the server-provided error message on a failed request', async () => {
    server.use(http.post('/api/roots', () => HttpResponse.json({ message: 'Путь уже отслеживается' }, { status: 400 })))

    await expect(addRoot('/data')).rejects.toThrow('Путь уже отслеживается')
  })

  it('falls back to a generic message when the error body is not JSON', async () => {
    server.use(http.post('/api/roots', () => new HttpResponse('oops', { status: 500 })))

    await expect(addRoot('/data')).rejects.toThrow('статус 500')
  })
})
