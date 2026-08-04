import { type FormEvent, useState } from 'react'
import { addRoot, reindexRoot, removeRoot } from '../api/client'
import type { IndexRootStatus } from '../api/types'
import { useIndexStatus } from '../hooks/useIndexStatus'

const STATUS_LABELS: Record<IndexRootStatus, string> = {
  IDLE: 'Ожидание',
  SCANNING: 'Сканирование',
  WATCHING: 'Отслеживание',
  ERROR: 'Ошибка',
}

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
      setFormError(err instanceof Error ? err.message : 'Не удалось добавить директорию')
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
          placeholder="/data/my-folder (путь внутри контейнера backend)"
          value={newPath}
          onChange={(event) => setNewPath(event.target.value)}
          aria-label="Путь до директории"
        />
        <button type="submit" disabled={submitting}>
          Добавить директорию
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
            <th>Путь</th>
            <th>Статус</th>
            <th>Прогресс</th>
            <th>Документов</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {roots.map((root) => (
            <tr key={root.id}>
              <td>{root.path}</td>
              <td>{STATUS_LABELS[root.status]}</td>
              <td>{root.status === 'SCANNING' ? `${root.processedFiles} / ${root.totalFiles}` : '—'}</td>
              <td>{root.docCount}</td>
              <td className="roots-actions">
                <button type="button" onClick={() => handleReindex(root.id)}>
                  Переиндексировать
                </button>
                <button type="button" onClick={() => handleRemove(root.id)}>
                  Удалить
                </button>
              </td>
            </tr>
          ))}
          {roots.length === 0 && (
            <tr>
              <td colSpan={5}>Пока нет отслеживаемых директорий.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
