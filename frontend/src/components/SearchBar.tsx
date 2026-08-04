interface SearchBarProps {
  value: string
  onChange: (value: string) => void
}

export function SearchBar({ value, onChange }: SearchBarProps) {
  return (
    <input
      type="search"
      className="search-bar"
      placeholder="Поиск файлов по имени или содержимому..."
      value={value}
      onChange={(event) => onChange(event.target.value)}
      aria-label="Поиск файлов"
    />
  )
}
