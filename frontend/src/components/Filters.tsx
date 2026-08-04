import { useEffect, useState, type FormEvent } from 'react'
import { listTags } from '../api/client'
import type { SearchFilters, TagCount } from '../api/types'

interface FiltersProps {
  filters: SearchFilters
  onChange: (filters: SearchFilters) => void
}

const COMMON_EXTENSIONS = ['txt', 'md', 'pdf', 'docx', 'xlsx', 'pptx', 'csv', 'json', 'log']
const MAX_TAG_CHIPS = 12

export function Filters({ filters, onChange }: FiltersProps) {
  const [availableTags, setAvailableTags] = useState<TagCount[]>([])
  const [tagInput, setTagInput] = useState('')

  useEffect(() => {
    listTags()
      .then(setAvailableTags)
      .catch(() => setAvailableTags([]))
  }, [])

  const toggleExtension = (ext: string) => {
    const next = filters.extensions.includes(ext)
      ? filters.extensions.filter((e) => e !== ext)
      : [...filters.extensions, ext]
    onChange({ ...filters, extensions: next })
  }

  const toggleTag = (tag: string) => {
    const next = filters.tags.includes(tag) ? filters.tags.filter((t) => t !== tag) : [...filters.tags, tag]
    onChange({ ...filters, tags: next })
  }

  const handleAddTag = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const value = tagInput.trim().toLowerCase()
    if (value && !filters.tags.includes(value)) {
      onChange({ ...filters, tags: [...filters.tags, value] })
    }
    setTagInput('')
  }

  const chipTags = [...new Set([...filters.tags, ...availableTags.map((t) => t.tag)])].slice(0, MAX_TAG_CHIPS)

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
      {chipTags.length > 0 && (
        <div className="filters-tags">
          {chipTags.map((tag) => (
            <label key={tag} className="filter-chip">
              <input type="checkbox" checked={filters.tags.includes(tag)} onChange={() => toggleTag(tag)} />
              #{tag}
            </label>
          ))}
          <form className="filters-tag-add" onSubmit={handleAddTag}>
            <input
              type="text"
              list="filter-tag-options"
              placeholder="+ метка"
              value={tagInput}
              onChange={(event) => setTagInput(event.target.value)}
            />
            <datalist id="filter-tag-options">
              {availableTags.map((t) => (
                <option key={t.tag} value={t.tag} />
              ))}
            </datalist>
          </form>
        </div>
      )}
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
