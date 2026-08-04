import type { SearchFilters } from '../api/types'

interface FiltersProps {
  filters: SearchFilters
  onChange: (filters: SearchFilters) => void
}

const COMMON_EXTENSIONS = ['txt', 'md', 'pdf', 'docx', 'xlsx', 'pptx', 'csv', 'json', 'log']

export function Filters({ filters, onChange }: FiltersProps) {
  const toggleExtension = (ext: string) => {
    const next = filters.extensions.includes(ext)
      ? filters.extensions.filter((e) => e !== ext)
      : [...filters.extensions, ext]
    onChange({ ...filters, extensions: next })
  }

  return (
    <div className="filters">
      <div className="filters-extensions">
        {COMMON_EXTENSIONS.map((ext) => (
          <label key={ext} className="filter-chip">
            <input type="checkbox" checked={filters.extensions.includes(ext)} onChange={() => toggleExtension(ext)} />
            .{ext}
          </label>
        ))}
      </div>
      <input
        type="text"
        className="filters-path"
        placeholder="Путь начинается с..."
        value={filters.path}
        onChange={(event) => onChange({ ...filters, path: event.target.value })}
      />
      <label className="filters-date">
        С
        <input type="date" value={filters.from} onChange={(event) => onChange({ ...filters, from: event.target.value })} />
      </label>
      <label className="filters-date">
        По
        <input type="date" value={filters.to} onChange={(event) => onChange({ ...filters, to: event.target.value })} />
      </label>
    </div>
  )
}
