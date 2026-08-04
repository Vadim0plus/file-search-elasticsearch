import { type FormEvent, useState } from 'react'
import { addRoot, reindexRoot, removeRoot } from '../api/client'
import { useIndexStatus } from '../hooks/useIndexStatus'

export function IndexManager() {
  const { roots, error, refresh } = useIndexStatus()
  const [newPath, setNewPath] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    const path = newPath.trim()
    if (!path) {
      return
    }
    setSubmitting(true)
    setFormError(null)
    try {
      await addRoot(path)
      setNewPath('')
      await refresh()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to add root')
    } finally {
      setSubmitting(false)
    }
  }

  const handleReindex = async (id: string) => {
    await reindexRoot(id)
    await refresh()
  }

  const handleRemove = async (id: string) => {
    await removeRoot(id)
    await refresh()
  }

  return (
    <div className="index-manager">
      <form className="add-root-form" onSubmit={handleAdd}>
        <input
          type="text"
          placeholder="/data/my-folder (path inside the backend container)"
          value={newPath}
          onChange={(event) => setNewPath(event.target.value)}
          aria-label="Root path"
        />
        <button type="submit" disabled={submitting}>
          Add root
        </button>
      </form>
      {formError && (
        <div className="form-error" role="alert">
          {formError}
        </div>
      )}
      {error && (
        <div className="form-error" role="alert">
          {error}
        </div>
      )}

      <table className="roots-table">
        <thead>
          <tr>
            <th>Path</th>
            <th>Status</th>
            <th>Progress</th>
            <th>Docs</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {roots.map((root) => (
            <tr key={root.id}>
              <td>{root.path}</td>
              <td>{root.status}</td>
              <td>{root.status === 'SCANNING' ? `${root.processedFiles} / ${root.totalFiles}` : '—'}</td>
              <td>{root.docCount}</td>
              <td className="roots-actions">
                <button type="button" onClick={() => handleReindex(root.id)}>
                  Reindex
                </button>
                <button type="button" onClick={() => handleRemove(root.id)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
          {roots.length === 0 && (
            <tr>
              <td colSpan={5}>No tracked directories yet.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
