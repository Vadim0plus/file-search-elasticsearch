import { useEffect, useState } from 'react'

export function useHealth(intervalMs = 10000): boolean {
  const [healthy, setHealthy] = useState(true)

  useEffect(() => {
    let cancelled = false

    const check = async () => {
      try {
        const response = await fetch('/actuator/health')
        if (!cancelled) {
          setHealthy(response.ok)
        }
      } catch {
        if (!cancelled) {
          setHealthy(false)
        }
      }
    }

    check()
    const timer = setInterval(check, intervalMs)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [intervalMs])

  return healthy
}
