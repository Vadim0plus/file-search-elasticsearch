import { useCallback, useEffect, useRef, useState } from 'react'
import { listRoots } from '../api/client'
import type { IndexRoot } from '../api/types'

const ACTIVE_POLL_MS = 2000
const IDLE_POLL_MS = 8000

export function useIndexStatus() {
  const [roots, setRoots] = useState<IndexRoot[]>([])
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const refresh = useCallback(async () => {
    try {
      const result = await listRoots()
      setRoots(result)
      setError(null)
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось загрузить список директорий')
      return null
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    const tick = async () => {
      const result = await refresh()
      if (cancelled) {
        return
      }
      const isScanning = result?.some((root) => root.status === 'SCANNING') ?? false
      timerRef.current = setTimeout(tick, isScanning ? ACTIVE_POLL_MS : IDLE_POLL_MS)
    }
    tick()

    return () => {
      cancelled = true
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [refresh])

  return { roots, error, refresh }
}
