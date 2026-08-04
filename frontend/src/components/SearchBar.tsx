interface SearchBarProps {
  value: string
  onChange: (value: string) => void
}

export function SearchBar({ value, onChange }: SearchBarProps) {
  return (
    <input
      type="search"
      className="search-bar"
      placeholder="Search files by name or content..."
      value={value}
      onChange={(event) => onChange(event.target.value)}
      aria-label="Search files"
    />
  )
}
