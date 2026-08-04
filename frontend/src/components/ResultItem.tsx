import type { SearchHit } from '../api/types'

interface ResultItemProps {
  hit: SearchHit
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
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
export function ResultItem({ hit }: ResultItemProps) {
  return (
    <li className="result-item">
      <div className="result-header">
        <span className="result-filename">{hit.fileName}</span>
        <span className="result-meta">
          {formatBytes(hit.sizeBytes)} · {new Date(hit.modifiedAt).toLocaleString()}
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
      <a className="result-download" href={hit.downloadUrl} download={hit.fileName}>
        Download
      </a>
    </li>
  )
}
