import type { SearchHit } from '../api/types'
import { getFileIcon } from '../utils/fileIcon'

interface ResultItemProps {
  hit: SearchHit
  onPreview: (id: string) => void
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} Б`
  }
  const units = ['КБ', 'МБ', 'ГБ']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`
}

// Fragments are rendered as plain React text nodes (never dangerouslySetInnerHTML), so even if
// a file's content contains raw HTML/script text, it is displayed inertly and never parsed as markup.
export function ResultItem({ hit, onPreview }: ResultItemProps) {
  return (
    <li className="result-item">
      <div className="result-header">
        <span className="result-filename">
          <span className="result-icon" aria-hidden="true">
            {getFileIcon(hit.extension)}
          </span>
          {hit.fileName}
        </span>
        <span className="result-meta">
          {formatBytes(hit.sizeBytes)} · {new Date(hit.modifiedAt).toLocaleString('ru-RU')}
        </span>
      </div>
      <div className="result-path">{hit.path}</div>
      {hit.highlights.map((fragments, snippetIndex) => (
        <p className="result-excerpt" key={snippetIndex}>
          {fragments.map((fragment, fragmentIndex) =>
            fragment.matched ? (
              <mark key={fragmentIndex}>{fragment.text}</mark>
            ) : (
              <span key={fragmentIndex}>{fragment.text}</span>
            )
          )}
        </p>
      ))}
      <div className="result-actions">
        <button type="button" className="result-preview" onClick={() => onPreview(hit.id)}>
          Просмотр
        </button>
        <a className="result-download" href={hit.downloadUrl} download={hit.fileName}>
          Скачать
        </a>
      </div>
    </li>
  )
}
