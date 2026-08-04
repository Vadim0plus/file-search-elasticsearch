import { useState, type FormEvent } from 'react'
import { addTag, generateTags, removeTag } from '../api/client'

interface TagEditorProps {
  fileId: string
  tags: string[]
  aiTags: string[]
}

export function TagEditor({ fileId, tags: initialTags, aiTags: initialAiTags }: TagEditorProps) {
  const [tags, setTags] = useState(initialTags)
  const [aiTags, setAiTags] = useState(initialAiTags)
  const [input, setInput] = useState('')
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const value = input.trim()
    if (!value) {
      return
    }
    setError(null)
    try {
      const result = await addTag(fileId, value)
      setTags(result.tags)
      setAiTags(result.aiTags)
      setInput('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось добавить метку')
    }
  }

  const handleRemove = async (tag: string) => {
    setError(null)
    try {
      const result = await removeTag(fileId, tag)
      setTags(result.tags)
      setAiTags(result.aiTags)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось удалить метку')
    }
  }

  const handleGenerate = async () => {
    setGenerating(true)
    setError(null)
    try {
      const result = await generateTags(fileId)
      setTags(result.tags)
      setAiTags(result.aiTags)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось сгенерировать метки')
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div className="tag-editor">
      <div className="tag-editor-header">
        <span className="tag-editor-label">Метки</span>
        <button type="button" className="tag-generate-button" onClick={handleGenerate} disabled={generating}>
          {generating ? 'Генерация…' : '✨ Сгенерировать с помощью AI'}
        </button>
      </div>

      {tags.length > 0 && (
        <ul className="tag-list">
          {tags.map((tag) => (
            <li className="tag-chip tag-chip-removable" key={tag}>
              {aiTags.includes(tag) && (
                <span className="tag-ai-badge" title="Предложено AI">
                  AI
                </span>
              )}
              {tag}
              <button
                type="button"
                className="tag-remove-button"
                onClick={() => handleRemove(tag)}
                aria-label={`Удалить метку ${tag}`}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      <form className="tag-add-form" onSubmit={handleAdd}>
        <input
          type="text"
          placeholder="Добавить метку вручную…"
          value={input}
          onChange={(event) => setInput(event.target.value)}
        />
        <button type="submit">Добавить</button>
      </form>

      {error && (
        <div className="form-error" role="alert">
          {error}
        </div>
      )}
    </div>
  )
}
